# AIRDR-3 Paid Drop Lifecycle Design

**Work item:** AIRDR-3

**Branch:** `4.0-beta`

**Scope:** Make Treasury-backed paid drops non-blocking, track each paid request to a final drop outcome, and clean up or refund failed deliveries without moving Bukkit world or entity work off Paper's server thread.

## Decision Summary

Airdrop will use concurrency across an explicit boundary:

- Paper/Bukkit access stays on the server thread. This includes commands, permissions, player messaging, world/chunk inspection, entities, blocks, inventories, events, and plugin lifecycle work.
- Treasury remains natively asynchronous. Airdrop composes its futures without calling `join()` or `get()` on the server thread.
- Legacy Vault remains synchronous on the server thread. Vault exposes synchronous methods and does not give Airdrop a universal thread-safety contract for third-party providers.
- A single plugin-owned coordinator executor serializes transaction state, journal writes, provider completions, wall-clock timeouts, and retry scheduling.
- Coordinator events schedule Bukkit phases on Paper's server thread and receive immutable results back. No provider callback or persistence task accesses Bukkit objects directly.

The legacy Vault exception is an intentional, user-approved narrowing of AIRDR-3. The Plane acceptance text should be amended before implementation so the issue does not claim that every provider path is non-blocking. Supporting VaultUnlocked's asynchronous API is a separate enhancement.

## Goals

- Keep Paper ticking while Treasury resolves accounts, balances, withdrawals, deposits, or timeouts.
- Permit at most one unsettled Airdrop payment for each player.
- Validate permissions and the landing column before charging.
- Define delivery as successful barrel creation followed by insertion of every package item.
- Give every paid request a transaction ID, payer, exact amount, provider/currency identity, final request outcome, and settlement status.
- Remove Airdrop-owned entities, blocks, tasks, and manager entries before refunding a failed delivery.
- Prevent duplicate Airdrop charges, drops, and automatic refunds.
- Preserve unresolved settlements across restart.
- Never interpret an ambiguous provider result as proof that money did or did not move.

## Non-goals

- General Folia support.
- Running arbitrary legacy Vault providers asynchronously.
- Replacing Vault with Treasury or VaultUnlocked.
- A general ledger or double-entry accounting system.
- Mathematically exact once-only external effects across a JVM crash. Vault and Treasury 2.0.1 do not provide a contractual idempotency key or authoritative unique transaction lookup.

## Core Model

### `PaidDropCoordinator`

An instance owned by `Airdrop` coordinates the paid path that currently lives in static `DropController.playerInitiatedDropPackage` methods.

It owns:

- one serialized coordinator event queue;
- the active-player reservation map;
- legal request and settlement transitions;
- journal persistence;
- economy phase timeouts and raw future completions;
- dispatch to and from the Paper server thread;
- recovery and shutdown.

The existing direct `DropController.dropPackage` APIs remain for free and administrative drops. `DropCommand` starts a paid request and returns immediately; later server-thread messages report charge, timeout, delivery, refund, or manual-review outcomes.

### Durable identity

Each transaction persists:

- transaction UUID;
- payer UUID and last-known name;
- package name;
- amount as its original validated decimal string;
- provider kind, provider key/name, and registrar identity where available;
- exact Treasury currency identifier where applicable;
- world UUID and landing-column coordinates;
- request state and settlement state;
- delivery phase (`NOT_STARTED`, `FALLING`, or `LANDING_IN_PROGRESS`) and exact landing block once known;
- relevant entity UUIDs and block location once known;
- timestamps and the last failure text.

Recovery never silently changes provider or Treasury currency. A missing or changed provider leaves the settlement unresolved for administrator review.

No live `Player`, `World`, `Location`, `Entity`, `Inventory`, or `ItemStack` is retained across threads. The current-runtime request owns cloned package contents and drop options.

### Two state dimensions

Drop outcome and financial settlement are separate. This lets a timed-out command reach one final drop outcome even when the provider's financial result is unknowable.

Request state:

```text
PREPARING -> PAYMENT_PENDING -> SPAWN_PENDING -> DROP_ACTIVE
     |              |                |              |
     +--------------+----------------+--------------+-> FAILED
                                                   \-> DELIVERED
                                                   \-> REVIEW_REQUIRED
```

`FAILED`, `DELIVERED`, and `REVIEW_REQUIRED` are immutable final request outcomes. `REVIEW_REQUIRED` means a crash boundary made delivery unknowable. No crate is created after a request becomes `FAILED`.

Settlement state:

```text
NOT_STARTED
  -> BALANCE_PENDING
  -> WITHDRAW_IN_FLIGHT
  -> UNCHARGED | CHARGED | OUTCOME_UNKNOWN

CHARGED
  -> CLEANUP_PENDING
  -> REFUND_PENDING
  -> REFUND_IN_FLIGHT
  -> REFUNDED | OUTCOME_UNKNOWN
```

`UNCHARGED`, `REFUNDED`, and a delivered request with `CHARGED` are settled. `OUTCOME_UNKNOWN` is a durable operational outcome requiring review and keeps the player's paid-drop lock. A late raw provider completion may supply new evidence and advance only the settlement; it never changes the final request outcome or creates a crate.

All state changes occur on the coordinator executor. Duplicate cleanup, landing, timer, disable, and future-completion events are ignored when they do not match the current legal state.

### Provider outcomes

Economy adapters report one of three outcomes:

- `SUCCESS`: the adapter has evidence the requested mutation succeeded.
- `REJECTED_NO_EFFECT`: the adapter can prove it did not invoke a mutation or the provider contract explicitly guarantees rejection had no effect.
- `OUTCOME_UNKNOWN`: an invoked mutation timed out, completed exceptionally without a no-effect guarantee, or was interrupted by shutdown.

Treasury 2.0.1 mutation futures return a balance or complete exceptionally; the API does not define a failure taxonomy proving an exceptional completion had no side effect. Therefore, once a Treasury withdrawal or deposit is invoked, a timeout or exceptional completion becomes `OUTCOME_UNKNOWN`, not an automatic retry.

An automatic refund retry is allowed only when no provider mutation was invoked or an adapter can prove `REJECTED_NO_EFFECT`. This conservative policy favors a visible unresolved transaction over duplicate currency.

## Economy Abstraction

`EconomyProvider` becomes identity-based and completion-stage based:

```java
ExecutionModel executionModel(); // NATIVE_ASYNC or SERVER_THREAD
CompletionStage<BalanceResult> getBalance(PayerIdentity payer);
CompletionStage<EconomyResult> withdraw(
        PayerIdentity payer, BigDecimal amount, UUID transactionId);
CompletionStage<EconomyResult> deposit(
        PayerIdentity payer, BigDecimal amount, UUID transactionId);
ProviderIdentity identity();
```

- `TreasuryEconomyProvider` uses payer UUID directly and starts only one API phase at a time. It includes the Airdrop transaction ID in the transaction reason.
- `VaultEconomyProvider` is invoked only from the server thread, resolves `OfflinePlayer` there, and returns a completed stage. `PayerIdentity` retains both UUID and name because Vault's standard `AbstractEconomy` compatibility methods may delegate `OfflinePlayer` operations to a player name.
- The coordinator converts the validated package `double` once with `BigDecimal.valueOf(price)` and retains the resulting decimal string.

The withdrawal result is authoritative. A balance check improves error messaging but cannot prevent another plugin from changing the account before withdrawal.

## Timeout Semantics

Airdrop must not call `CompletableFuture.orTimeout()` on a provider-owned future because that mutates the same future and can hide late completion. It must not assume `cancel()` stopped the provider's underlying work.

For each Treasury phase:

1. Keep the raw provider future untouched.
2. Attach a raw `whenComplete` callback that posts a coordinator event.
3. Schedule an independent wall-clock timeout event, defaulting to five seconds.
4. Let the serialized coordinator accept whichever event is legal for the current phase.
5. Start the next provider phase only after the prior result is accepted and its next state is durable. Do not pre-compose a lookup/balance/withdrawal chain that can continue after an earlier timeout.

An account or balance timeout occurs before a mutation and finishes the request as `FAILED`/`UNCHARGED`. A withdrawal timeout finishes the request as `FAILED`/`OUTCOME_UNKNOWN`; no crate is created. If the untouched withdrawal future later succeeds, the coordinator changes settlement to `CHARGED`, then begins cleanup/refund recovery while keeping the request `FAILED`.

Legacy Vault has no meaningful Airdrop timeout without either blocking the server thread or invoking an arbitrary provider off-thread. A startup warning documents this compatibility limitation.

## Persistence

`PaidDropJournal` stores unresolved transactions plus the most recent 1,000 terminal records in `transactions.yml`.

- Coordinator events serialize all journal mutations.
- Writes use a temporary file and atomic replacement, following the repository's existing safe configuration-write pattern.
- A failed write fails closed: the next external side effect does not begin.
- A state authorizing an economy or Bukkit side effect is durable before dispatch.
- The journal records the final request and settlement state before releasing the active-player lock.
- `/airdrop transaction list` lists unresolved transactions. `/airdrop transaction resolve <id> <delivered|refunded|uncharged>` records an externally verified result, releases the lock when appropriate, and writes an audit log. It does not perform an economy mutation.

## Request Flow

### 1. Server-thread preflight

1. Resolve the package and check its permission.
2. Clone package contents and drop options.
3. Capture payer identity, world UUID, and current column.
4. Calculate the intended surface and spawn locations.
5. Verify the player is not under cover, the landing block is usable, the target is within world bounds, and the contents fit in an empty barrel.
6. Post the immutable preflight result to the coordinator.
7. The coordinator atomically reserves the player, creates the transaction, and persists it before payment begins.

The required Paper integration test determines exact empty-column and height-map behavior. The implementation must encode observed Paper behavior instead of assuming MockBukkit matches it.

### 2. Charge

For Treasury, the coordinator runs account lookup, balance retrieval, and withdrawal as separate guarded phases. It persists `WITHDRAW_IN_FLIGHT` immediately before invoking withdrawal.

For Vault, it persists the same intent, schedules the synchronous call on the server thread, and posts the result back to the coordinator.

A confirmed successful withdrawal persists `CHARGED` and moves the request to `SPAWN_PENDING`. A rejection produces `FAILED`/`UNCHARGED`. An ambiguous mutation produces `FAILED`/`OUTCOME_UNKNOWN` and never spawns.

### 3. Spawn

After confirmed charge:

1. Persist request `SPAWN_PENDING` and settlement `CHARGED`.
2. Schedule one server-thread spawn operation.
3. Confirm the plugin is enabled and the original world still exists.
4. Revalidate the landing column because the world may have changed during payment.
5. Create the crate, parachute entities, and effects while registering each resource immediately in a local `DropResources` accumulator.
6. Mark Airdrop-owned entities with the transaction UUID where the Paper API permits it.
7. Return created entity UUIDs and locations to the coordinator.
8. Persist those identifiers, transition to `DROP_ACTIVE`, and start the delivery timeout, defaulting to 120 seconds.

The spawn operation returns its `DropResources` accumulator from a `finally` path even when spawning throws. The coordinator persists `CLEANUP_PENDING` before scheduling cleanup.

A restart while still `SPAWN_PENDING` is ambiguous because entities may exist without their identifiers being committed. It recovers to request `REVIEW_REQUIRED` with settlement `CHARGED`, logs prominently, and does not automatically refund.

### 4. Landing and delivery

`FallingCrateListener` cancels normal falling-block placement, removes the falling entity, and submits an immutable landing intent. It does not mutate the barrel immediately.

The coordinator persists the exact landing block and a landing-in-progress marker before scheduling one server-thread landing operation. That operation:

- revalidates that the target block is still replaceable and never overwrites an unrelated player block;
- creates and verifies the barrel;
- marks the barrel's persistent data with the transaction UUID;
- inserts cloned contents;
- treats any exception or overflow as failure;
- on failure, clears inserted contents and reports the partial resource set;
- on success, registers the landed crate and reports complete insertion.

After a successful result, the coordinator persists request `DELIVERED` with settlement `CHARGED`, cancels the delivery timeout, then releases the player lock. `PackageLandEvent` fires on the server thread after the durable delivery decision. Another plugin's event-listener exception is logged and does not refund delivered goods.

A restart while landing or while `DROP_ACTIVE` becomes request `REVIEW_REQUIRED` with settlement `CHARGED`; it never automatically refunds because the barrel may already contain the package. Transaction PDC markers and stored locations support operator diagnosis and cleanup, but AIRDR-3 does not claim it can reconstruct every partially committed inventory automatically.

A player disconnect never cancels a confirmed charged delivery. Status messages are sent only if the player is online.

### 5. Cleanup and refund

All failure sources post one coordinator event:

- spawn failure;
- falling timeout;
- missing/unloaded world;
- chunk/world cleanup;
- falling entity removal;
- landing failure or overflow;
- plugin disable before delivery.

For a confirmed charged failure:

1. Persist final request `FAILED` and settlement `CLEANUP_PENDING`, including every known entity UUID and block location.
2. Schedule idempotent server-thread cleanup.
3. Remove known falling/parachute entities, partial barrels/contents, effects, tasks, and all manager entries.
4. Return cleanup results to the coordinator.
5. Only after successful cleanup, persist `REFUND_PENDING`.
6. Invoke the original provider and currency for the refund according to its execution model.

Recovery reruns `CLEANUP_PENDING` before considering a refund. Duplicate signals cannot skip cleanup or start another refund.

A successful deposit persists `REFUNDED`. A provider unavailable before invocation remains `REFUND_PENDING` and can resume after restart. An invoked refund that times out or completes exceptionally without a no-effect guarantee becomes `OUTCOME_UNKNOWN` and is not automatically retried.

## Disable and Recovery

During `onDisable`:

1. Stop accepting new paid requests.
2. Submit a coordinator shutdown-preparation event and wait for its bounded result. That event persists state and returns immutable cleanup work; it must not schedule Bukkit work while the server thread waits.
3. Convert pre-mutation requests to `FAILED`/`UNCHARGED`.
4. Convert invoked, unfinished mutations to `FAILED`/`OUTCOME_UNKNOWN`.
5. Convert confirmed charged, undelivered requests to `FAILED`/`CLEANUP_PENDING`.
6. Run the returned cleanup work directly on the current server thread.
7. Submit cleanup results, flush the journal, and close the coordinator executor within a bounded interval.
8. Prevent late completions from scheduling Bukkit work after shutdown begins.

During shutdown, a successful cleanup may persist `REFUND_PENDING`, but it never invokes the economy provider. Recovery starts that refund after the next enable has restored the recorded provider and currency.

Late provider completion after the journal is closed is ignored by the disabled instance; the durable ambiguous state remains for the next enable.

Recovery is exhaustive:

| Durable state | Recovery action |
|---|---|
| `NOT_STARTED`, `BALANCE_PENDING` | `FAILED` / `UNCHARGED` |
| `WITHDRAW_IN_FLIGHT` | `FAILED` / `OUTCOME_UNKNOWN` |
| `CHARGED` + `SPAWN_PENDING` | `REVIEW_REQUIRED` / `CHARGED` |
| `CHARGED` + `DROP_ACTIVE` or landing in progress | `REVIEW_REQUIRED` / `CHARGED` |
| `CLEANUP_PENDING` | rerun cleanup, then `REFUND_PENDING` |
| `REFUND_PENDING` | resume only with the recorded provider/currency |
| `REFUND_IN_FLIGHT` | `FAILED` / `OUTCOME_UNKNOWN` |
| `OUTCOME_UNKNOWN` | retain lock and require review |
| `DELIVERED` + `CHARGED` | terminal; no action |
| `FAILED` + `UNCHARGED` or `REFUNDED` | terminal; no action |

## Configuration and Messages

Add validated defaults:

```yaml
economy:
  operation-timeout-seconds: 5

drop:
  delivery-timeout-seconds: 120
```

Add localized messages for an active request, payment timeout, charge rejection, delivery failure, refund pending, refund complete, and administrator review. Timeout messaging states that no crate will be created and that an uncertain transaction has been recorded when applicable.

## Testing Strategy

### Unit tests

- Treasury account, balance, withdrawal, and refund futures that never complete.
- Raw completion racing the timeout event in both orders.
- No later economy phase starts after an earlier phase times out.
- Withdrawal timeout followed by late success starts one cleanup/refund path and never a crate.
- Exceptional Treasury mutations become `OUTCOME_UNKNOWN`, not automatic retries.
- Two commands from one player create one reservation and one charge.
- Every legal transition and every durable-state recovery mapping.
- Journal failure prevents the next external side effect.
- Changed/missing provider or Treasury currency after restart remains unresolved.
- Duplicate cleanup/landing/timeout signals cause no duplicate drop or refund.
- Disable racing with successful withdrawal and server-thread scheduling.
- Offline Vault identity retains UUID and last-known name.

### MockBukkit tests

- Bukkit preflight, Vault calls, spawning, events, messaging, landing, and cleanup occur on the server thread.
- Successful Treasury payment schedules exactly one server-thread drop.
- Disconnect before payment completion and after charge.
- Partial spawn, entity removal, chunk unload, world unload, null-world landing, exception, and overflow.
- `CLEANUP_PENDING` removes every known manager entry, task, entity, partial barrel, and inserted item before refund.
- Crash-recovery simulations at `SPAWN_PENDING`, `DROP_ACTIVE`, landing-in-progress, and `REFUND_IN_FLIGHT` never automatically refund an ambiguous outcome.
- Timeout messages contain the expected actionable text.

### Paper integration test

- Observe the chosen height map on empty columns, normal terrain, fluid, leaves, and world-height boundaries.
- Confirm ticks continue while a Treasury future remains incomplete.

## Rollout

- Free and administrative drops keep their synchronous server-thread behavior.
- Treasury remains preferred when both providers exist.
- Vault remains a supported fallback and emits a startup warning describing its synchronous compatibility path.
- Existing configuration continues to work with the new defaults.
- Documentation distinguishes non-blocking Treasury from provider-dependent legacy Vault.

## Verified Constraints

- Paper documents that synchronous tasks execute on the main server thread and that world-changing Bukkit access is unsafe from asynchronous tasks: <https://docs.papermc.io/paper/dev/scheduler/>.
- Java documents that `CompletableFuture.cancel()` does not control the underlying computation: <https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html>.
- Treasury 2.0.1 exposes UUID account lookup and future-returning balance mutations, but no contractual idempotency key: <https://github.com/ArcanePlugins/Treasury/blob/7417830c62488badd0f384f45e2bb89fcb6af3cf/api/src/main/java/me/lokka30/treasury/api/economy/account/Account.java>.
- Vault 1.7 exposes synchronous economy methods, and its `AbstractEconomy` compatibility layer may reduce `OfflinePlayer` calls to player names: <https://github.com/MilkBowl/VaultAPI/blob/68f14eca202d9b7f1a0fe5a4dcfcfbe26d4a6a40/src/main/java/net/milkbowl/vault/economy/AbstractEconomy.java>.

## Known Limitation

Airdrop can guarantee one final drop outcome and prevent duplicate actions in every state it controls. It cannot prove whether a third-party provider applied a mutation when the provider returns no authoritative outcome or the process dies mid-call. Such settlements remain visible as `OUTCOME_UNKNOWN`; Airdrop does not guess, automatically retry, or silently release the player lock.
