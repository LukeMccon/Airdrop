# AIRDR-3 VaultUnlocked Paid Drops Implementation Plan

> Implement each task test-first. Keep commits small and tagged `AIRDR-3`.

**Goal:** Replace Treasury with VaultUnlocked's optional native async economy API while retaining a safe legacy Vault fallback and a small server-first paid-drop lifecycle.

**Architecture:** Startup selects one provider: native `AsyncEconomy` when advertised by a modern VaultUnlocked service, otherwise the legacy Vault service. Each paid request owns a small in-memory `PaidDropSession`; all session transitions occur on Paper's server thread. `Crate` reports one terminal delivery outcome so a confirmed charge can be refunded once after a known failure.

**Tech stack:** Java 21, Paper 1.21.8, VaultUnlockedAPI 2.20, legacy Vault API 1.7, JUnit 5, Mockito, MockBukkit, Gradle.

## Invariants

- Do not call `join()`, `get()`, or `orTimeout()` on provider futures.
- Do not invoke legacy Vault providers off Paper's server thread.
- Do not touch Bukkit objects in native provider completion threads; schedule the result onto Paper first.
- Do not spawn a crate until withdrawal success is confirmed.
- Never automatically retry an ambiguous withdrawal or refund.
- A late confirmed withdrawal may start one refund, but never resurrect its cancelled crate.
- Do not add a journal, transaction commands, provider polling, or a coordinator executor.
- Preserve free/admin drop behavior.

---

## Task 1: Replace the economy boundary

**Files:**

- Modify: `build.gradle.kts`
- Modify: `src/main/java/com/airdropmc/economy/EconomyProvider.java`
- Modify: `src/main/java/com/airdropmc/economy/EconomyResult.java`
- Modify: `src/main/java/com/airdropmc/economy/VaultEconomyProvider.java`
- Create: `src/main/java/com/airdropmc/economy/EconomyPlayer.java`
- Create: `src/main/java/com/airdropmc/economy/VaultUnlockedEconomyProvider.java`
- Replace: `src/test/java/com/airdropmc/economy/TreasuryEconomyProviderTest.java`
- Create: `src/test/java/com/airdropmc/economy/VaultUnlockedEconomyProviderTest.java`
- Create: `src/test/java/com/airdropmc/economy/VaultEconomyProviderTest.java`

### Steps

1. Write failing tests proving:
   - `EconomyResult` distinguishes `SUCCESS`, `REJECTED`, and `UNKNOWN`.
   - modern affordability, withdrawal, and deposit delegate to `AsyncEconomy` with UUID, plugin name, and exact `BigDecimal`;
   - modern responses map success/failure correctly while exceptional completion maps to `UNKNOWN`;
   - legacy methods execute synchronously and return completed stages;
   - invalid or negative amounts are rejected without invoking a provider.
2. Run:

   ```bash
   ./gradlew test --tests 'com.airdropmc.economy.*'
   ```

   Confirm the new tests fail for missing contracts.
3. Add the creatorfromhell CodeMC repository and `compileOnly`/test dependency `net.milkbowl.vault:VaultUnlockedAPI:2.20`. Treasury remains temporarily until startup discovery is replaced in Task 2.
4. Change `EconomyProvider` to expose:
   - `boolean nativeAsync()`;
   - `CompletionStage<EconomyResult> canAfford(EconomyPlayer, BigDecimal)`;
   - `CompletionStage<EconomyResult> withdraw(EconomyPlayer, BigDecimal)`;
   - `CompletionStage<EconomyResult> deposit(EconomyPlayer, BigDecimal)`;
   - `String getName()`.
5. Implement `VaultUnlockedEconomyProvider` around a required `AsyncEconomy`; keep the raw mapped stages externally timeout-able without mutating them.
6. Adapt legacy Vault by resolving `OfflinePlayer` and performing each call immediately on the caller's server thread.
7. Delete the obsolete Treasury test and rerun the focused tests until green.
8. Commit:

   ```bash
   git add build.gradle.kts src/main/java/com/airdropmc/economy src/test/java/com/airdropmc/economy
   git commit -m "AIRDR-3: add VaultUnlocked economy boundary"
   ```

---

## Task 2: Select the provider once at startup

**Files:**

- Modify: `src/main/java/com/airdropmc/Airdrop.java`
- Delete: `src/main/java/com/airdropmc/economy/TreasuryEconomyProvider.java`
- Create: `src/main/java/com/airdropmc/economy/EconomyProviderDiscovery.java`
- Create: `src/test/java/com/airdropmc/economy/EconomyProviderDiscoveryTest.java`
- Modify: `src/test/java/com/airdropmc/config/PluginYmlPermissionsTest.java`

### Steps

1. Write failing discovery tests for:
   - a registered modern economy with `supportsAsync()` and a present `async()` is preferred;
   - a modern service without native async is skipped;
   - legacy Vault is selected when no native async service exists;
   - missing services return empty;
   - discovery is performed once and does not hot-swap registrations.
2. Add `EconomyProviderDiscovery` with modern API references isolated so original Vault-only servers fail gracefully when `vault2` classes are absent.
3. Replace Treasury-first setup in `Airdrop` with one discovery call during `onEnable()`.
4. Delete the Treasury adapter and its Gradle dependencies.
5. Keep `softdepend` as `Vault`: VaultUnlocked intentionally declares its plugin name as `Vault`. Remove `Treasury`.
6. Add a shutdown flag set before crate cleanup so late callbacks and crate destruction cannot start payment work during disable.
7. Run:

   ```bash
   ./gradlew test --tests com.airdropmc.economy.EconomyProviderDiscoveryTest \
     --tests com.airdropmc.config.PluginYmlPermissionsTest
   ```

8. Commit:

   ```bash
   git add build.gradle.kts src/main/java/com/airdropmc/Airdrop.java \
     src/main/java/com/airdropmc/economy/EconomyProviderDiscovery.java \
     src/test/java/com/airdropmc/economy/EconomyProviderDiscoveryTest.java \
     src/test/java/com/airdropmc/config/PluginYmlPermissionsTest.java
   git commit -m "AIRDR-3: select VaultUnlocked at startup"
   ```

---

## Task 3: Introduce the small paid-drop session

**Files:**

- Create: `src/main/java/com/airdropmc/paid/PaidDropSession.java`
- Modify: `src/main/java/com/airdropmc/controllers/DropController.java`
- Modify: `src/main/java/com/airdropmc/packages/Package.java`
- Modify: `src/main/java/com/airdropmc/commands/DropCommand.java`
- Replace: `src/test/java/com/airdropmc/controllers/DropControllerEconomyFlowTest.java`
- Modify/Delete: `src/test/java/com/airdropmc/packages/PackageChargeTest.java`
- Create: `src/test/java/com/airdropmc/paid/PaidDropSessionTest.java`

### Steps

1. Write failing tests proving:
   - permissions, target validation, cloned contents, and admission happen before payment;
   - one player's pending lease rejects a second request;
   - affordability rejection sends the existing cannot-afford result and releases the lease;
   - affordability timeout/failure creates no crate and releases the lease;
   - confirmed withdrawal schedules exactly one server-thread spawn;
   - withdrawal rejection/exception/timeout creates no crate;
   - a late success after timeout starts one refund and never spawns;
   - a player disconnect does not cancel a confirmed charge or delivery;
   - a zero-price package bypasses economy mutation.
2. Implement `PaidDropSession` with server-thread-confined phases: `CHECKING`, `WITHDRAWING`, `FALLING`, `DELIVERED`, `CANCELLED`, and `REFUNDING`.
3. Attach provider callbacks only to post immutable results through `Bukkit.getScheduler().runTask(...)`.
4. For native async providers, schedule an independent 100-tick timeout without cancelling the raw provider stage. Do not apply that timeout to completed legacy calls.
5. Let `DropController.playerInitiatedDropPackage` retain synchronous preflight exceptions, then hand the captured request to the session and return.
6. Remove synchronous payment helpers from `Package`; keep price and item behavior unchanged.
7. Run:

   ```bash
   ./gradlew test --tests com.airdropmc.paid.PaidDropSessionTest \
     --tests com.airdropmc.controllers.DropControllerEconomyFlowTest \
     --tests com.airdropmc.commands.DropCommandPackageIdentityTest
   ```

8. Commit:

   ```bash
   git add src/main/java/com/airdropmc/paid src/main/java/com/airdropmc/controllers/DropController.java \
     src/main/java/com/airdropmc/packages/Package.java src/main/java/com/airdropmc/commands/DropCommand.java \
     src/test/java/com/airdropmc/paid src/test/java/com/airdropmc/controllers/DropControllerEconomyFlowTest.java \
     src/test/java/com/airdropmc/packages/PackageChargeTest.java
   git commit -m "AIRDR-3: coordinate paid drops in memory"
   ```

---

## Task 4: Report crate delivery and refund known failures

**Files:**

- Modify: `src/main/java/com/airdropmc/Crate.java`
- Modify: `src/main/java/com/airdropmc/listeners/FallingCrateListener.java`
- Modify: `src/test/java/com/airdropmc/CrateLandingLifecycleTest.java`
- Modify: `src/test/java/com/airdropmc/CrateDestroyTest.java`
- Modify: `src/test/java/com/airdropmc/listeners/FallingCrateListenerTest.java`
- Extend: `src/test/java/com/airdropmc/paid/PaidDropSessionTest.java`

### Steps

1. Write failing tests proving:
   - `LANDED` is emitted once after barrel creation and item insertion;
   - destroying an undelivered crate emits `FAILED` once;
   - partial spawn, lost falling entity, null landing block, landing exception, chunk/world cleanup, and duplicate destroy signals produce at most one failure;
   - a known post-charge failure cleans up before starting one refund;
   - successful landing, opening, expiry, and later destruction never refund;
   - plugin shutdown cleanup does not refund.
2. Add an optional one-shot outcome listener to `Crate`, preserving the existing constructor as a no-op overload for free/admin drops.
3. Emit `LANDED` only after insertion and lease transition succeed. Emit `FAILED` only when an undelivered crate is destroyed.
4. Connect paid crate outcomes to `PaidDropSession`; guard refund with one-shot session state.
5. On confirmed refund send the final refund message. On rejection/unknown result log once and do not retry.
6. Run the focused crate/listener/session tests until green.
7. Commit:

   ```bash
   git add src/main/java/com/airdropmc/Crate.java src/main/java/com/airdropmc/listeners/FallingCrateListener.java \
     src/test/java/com/airdropmc/CrateLandingLifecycleTest.java \
     src/test/java/com/airdropmc/CrateDestroyTest.java \
     src/test/java/com/airdropmc/listeners/FallingCrateListenerTest.java \
     src/test/java/com/airdropmc/paid/PaidDropSessionTest.java
   git commit -m "AIRDR-3: refund failed paid deliveries"
   ```

---

## Task 5: Keep the public surface small

**Files:**

- Modify: `src/main/java/com/airdropmc/lang/MessageKey.java`
- Modify: `src/main/resources/lang/en.yml`
- Modify: `README.md`
- Create: `src/test/java/com/airdropmc/lang/PaidDropMessageTest.java`
- Modify: affected legacy tests after Treasury removal

### Steps

1. Add tests that require only two new keys: generic no-crate failure and confirmed-refund failure.
2. Update startup text and README requirements to VaultUnlocked/native async with original Vault compatibility fallback.
3. Ensure no transaction commands, IDs, progress messages, journal configuration, or timeout configuration were introduced.
4. Search for stale Treasury references:

   ```bash
   rg -n "Treasury|treasury-api|transaction resolve|transactions.yml" \
     README.md build.gradle.kts src/main src/test
   ```

   Expected: no product or dependency references.
5. Run:

   ```bash
   ./gradlew test
   ```

6. Commit:

   ```bash
   git add README.md src/main/java/com/airdropmc/lang/MessageKey.java src/main/resources/lang/en.yml \
     src/test/java/com/airdropmc/lang/PaidDropMessageTest.java
   git commit -m "AIRDR-3: document VaultUnlocked paid drops"
   ```

---

## Task 6: Verify and independently double-check

1. Run clean verification:

   ```bash
   ./gradlew clean test build --rerun-tasks
   git diff --check 4.0-beta...HEAD
   git status --short
   ```

2. Run a Paper smoke test with VaultUnlocked plus a compatible provider when the local fixture permits:
   - successful paid drop;
   - insufficient funds;
   - forced landing failure and one refund.
3. Invoke the requested `doublecheck` skill through a separate review agent. Give it the approved design, implementation diff, test results, and the official VaultUnlocked sources. Address verified findings test-first.
4. Rerun clean verification after review fixes.
5. Commit any review corrections with `AIRDR-3` in the subject.

## Expected scope

- One modern adapter, one small session class, and a one-shot crate callback.
- No durable state machine, coordinator executor, transaction UI, or operational journal.
- A focused number of new tests; no target test count.
