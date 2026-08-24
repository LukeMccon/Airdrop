# AIRDR-3 VaultUnlocked Paid Drops Design

**Work item:** AIRDR-3

**Target branch:** `4.0-beta`

**Status:** Supersedes the earlier Treasury/coordinator design

## Decision

Airdrop 4.0 will prefer VaultUnlocked's modern economy API and remove Treasury support.

Provider selection happens once during startup:

1. Use `net.milkbowl.vault2.economy.Economy` when VaultUnlocked and a modern provider are registered.
2. Use its native `AsyncEconomy` only when the provider advertises `supportsAsync()` and supplies `async()`.
3. Otherwise use the legacy `net.milkbowl.vault.economy.Economy` service. That service may be supplied by VaultUnlocked or original Vault.
4. If economy is enabled and neither service exists, retain the current behavior of disabling Airdrop with one startup error.

Changing economy providers requires a server restart. Airdrop will not poll for providers or hot-swap them at runtime.

## Scope

### Goals

- Remove Treasury and its blocking `join()` calls.
- Use VaultUnlocked's UUID, `BigDecimal`, and native asynchronous operations when genuinely supported by the active provider.
- Keep legacy Vault compatibility without invoking arbitrary providers off-thread.
- Validate the drop and reserve capacity before charging.
- Never create a crate unless withdrawal success is confirmed.
- Refund once when a confirmed charge is followed by a known crate failure.
- Keep player-facing output limited to final outcomes.
- Keep the change small enough to review and maintain.

### Non-goals

- General Folia support. VaultUnlocked being Folia-compatible does not make Airdrop itself Folia-compatible.
- Durable transaction recovery across a process crash.
- Transaction administration commands or a `transactions.yml` journal.
- Runtime provider switching, multi-currency configuration, or a general payment framework.
- Exactly-once external effects when an economy provider returns an ambiguous result.

The omitted crash recovery is an intentional server-first tradeoff: after a JVM or server crash, Airdrop will not automatically create goods or issue a possibly duplicate refund. A rare player charge may therefore require ordinary server-owner support, but a crash cannot cause Airdrop to mint a crate or repeatedly refund money.

## Threading Model

Paper world, entity, inventory, event, command, permission, and message operations stay on Paper's server thread.

The modern VaultUnlocked adapter returns `CompletionStage` results:

- A native `AsyncEconomy` withdrawal or deposit remains asynchronous.
- Completion callbacks contain only payment data and schedule any Bukkit work back onto Paper's server thread.
- Airdrop never calls `join()` or `get()` on a provider future.
- A 100-tick Paper task races the provider result without cancelling or mutating the provider-owned future.

VaultUnlocked's `EconomyFutures` helper will not be used as proof of asynchronous execution: when native async support is absent, it executes the synchronous economy call inline and wraps the result in a completed future.

The legacy Vault adapter calls synchronous methods on Paper's server thread and returns an already-completed stage. This is the compatibility behavior used by most existing plugins. It can briefly delay a tick if the economy provider is slow, but avoids making unsupported thread-safety assumptions.

No plugin-owned coordinator thread is introduced.

## Economy Contract

The internal economy interface exposes affordability, withdrawal, and deposit as completion stages and reports:

- `SUCCESS`: the provider confirmed the mutation.
- `REJECTED`: the provider returned a normal failure, including insufficient funds.
- `UNKNOWN`: the invoked operation timed out or completed exceptionally without proving whether money moved.

The modern adapter calls VaultUnlocked with the player's UUID, the exact `BigDecimal` created from the validated package price, and Airdrop's plugin name. The legacy adapter retains the existing `Player`-based calls.

The request captures cloned package contents, payer UUID/name, world UUID, and the validated target before asynchronous work begins. A disconnect does not cancel a confirmed charge or delivery; messages are best-effort when the player is online.

The affordability result is advisory and preserves the existing cannot-afford message without starting a mutation. A successful check is followed by withdrawal, whose result remains authoritative because another plugin may change the balance between calls.

## Paid Drop Flow

1. On Paper's server thread, validate the package, permission, sky/landing column, inventory capacity, and drop limits.
2. Acquire the existing per-player/drop-location admission lease before payment.
3. Check affordability. A confirmed negative result closes the lease and sends the existing cannot-afford message. An unavailable or timed-out result closes the lease with no mutation and the generic failure message.
4. After a confirmed positive check, start one withdrawal and handle its result:
   - `REJECTED`: close the lease and create no crate.
   - `UNKNOWN`: close the lease, create no crate, log the ambiguity, and allow the player to retry.
   - `SUCCESS`: schedule crate creation on Paper's server thread.
5. If crate creation throws, clean up known resources and start one refund.
6. If creation succeeds, send the existing charged message and wait for the crate's one-shot outcome.
7. A successful landing completes the paid request.
8. A known failure while the crate is still falling cleans it up and starts one refund.

`Crate` gains a small optional one-shot outcome listener:

- `LANDED` fires only after the barrel exists and all contents have been inserted.
- `FAILED` fires when creation, landing, entity loss, chunk/world cleanup, or another existing destroy path removes an undelivered crate.
- Free and administrative drops use the same no-op default behavior as today.
- Destroying an already landed crate never requests a refund.
- Plugin shutdown suppresses new economy work; it cleans up crates but does not attempt refunds during disable.

## Timeouts and Late Results

The 100-tick payment timeout applies only to native asynchronous VaultUnlocked operations.

- Withdrawal times out: cancel the drop, release its lease, log an uncertain charge, and do not refund automatically.
- The same withdrawal later reports rejection: no action.
- The same withdrawal later reports success while this plugin instance is still enabled: issue one refund and never create the cancelled crate.
- Refund times out or fails ambiguously: log it and never retry automatically.

Each request has a small in-memory phase (`CHECKING`, `WITHDRAWING`, `FALLING`, `DELIVERED`, `CANCELLED`, or `REFUNDING`). Provider callbacks immediately schedule onto Paper's server thread, so all phase transitions happen there. Separate one-shot flags for delivery and refund prevent a timeout, late provider completion, crate callback, or duplicate cleanup signal from spawning or refunding twice.

## User-Facing Behavior

Keep existing permission, capacity, cooldown, economy-unavailable, cannot-afford, and charged messages.

Add only two final-outcome messages:

- Generic failure: the airdrop failed and no crate was created. This also covers an uncertain withdrawal without claiming the player was not charged.
- Confirmed refund: the airdrop failed and the payment was refunded.

There are no progress messages, transaction IDs, review commands, or manual-resolution prompts.

Update startup text and README documentation from "Treasury or Vault" to "VaultUnlocked or Vault." Add no new user-facing configuration; the native async timeout is a conservative internal constant.

## Testing

Add focused tests, not an exhaustive transaction-state suite:

- Provider discovery prefers modern VaultUnlocked and falls back to legacy Vault at startup.
- Native async success, rejection, timeout, late success/refund, and ambiguous refund.
- Legacy calls occur on the server thread.
- Confirmed withdrawal creates one crate; rejected or unknown withdrawal creates none.
- Spawn and falling/landing failures trigger at most one refund.
- Successful landing and later crate expiry never refund.
- Shutdown cleanup starts no new payment operation.
- Existing free/admin drop behavior and the full existing Gradle test suite remain green.

A manual Paper smoke test should cover one successful paid drop, insufficient funds, and forced landing failure with a VaultUnlocked-compatible economy provider.

## Verified Constraints

- VaultUnlocked 2.20 introduced optional `AsyncEconomy`, `supportsAsync()`, and `async()`: <https://github.com/TheNewEconomy/VaultUnlocked/releases/tag/2.20.0>.
- `Economy#async()` defaults to empty, so modern API presence alone does not promise native async execution: <https://github.com/TheNewEconomy/VaultUnlockedAPI/blob/master/src/main/java/net/milkbowl/vault2/economy/Economy.java>.
- `EconomyFutures` falls back by executing the synchronous method and wrapping its result in `CompletableFuture.completedFuture(...)`: <https://github.com/TheNewEconomy/VaultUnlockedAPI/blob/master/src/main/java/net/milkbowl/vault2/economy/EconomyFutures.java>.
- VaultUnlocked documents backward compatibility with original Vault consumers while requiring explicit provider implementation for its enhanced API: <https://github.com/TheNewEconomy/VaultUnlockedAPI>.
- Treasury's latest published release is 2.0.1 from July 2023: <https://github.com/ArcanePlugins/Treasury/releases/tag/2.0.1>.
