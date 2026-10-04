# AIRDR-73: load remote delivery destinations asynchronously

Date: 2026-10-04. Status: proposed design; runtime implementation is pending.

Work item: [AIRDR-73](https://plane.mcconnaughey.us/homelab/projects/c88c9eb3-373d-4704-97d8-6c4a82ea1f10/issues/aed91955-d503-4e19-9c5b-a35ad2fcfc68). Related change: [PR #113](https://github.com/LukeMccon/Airdrop/pull/113). This addresses the [coordinate-loading review finding](https://github.com/LukeMccon/Airdrop/pull/113#discussion_r4177503740).

## Remote deliveries should work in existing terrain

Load unloaded destination chunks asynchronously. By default, permit terrain that has already been generated and reject destinations that would require new terrain. Server owners can explicitly enable generation.

For example, sending a package to an explored base should work when nobody is nearby. Sending to an unexplored destination should explain that terrain generation is disabled and leave the sender uncharged.

Async loading removes the current synchronous destination load, but still consumes server resources. Admission limits, a separate attempt throttle, a global load cap, and cleanup rules must apply before remote work starts.

## The current surface lookup runs before limits

`DropRequestCoordinator.begin()` calls `resolveTarget()`, which uses `World#getHighestBlockAt`, before checking package permissions and acquiring admission. Repeated coordinate commands can therefore load or generate terrain even when the eventual request fails a pending-request or cooldown check.

The existing admission lease reserves a complete landing key, including Y. That Y is unknown until the chunk is loaded and its surface inspected. Admission needs a preparation stage that reserves player and server capacity first, then binds the actual landing location.

## Other plugins support this approach

| Source | Relevant behavior |
| --- | --- |
| [EssentialsX AsyncTeleport](https://github.com/EssentialsX/Essentials/blob/2.x/Essentials/src/main/java/com/earth2me/essentials/AsyncTeleport.java) | Checks configured cooldowns before async loading and checks destination safety after loading; its loader permits generation. |
| [Phoenix616 RandomTeleport](https://github.com/Phoenix616/RandomTeleport) | Separates loaded-only and generated-only modes and exposes cooldowns and maximum search attempts. |
| [DarkEyeDragon RandomTeleport](https://github.com/DarkEyeDragon/RandomTeleport) | Supports async chunk loading, cooldowns, and a configurable cache of prepared destinations. |

[Paper's chunk API](https://jd.papermc.io/paper/1.21.11/org/bukkit/World.html#getChunkAtAsync(int,int,boolean,boolean)) supports generation and priority flags. Use ordinary priority so Paper controls scheduling. Never block the main thread with `get()` or `join()`.

Loaded-only delivery would be simpler but reject valid remote bases. Always allowing generation would support more destinations while allowing commands to expand the world. Async loading with generation disabled by default is the selected middle ground. Airdrop needs a single known destination, so it does not need random-location searches or a cache of prepared locations.

## Preparation must reserve capacity before touching terrain

Use the existing coordinator and admission controller, with a small internal chunk-loading adapter that can be controlled in tests. Keep mutable request state and Bukkit world access on the main thread.

1. Validate service readiness, package existence, sender authority, package access, world identity, finite coordinates, and the world border. Apply the existing named-recipient permission rule. Reject an unavailable economy configuration/provider for a non-exempt paid request before loading; do not withdraw money.
2. Capture the package, settings, destination, and cost exemption once. Async waiting must not retarget a request or change its price after a reload.
3. Acquire a preparation lease. Check the player's normal cooldown and pending request, then reserve the player's pending slot and the existing falling and landed capacity. System requests reserve server capacity too. The lease initially has no landing key.
4. If the chunk is unloaded or not entity ticking, check the remote-attempt throttle and acquire a global load slot. Reject immediately when busy; do not create a waiting queue. Prepare the destination's two-chunk ticking neighborhood sequentially through Paper async loads with the captured generation policy and `urgent=false`; keep one backend operation per preparation. Refresh the center last, then retain it.
5. On successful completion, verify the request is still active and the same world is loaded. Retain the chunk, inspect its surface, and bind the exact landing key atomically. Reject a conflicting landing reservation and release preparation resources.
6. Publish the resolved context and fire `AirdropRequestEvent`. Recheck applicable permissions and the world border after the wait/event. If cancelled or invalid, release the lease and chunk retention without charging.
7. Continue through the existing affordability, withdrawal, spawn, landing, and refund flow. Successful spawn starts the ordinary player cooldown. Keep the chunk retained until landing or delivery failure.

Apply this resolver to every request path that could otherwise inspect an unloaded chunk, including shared API requests. Already-loaded, entity-ticking targets use the immediate path and consume no remote-load slot or remote-attempt throttle. Named targets retain their captured destination and existing eligibility rules.

Preparation leases close idempotently. They can commit only after binding a landing key. Existing `acquirePlayer`/`acquireSystem` callers can retain their behavior through a prepare-and-bind wrapper; landed-crate recovery continues to use its existing reservation path.

## Remote loads need separate limits

Proposed global settings under `drop.remote-loading`:

| Setting | Default | Allowed values | Purpose |
| --- | --- | --- | --- |
| `generate-new-chunks` | `false` | boolean | Require explicit consent to terrain generation. |
| `max-concurrent-loads` | `2` | integers 1–32 | Bound outstanding Paper load operations across all worlds and callers. |
| `attempt-cooldown-seconds` | `5` | integers 1–3600 | Bound repeated remote attempts, including failures. |
| `load-timeout-seconds` | `10` | integers 1–60 | End a delivery request that waits too long for its destination. |

These are conservative starting values, not measured throughput guarantees. Validate them through the existing configuration loader; missing keys receive defaults. Reloaded policy applies to new requests. Lowering the load cap blocks new loads until outstanding work falls below the new cap; it does not interrupt existing loads.

The attempt throttle starts when a remote load is actually submitted, before its result is known. Key player attempts by UUID. Use one shared throttle key for non-player requests. Expire old entries so the map cannot grow indefinitely.

Cost exemption and normal cooldown bypass do not bypass the remote throttle or global cap. Existing falling and landed limits still bound retained delivery chunks after loading finishes. Generation, when enabled, requires the entire two-chunk ticking neighborhood to fit inside the world border; recheck that footprint between backend operations if the border moves. It remains subject to all admission and remote limits; asynchronous generation can still consume substantial CPU and disk space.

## A timeout must not free an unfinished load slot

Treat the request deadline and the underlying Paper operation separately. At the deadline, finish the request without payment, release its preparation lease, and cancel its deadline task. The underlying operation remains counted against the global cap until it actually completes.

Do not rely on cancelling a future to stop server chunk work. A late completion releases only its load slot; it cannot publish a context, add a ticket, start payment, spawn a crate, or complete the handle again. An exceptional completion follows the same cleanup rule. A load that never finishes occupies a slot rather than allowing unlimited replacement work.

Before accepting any completion, check the request phase, plugin availability, world identity, and captured deadline. Check the deadline again before beginning payment. Use a monotonic clock for elapsed time. Scheduler delays during server lag do not permit an expired request to resume. Cancel the preparation deadline task when preparation finishes successfully; subsequent payment operations retain their existing session deadlines.

## Chunk retention must end with delivery

Prepare the 5×5 ticking neighborhood asynchronously; Paper's `isChunkGenerated` can synchronously wait on disk and must not be used here. Add a plugin chunk ticket only after a successful load while the chunk is still loaded. Adding a ticket itself can load a chunk, so it must not become a substitute synchronous loading path. [Paper documents ticket ownership and removal](https://jd.papermc.io/paper/1.21.11/org/bukkit/World.html#addPluginChunkTicket(int,int,org.bukkit.plugin.Plugin)).

Track retention by world UUID and chunk coordinates with a reference count: Paper allows one ticket per plugin per chunk, while several Airdrop requests may share that chunk. Remove the ticket only when the last request releases it. Every immediate request to an already entity-ticking chunk also acquires a retention reference, even when Airdrop did not previously own a ticket. This keeps the captured destination available if its nearby players leave. Keep retention through payment and falling. Remote preparations remove their parachutes and release retention when the barrel commits. Already entity-ticking destinations keep retention through the normal three-second parachute fly-away, then clean remaining auxiliaries and release it; shutdown cancels that delayed cleanup and releases retention immediately. Delivery failure cleans up immediately. Landed crates continue through ordinary tracking and persistence without permanent chunk retention.

Remote falling deliveries also get a watchdog from successful spawn. Schedule it after `max(2400, 2 * ceil(dropHeight / fallingSpeed) + 600)` server ticks: at least two minutes at normal tick rate, or twice the configured nominal fall duration plus 30 seconds. This accommodates the supported slow-fall settings rather than imposing a fixed deadline that would reject them. If a crate has not landed, remove it and follow the existing failed-delivery/refund path before releasing retention. Cancel the watchdog on landing or earlier failure. Landing and watchdog callbacks must produce one terminal outcome.

On shutdown, stop accepting work, terminate waiting requests, cancel timers, and remove Airdrop's tickets. Late load callbacks cannot restart delivery. Normal world unload must not cause the resolver to reopen a world or silently retarget its coordinates.

## Payment and event guarantees remain explicit

Withdraw only after destination resolution, landing reservation, and request-event acceptance. Before-payment failures report `REJECTED` for a non-exempt priced request and `NOT_APPLICABLE` for an exempt or free request, as the current coordinator does. They make no deposit or refund call.

Use the existing `PaidDropSession` for confirmed charges and refunds. If delivery fails after a charge, preserve its single refund attempt and handling of uncertain provider results. Native protection checks and `AirdropLandingAttemptEvent` still happen at landing; a protection cancellation after payment uses that refund path. This design does not promise that a protection plugin's eventual landing decision can be predicted before charging.

The preparation reservation is a documented change to event ordering. `AirdropRequestEvent` now occurs after temporary preparation admission and chunk resolution, but before payment, spawn, and successful-drop cooldown. Cancellation releases all delivery reservations and retention; it retains an already-started remote-attempt throttle and cannot undo completed terrain generation. Update the API event documentation to state this boundary explicitly. API 1 shipped with Airdrop 4.1.0, so this change uses API 2.0.0 under the repository's [API policy](../../development/api-versioning.md) for breaking documented semantics. Do not classify the event-ordering change as merely an additive enum update. Keep events on the main thread and outcome publication exactly once.

Add distinct rejection reasons and localized messages for remote load capacity, attempt throttling, and load timeout. Use `TARGET_NOT_GENERATED` for disabled generation, `OUTSIDE_WORLD_BORDER` for a border rejection, and `INVALID_TARGET` for other invalid destinations. Update generated API compatibility artifacts and exhaustive enum consumers as required by the repository's API policy.

## Messages should explain progress and failure

Show a preparation message only when a remote load is needed: “Preparing the destination for your package…” It must not imply payment or successful spawn.

Suggested failure messages:

- “Remote deliveries are busy. Please try again shortly.”
- “Please wait {seconds} seconds before another remote delivery attempt.”
- “The destination took too long to load. You were not charged.”
- “That destination requires new terrain generation, which is disabled.”
- “That destination is outside the world border.”

Keep sender success feedback and recipient notifications at their existing lifecycle points. Named recipients receive the sender name/head after actual spawn and the location after landing. Coordinate sends have no named recipient. Recipient messages contain no request UUID.

## Verification must prove loading, cleanup, and economy behavior

Unit tests use controlled load futures and a monotonic test clock. Cover early permission, border, pending, cooldown, and capacity rejections without invoking the loader; binding conflicts; generation-disabled results; load exceptions; attempt throttling after failures; and cost/cooldown bypass respecting remote safeguards.

Exercise timeout followed by late success or failure, repeated timed-out requests, reload, shutdown, world removal, event cancellation/reentrancy, and shared chunk retention. Assert no duplicate outcome, no late spawn or payment, balanced leases/tickets, and load slots released only after underlying completion. Exercise the falling watchdog racing with landing, its deadline for the slowest supported falling speed, and one refund attempt after a confirmed charge.

LightKeeper scenarios on real Paper must demonstrate:

1. Delivery to a generated, saved, unloaded chunk outside player view and simulation distance, with no nearby player; the native falling crate lands and retention is released.
2. Rejection of an ungenerated destination with generation disabled, without generating that destination or charging; delivery succeeds when explicitly enabled.
3. Rejection outside the border and under pending/cooldown limits before terrain work; real LuckPerms bypass permissions still respect remote safeguards.
4. Paid remote delivery charges only the sender once; load failure does not charge; landing cancellation refunds through the existing path.
5. Shared-chunk requests retain the chunk until the final falling request finishes, and shutdown removes Airdrop tickets.

Controllable futures provide deterministic timeout and concurrency tests; LightKeeper proves actual Paper loading, entity ticking, and delivery behavior. A ticket that merely prevents unloading is insufficient if a remote falling entity does not tick. Resolve that behavior on the supported Paper version before shipping.

Run `./gradlew test build verifyApiCompatibility` and the relevant LightKeeper send, permissions, paid-economy, and crate-lifecycle lanes after implementation. Review the resulting change for admission ordering and for payment/cleanup races, including the unresolved PR finding.
