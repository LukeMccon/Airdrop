# AIRDR-4 Drop Admission and Lifecycle Limits Design

## Summary

AIRDR-4 adds one admission boundary for every airdrop. The boundary rate-limits player requests, reserves bounded falling and future-landed capacity before any economy withdrawal, and gives each accepted crate one idempotent lifecycle lease. A crate converts that lease when it lands and closes it on every failure, expiry, destruction, unload, or plugin-disable path.

The operator permission is deliberately narrow. `airdrop.cooldown.bypass` bypasses only the per-player request cooldown. Falling and landed capacity remain global, non-bypassable server-safety limits, including for operators and programmatic drops.

## Problem

The current request path validates the package and economy, charges the player, then constructs and spawns a crate. It does not limit request frequency, falling crates, parachute entities, landed crates, or the repeating effects tasks owned by landed crates.

Cleanup is distributed across landing, block break, inventory close, explosion, burn, chunk/world unload, failed landing, and plugin disable. `Crate.destroy()` is not currently terminal or idempotent, so duplicate cleanup signals have no shared ownership token that can prove capacity was released only once. Landed registration also replaces an existing same-block map entry, which can orphan the displaced crate's tracking and tasks.

## Goals

- Enforce a per-player request cooldown without resetting it on reconnect or runtime reload.
- Enforce a global limit on falling drops and their parachute entities/tasks.
- Enforce a global limit on landed crates and their continuous effects.
- Guarantee that every paid, accepted falling drop already owns future landed capacity.
- Reject capacity or location collisions before payment and before spawning any entity.
- Release all reservations exactly once on every failure and cleanup path.
- Expire landed crates and their effects after a configured lifetime.
- Preserve live accounting across runtime reload and clear it safely on plugin disable.
- Give operators a cooldown-only bypass without weakening global resource limits.
- Cover player commands and programmatic `DropController` entry points with the same hard-cap boundary.

## Non-Goals

- Persist active crates, cooldowns, or leases across a full server restart.
- Add a runtime command that overrides global capacity.
- Add per-world, per-package, or per-rank limits.
- Benchmark an exact maximum safe entity count for every Paper server configuration.
- Redesign economy withdrawal or refund provider implementations beyond ordering them around admission.
- Change package contents, falling physics, particle appearance, or crate looting behavior.

## Considered Approaches

### One permission bypasses every limit

This is simple to explain, but it makes `max-falling` and `max-landed` advisory rather than safety invariants. A command loop, automation error, or compromised operator account could recreate the unbounded entity/task condition AIRDR-4 is intended to close.

### Separate cooldown and capacity-override permissions

This preserves an emergency escape hatch, but there is no demonstrated need to exceed a server-safety limit at runtime. Planned events can raise configured caps and reload. A capacity override would add a high-risk privilege, logging requirements, and another acceptance matrix without solving stuck accounting; explicit cleanup is the correct recovery for a leaked lease.

### Cooldown-only bypass with hard capacity

This is the selected approach. Operators can run rapid manual tests or event drops, while every caller still participates in bounded entity, task, and landed-crate ownership. The permission is named `airdrop.cooldown.bypass` so its scope cannot be mistaken for a universal limit bypass.

## Configuration and Permission Defaults

The shipped `config.yml` will add:

```yaml
drop:
  limits:
    # Delay between successful player-initiated drops.
    request-cooldown-seconds: 30
    # Concurrent falling crates, including capacity reserved before spawn.
    max-falling: 3
    # Landed crates plus in-flight drops that have reserved a future landed slot.
    max-landed: 10
    # Maximum time a landed crate and its continuous effects may remain.
    landed-lifetime-seconds: 600
```

Invalid, missing, zero, or negative values fall back to these conservative defaults. Upper validation bounds prevent accidental integer/tick overflow and extreme values from silently disabling the protection. Existing configuration files receive the defaults through the repository's normal bundled-default behavior; they are not destructively rewritten.

The generated `plugin.yml` will declare `airdrop.cooldown.bypass` with an operator default and add it as a child of `airdrop.admin`. The application checks this exact permission for cooldown bypass. It does not infer capacity bypass from operator or administrator status.

## Migration Decision

No legacy alias or data migration is required. Repository-wide history checks found neither `airdrop.limits.bypass` nor `airdrop.cooldown.bypass` in any branch or tag, including the published non-v4 tags. The v3.2.0 and v3.4.0 prerelease manifests declare commands but no bypass permission, and their code exposes only the existing administrator/package permission family.

Version 4 therefore introduces `airdrop.cooldown.bypass` as a new permission. Supporting an unshipped `airdrop.limits.bypass` alias would create ambiguous broad authority and is intentionally out of scope. Existing operators continue to receive the new permission through its operator default; existing `airdrop.admin` grants receive it through the declared child relationship.

## Architecture

### Drop admission controller

A focused admission component owns mutable request and capacity state. It is independent of Bukkit entities so its concurrency, timing, reload, and exact-once behavior can be unit tested with a controllable monotonic clock.

Its state consists of:

- pending player UUIDs, preventing two concurrent requests from passing before cooldown commit;
- successful cooldown deadlines keyed by player UUID;
- the number of falling leases, including pre-spawn reservations;
- the number of landed claims, defined as active landed crates plus in-flight future-landed reservations;
- reserved landing block keys, covering both falling and landed crates; and
- an accepting/stopped flag for plugin shutdown.

All checks and claims occur in one synchronized operation. Bukkit normally delivers these commands on the server thread, but the atomic boundary also makes direct or concurrent API calls safe and satisfies the ticket's concurrent-request tests.

### Lifecycle lease

Successful admission returns one lease with this lifecycle:

```text
RESERVED -> FALLING -> LANDED -> CLOSED
                  \-> CLOSED
RESERVED --------------------> CLOSED
```

- `RESERVED`: the player request gate, falling capacity, future landed capacity, and target block are claimed before payment.
- `FALLING`: crate spawn and falling-entity registration completed.
- `LANDED`: falling capacity is released; the same future-landed claim becomes the active landed claim without another admission check.
- `CLOSED`: all remaining claims are released. Repeated close attempts do nothing.

The landed count does not increase during conversion because the slot was already reserved. This prevents several paid falling crates from competing for the final landed slot.

The lease is attached to the crate before spawning begins. Capacity release never depends on refund success. From acquisition through payload retrieval, construction, and ownership transfer, one failure boundary is responsible for closing the lease or destroying the crate; no pre-construction exception can leave an orphaned reservation.

### Landing-position reservation

Admission calculates a drop target containing both the falling spawn location and the intended landing block key. The target key is reserved atomically with capacity before payment. Another drop at that block is rejected while the first is falling or landed.

`CrateManager` will also use collision-safe registration rather than unconditional replacement. The landing listener uses `EntityChangeBlockEvent#getBlock()` as the authoritative target, and `Crate.land` compares that exact block key with the lease reservation before manager registration or world mutation. A mismatch fails closed and destroys the new crate instead of creating an unowned barrel or orphaning an older tracked crate.

### Crate terminal cleanup

`Crate.destroy()` becomes a terminal, idempotent operation. Its first call:

1. marks the crate destroyed;
2. removes the falling entity when present;
3. cancels parachute, flare, one-shot landing, glow, smoke, and landed-expiry tasks;
4. removes the landed barrel when this crate still owns it; and
5. closes the lifecycle lease.

Later calls have no effect. `CrateManager` remains responsible for removing lookup entries before or alongside destruction, but exact-once capacity is guaranteed by the lease rather than by assuming only one listener can signal cleanup.

## Player-Initiated Data Flow

1. Resolve the package and validate its package permission.
2. Verify economy availability and affordability.
3. Calculate and validate the sky, spawn location, and intended landing block.
4. Determine whether the player holds `airdrop.cooldown.bypass`.
5. Atomically acquire the pending request gate, falling slot, future-landed slot, and target-block reservation.
6. Materialize the package contents while the lease is still locally owned.
7. Withdraw the package price and record the confirmed transaction result before unrelated messaging.
8. Construct the crate with the lease and spawn/register it.
9. Dispatch `PackageDropEvent` only after registration is complete.
10. Atomically transition the lease from `RESERVED` to `FALLING` and commit the player's cooldown only after spawn, registration, and event dispatch return.
11. Release the temporary pending request gate and send charge confirmation as best-effort feedback.

A cooldown-bypassed player skips the cooldown/pending-player checks but still claims both capacity slots and the target block. The bypass does not waive package authorization, economy charging, or cleanup.

If payload retrieval or withdrawal fails, the controller closes the lease and starts no cooldown. If crate construction, spawning, registration, or an exception directly thrown by the local dispatch call fails, it destroys any partially created crate, closes the lease, attempts the existing refund after a confirmed withdrawal, and starts no cooldown. Paper logs and swallows ordinary third-party listener exceptions, so the plugin cannot promise rollback for failures the event manager does not expose. A failed refund or charge-confirmation message is reported independently and cannot retain capacity or roll back a successful crate.

## Programmatic Drop Data Flow

The public non-player `DropController.dropPackage` entry points skip player cooldown and economy behavior but use the same atomic falling, future-landed, and target-block admission. They report a typed limit rejection rather than spawning outside the hard caps.

All construction is routed through one admitted-drop helper. No public or internal path may directly instantiate and spawn a managed crate without a lease.

## Landing and Expiry

When the tracked falling block attempts to land:

1. use the event's target block and verify it exactly matches the lease reservation;
2. require an enabled plugin capable of scheduling expiry;
3. register the landed location collision-safely;
4. perform the barrel transition and contents insertion;
5. convert the lease from `FALLING` to `LANDED`, releasing only falling capacity;
6. schedule one delayed expiry task for the configured landed lifetime; and
7. start bounded landed effects.

The expiry task removes the crate through `CrateManager.removeCrateAndDestroy`. Opening a crate may stop its continuous effects as it does today, but does not cancel expiry. Empty close, block break, explosion, burn, world unload, failed landing, and expiry all converge on the same idempotent destruction boundary.

The delayed expiry adds at most one scheduled task per landed crate, and the hard landed cap bounds that total. Continuous glow/smoke tasks are bounded by the same cap and cannot outlive their crate.

## Reconnect, Reload, and Disable

Cooldowns use player UUIDs, so disconnecting and reconnecting does not reset the deadline. Expired deadlines are pruned during admissions so the map does not grow without bound.

Runtime reload does not recreate or clear the admission controller. New configured limits apply to future admission checks. If a new limit is below current occupancy, existing crates are not destroyed; new requests remain rejected until occupancy falls below the limit. Existing cooldown deadlines and landed-expiry deadlines keep their original timestamps rather than being reset or extended by reload.

Plugin disable follows an explicit order:

1. stop new admissions;
2. destroy every tracked crate, closing all leases;
3. clear residual admission/cooldown state;
4. cancel any remaining plugin tasks; and
5. clear other plugin singletons.

This order allows each crate to perform normal cleanup while the plugin instance is still available and leaves zero tracked leases or owned tasks afterward.

## Rejections and User Feedback

Admission returns a typed reason rather than a generic boolean:

- request already pending;
- cooldown active, including remaining whole seconds;
- falling capacity full;
- landed/future-landed capacity full;
- target block already reserved;
- plugin shutting down.

`DropCommand` maps these reasons to dedicated localized message keys. Capacity and collision rejections occur before economy withdrawal and entity construction. Programmatic callers receive the same reason through a typed exception/result contract.

## Testing

### Admission unit tests

- requests at and above each configured capacity;
- atomic concurrent acquisitions permit no oversubscription;
- future landed reservations prevent in-flight overcommit;
- cooldown starts only after successful spawn commit;
- rejected, charge-failed, and spawn-failed requests start no cooldown;
- cooldown bypass skips only cooldown and pending-player checks;
- operators with bypass are still rejected by falling and landed limits;
- reconnect uses the same UUID deadline;
- expired cooldowns are accepted and pruned;
- duplicate lease transitions and closes do not decrement twice;
- stopped admission rejects all new work;
- caps lowered below occupancy reject until leases close.

### Controller and economy tests

- capacity and target collision are rejected before `chargeUser` and `getItems`/entity spawn;
- charge failure releases both capacity claims and the target block;
- spawn or locally observable dispatch failure destroys partial resources, releases claims, and attempts one refund after confirmed withdrawal;
- payload failure occurs before withdrawal and releases the lease without a refund;
- charge-confirmation feedback failure does not roll back a successful drop;
- refund failure does not retain capacity;
- successful player spawn commits one cooldown;
- programmatic drops cannot bypass hard capacity.

### Crate and listener tests

- landing converts falling to landed without changing total landed claims;
- failed landing closes the lease and removes all partial resources;
- actual event-block/reserved-block mismatch fails before world mutation;
- inability to schedule expiry rejects landing rather than creating an immortal crate;
- same-location registration never replaces an existing tracked crate;
- expiry removes the barrel, cancels effects, and releases landed capacity;
- empty close, break, explosion, burn, chunk/world unload, and duplicate signals close once;
- plugin disable leaves zero leases, entities, tracked crates, and plugin tasks;
- reload preserves live leases/cooldowns and applies raised or lowered limits to future requests.

### Configuration and permission tests

- shipped defaults are `30`, `3`, `10`, and `600`;
- invalid values fall back to safe defaults;
- generated `plugin.yml` declares `airdrop.cooldown.bypass` as operator-default and an `airdrop.admin` child;
- no `airdrop.limits.bypass` compatibility alias is generated;
- localized rejection messages exist and contain their required placeholders.

### Manual Paper verification

Use `./gradlew runServer` to submit rapid requests from normal and bypass users, overlap falling drops, leave landed crates until expiry, reload with caps above and below current occupancy, reconnect during cooldown, unload a world, and disable the plugin. Observe that rejected calls cause no balance change or entity, accepted drops never exceed reserved capacity, and scheduler/entity state returns to zero after cleanup.

## Acceptance Criteria Mapping

- Separate request/falling/landed limits: one cooldown gate and two atomic capacity claims.
- Reserve before payment: both falling and future-landed capacity plus target location are claimed before withdrawal.
- No rejected charge/entity: every capacity/collision rejection precedes economy and spawn work.
- Cleanup releases capacity: all cleanup paths call the same terminal crate/lease boundary.
- Duplicate cleanup is harmless: crate destruction and lease close are idempotent.
- Reconnect/reload: UUID cooldowns and live leases remain intact.
- Multiple drops at one location: target reservation and collision-safe registration prevent replacement.
- Failed spawn/refund/world unload/disable: each path releases capacity independently of refund outcome.
- Landed time limit: delayed expiry destroys the crate and all continuous effects.
- Operator bypass: only `airdrop.cooldown.bypass` skips cooldown; no caller bypasses hard capacity.

## Open Questions

No product or architecture decisions remain. Default values are intentionally conservative starting points and remain configurable; real Paper validation may inform later tuning without changing the lease design.
