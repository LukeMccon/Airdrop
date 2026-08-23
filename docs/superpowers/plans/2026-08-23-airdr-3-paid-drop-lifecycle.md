# AIRDR-3 Paid Drop Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Treasury-backed paid drops non-blocking while durably tracking charge, delivery, cleanup, refund, and ambiguous provider outcomes without moving Bukkit world work off Paper's server thread.

**Architecture:** `PaidDropCoordinator` owns one serialized event loop and the durable state machine. Economy adapters expose completion stages and declare whether they run natively async or on Paper's server thread; `PaidDropWorldService` is the only paid-flow component allowed to retain and manipulate Bukkit objects. `PaidDropJournal` writes immutable transaction snapshots before every external side effect, allowing conservative restart recovery and administrator resolution.

**Tech Stack:** Java 21, Paper API 1.21.8, Treasury API 2.0.1, Vault API 1.7, Bukkit YAML, JUnit 5, Mockito, MockBukkit, Gradle.

---

## Scope and implementation invariants

- Work only on branch `4.0-beta` and tag every commit with `AIRDR-3`.
- Amend AIRDR-3 in Plane before code lands: Treasury is non-blocking; legacy Vault intentionally stays synchronous on Paper's server thread because Vault has no universal async contract.
- Never call `join()`, `get()`, or `orTimeout()` on an economy future.
- Never call Bukkit world, entity, inventory, player-message, event, or scheduler APIs from the coordinator event-loop thread.
- Persist a state authorizing an external side effect before invoking that side effect.
- Treat any invoked Treasury mutation that times out or completes exceptionally as `OUTCOME_UNKNOWN`.
- Never retry an ambiguous withdrawal or refund automatically.
- Clean up every known Airdrop-owned resource before changing `CLEANUP_PENDING` to `REFUND_PENDING`.
- Keep the player reservation until the transaction is delivered, confirmed uncharged, confirmed refunded, or manually resolved.
- Keep existing free and administrative drop behavior synchronous on Paper's server thread.

## File map

### Economy boundary

- Create `src/main/java/com/airdropmc/economy/ExecutionModel.java`: declares `NATIVE_ASYNC` and `SERVER_THREAD`.
- Create `src/main/java/com/airdropmc/economy/EconomyOutcome.java`: declares `SUCCESS`, `REJECTED_NO_EFFECT`, and `OUTCOME_UNKNOWN`.
- Create `src/main/java/com/airdropmc/economy/PayerIdentity.java`: UUID plus last-known player name.
- Create `src/main/java/com/airdropmc/economy/ProviderIdentity.java`: durable provider kind, key, registrar, and currency identifier.
- Create `src/main/java/com/airdropmc/economy/EconomyAccount.java`: runtime-only opaque account handle that lets the coordinator timeout account lookup before balance or mutation starts.
- Create `src/main/java/com/airdropmc/economy/BalanceResult.java`: exact decimal balance result.
- Modify `src/main/java/com/airdropmc/economy/EconomyResult.java`: replace the boolean result with `EconomyOutcome`.
- Modify `src/main/java/com/airdropmc/economy/EconomyProvider.java`: expose identity-based `CompletionStage` operations.
- Modify `src/main/java/com/airdropmc/economy/TreasuryEconomyProvider.java`: compose raw Treasury futures without blocking.
- Modify `src/main/java/com/airdropmc/economy/VaultEconomyProvider.java`: resolve `OfflinePlayer` and return completed stages on the server thread.

### Durable paid-drop core

- Create `src/main/java/com/airdropmc/paid/PaidDropRequest.java`: Bukkit-free preflight values passed to the coordinator.
- Create `src/main/java/com/airdropmc/paid/DropResources.java`: immutable entity/block identifiers stored in state and returned by Paper-side operations.
- Create `src/main/java/com/airdropmc/paid/PaidDropTransaction.java`: state enums, immutable snapshot, legal transitions, and settlement predicates.
- Create `src/main/java/com/airdropmc/paid/PaidDropJournal.java`: load, retention, YAML serialization, and atomic replacement.
- Create `src/main/java/com/airdropmc/paid/CoordinatorLoop.java`: serialized execution and independently cancellable wall-clock timers.
- Create `src/main/java/com/airdropmc/paid/SingleThreadCoordinatorLoop.java`: one daemon `ScheduledThreadPoolExecutor` implementation.
- Create `src/main/java/com/airdropmc/paid/ServerThreadDispatcher.java`: narrow scheduling interface used by the coordinator.
- Create `src/main/java/com/airdropmc/paid/PaidDropCoordinator.java`: reservation, provider phases, timeout races, world phases, recovery, admin resolution, and shutdown.

### Paper-side delivery
- Create `src/main/java/com/airdropmc/paid/PaidDropWorldService.java`: preflight, runtime payload registry, spawn, landing, event publication, messaging, and idempotent cleanup.
- Modify `src/main/java/com/airdropmc/Crate.java`: paid transaction identity, PDC markers, rollback-safe paid landing, and resource snapshots.
- Modify `src/main/java/com/airdropmc/ParachuteSystem.java`: expose owned entity UUIDs for persistence and cleanup.
- Modify `src/main/java/com/airdropmc/helpers/CrateManager.java`: retain the optional paid transaction ID with falling and landed entries.
- Modify `src/main/java/com/airdropmc/listeners/FallingCrateListener.java`: submit paid landing intents instead of landing immediately.
- Modify `src/main/java/com/airdropmc/listeners/CrateCleanupListener.java`: turn chunk/world/entity removal into coordinator failure signals for paid crates while preserving direct cleanup for free crates.

### Commands, lifecycle, configuration, and messages

- Modify `src/main/java/com/airdropmc/commands/DropCommand.java`: run server-thread preflight and enqueue a paid request.
- Create `src/main/java/com/airdropmc/commands/TransactionCommand.java`: list and resolve unresolved transactions.
- Modify `src/main/java/com/airdropmc/commands/CmdAirdrop.java`: route `transaction` subcommands.
- Modify `src/main/java/com/airdropmc/AirdropTabCompleter.java`: admin-only transaction completions.
- Modify `src/main/java/com/airdropmc/Airdrop.java`: construct, recover, expose, and shut down the paid-drop services.
- Modify `src/main/java/com/airdropmc/controllers/DropController.java`: remove the paid path and keep only free/admin spawning.
- Modify `src/main/java/com/airdropmc/packages/Package.java`: remove synchronous affordability and charge methods.
- Delete `src/main/java/com/airdropmc/exceptions/CannotAffordException.java` and `src/main/java/com/airdropmc/exceptions/EconomyUnavailableException.java` after callers and tests migrate.
- Modify `src/main/java/com/airdropmc/config/ConfigKeys.java` and `src/main/resources/config.yml`: validated economy and delivery timeouts.
- Modify `src/main/java/com/airdropmc/lang/MessageKey.java` and `src/main/resources/lang/en.yml`: asynchronous payment, delivery, refund, and review messages.

### Tests and operational documentation

- Replace `src/test/java/com/airdropmc/controllers/DropControllerEconomyFlowTest.java` with focused paid-flow tests in `src/test/java/com/airdropmc/paid/`.
- Modify `src/test/java/com/airdropmc/economy/TreasuryEconomyProviderTest.java` and create `VaultEconomyProviderTest.java`.
- Create tests for the state machine, journal, coordinator, world service, listeners, commands, recovery, and shutdown named in the tasks below.
- Create `docs/testing/AIRDR-3-paper-integration.md`: reproducible real-Paper checks for empty columns and tick continuity.

---

### Task 1: Align the tracked acceptance criteria

**Files:**
- Reference: `docs/superpowers/specs/2026-08-23-airdr-3-paid-drop-lifecycle-design.md`
- External: Plane work item `AIRDR-3`

- [ ] **Step 1: Amend AIRDR-3 in Plane**

Add this acceptance note verbatim:

```text
Provider threading boundary: Treasury account, balance, withdrawal, and deposit futures must be composed without blocking Paper's server thread. Legacy Vault remains a supported synchronous server-thread fallback because Vault does not define a universal provider thread-safety contract. World, entity, block, inventory, event, command, and plugin lifecycle work remains on Paper's server thread.
```

- [ ] **Step 2: Confirm the issue still contains the failure and recovery cases**

Verify AIRDR-3 still explicitly covers payment timeout, insufficient funds, one active request per player, disconnect, disable plus late completion, partial spawn, falling removal/timeout, landing exception/overflow, offline refund, restart recovery, one final request outcome, and no duplicate charge/drop/refund.

No repository commit is needed for this task because the reviewed design document is already committed as `3098dfd`.

---

### Task 2: Introduce the non-blocking economy contract

**Files:**
- Create: `src/main/java/com/airdropmc/economy/ExecutionModel.java`
- Create: `src/main/java/com/airdropmc/economy/EconomyOutcome.java`
- Create: `src/main/java/com/airdropmc/economy/PayerIdentity.java`
- Create: `src/main/java/com/airdropmc/economy/ProviderIdentity.java`
- Create: `src/main/java/com/airdropmc/economy/EconomyAccount.java`
- Create: `src/main/java/com/airdropmc/economy/BalanceResult.java`
- Modify: `src/main/java/com/airdropmc/economy/EconomyResult.java`
- Modify: `src/main/java/com/airdropmc/economy/EconomyProvider.java`
- Test: `src/test/java/com/airdropmc/economy/EconomyContractTest.java`

- [ ] **Step 1: Write the failing contract test**

```java
package com.airdropmc.economy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyContractTest {

	@Test
	void payerIdentity_requiresUuidAndKeepsOfflineNameFallback() {
		UUID payerId = UUID.randomUUID();
		PayerIdentity payer = new PayerIdentity(payerId, "Luke");

		assertEquals(payerId, payer.uniqueId());
		assertEquals("Luke", payer.lastKnownName());
		assertThrows(NullPointerException.class, () -> new PayerIdentity(null, "Luke"));
	}

	@Test
	void economyResults_distinguishRejectionFromAmbiguity() {
		EconomyResult rejected = EconomyResult.rejected("Insufficient funds");
		EconomyResult unknown = EconomyResult.unknown("Provider timed out");

		assertFalse(rejected.succeeded());
		assertTrue(rejected.provesNoEffect());
		assertEquals(EconomyOutcome.OUTCOME_UNKNOWN, unknown.outcome());
	}

	@Test
	void balanceResult_preservesDecimalValue() {
		BalanceResult result = BalanceResult.available(new BigDecimal("10.125"));

		assertEquals(new BigDecimal("10.125"), result.balance());
	}
}
```

- [ ] **Step 2: Run the contract test and verify it fails**

Run: `./gradlew test --tests com.airdropmc.economy.EconomyContractTest`

Expected: compilation fails because the new economy value types do not exist.

- [ ] **Step 3: Add the value types and async interface**

Use these exact public contracts:

```java
public enum ExecutionModel { NATIVE_ASYNC, SERVER_THREAD }

public enum EconomyOutcome { SUCCESS, REJECTED_NO_EFFECT, OUTCOME_UNKNOWN }

public record PayerIdentity(UUID uniqueId, String lastKnownName) {
	public PayerIdentity {
		Objects.requireNonNull(uniqueId, "uniqueId");
		lastKnownName = lastKnownName == null ? "" : lastKnownName;
	}
}

public record ProviderIdentity(String kind, String key, String registrar, String currencyId) {
	public ProviderIdentity {
		kind = Objects.requireNonNull(kind, "kind");
		key = Objects.requireNonNull(key, "key");
		registrar = registrar == null ? "" : registrar;
		currencyId = currencyId == null ? "" : currencyId;
	}
}

public record BalanceResult(BigDecimal balance) {
	public BalanceResult {
		Objects.requireNonNull(balance, "balance");
	}

	public static BalanceResult available(BigDecimal balance) {
		return new BalanceResult(balance);
	}
}

public interface EconomyAccount {
	PayerIdentity payer();
}

public record EconomyResult(EconomyOutcome outcome, String message) {
	public EconomyResult {
		Objects.requireNonNull(outcome, "outcome");
		message = message == null ? "" : message;
	}

	public static EconomyResult success() {
		return new EconomyResult(EconomyOutcome.SUCCESS, "");
	}

	public static EconomyResult rejected(String message) {
		return new EconomyResult(EconomyOutcome.REJECTED_NO_EFFECT, message);
	}

	public static EconomyResult unknown(String message) {
		return new EconomyResult(EconomyOutcome.OUTCOME_UNKNOWN, message);
	}

	public boolean succeeded() {
		return outcome == EconomyOutcome.SUCCESS;
	}

	public boolean provesNoEffect() {
		return outcome == EconomyOutcome.REJECTED_NO_EFFECT;
	}

	@Deprecated(forRemoval = true)
	public static EconomyResult ok() {
		return success();
	}

	@Deprecated(forRemoval = true)
	public static EconomyResult fail(String message) {
		return rejected(message);
	}

	@Deprecated(forRemoval = true)
	public boolean success() {
		return succeeded();
	}
}

public interface EconomyProvider {
	default ExecutionModel executionModel() {
		return ExecutionModel.SERVER_THREAD;
	}

	default ProviderIdentity identity() {
		return new ProviderIdentity("legacy", getName(), "", "");
	}

	default CompletionStage<EconomyAccount> resolveAccount(PayerIdentity payer) {
		return CompletableFuture.failedFuture(
				new UnsupportedOperationException("Provider has not migrated to paid economy operations"));
	}

	default CompletionStage<BalanceResult> getBalance(EconomyAccount account) {
		return CompletableFuture.failedFuture(
				new UnsupportedOperationException("Provider has not migrated to paid economy operations"));
	}

	default CompletionStage<EconomyResult> withdraw(
			EconomyAccount account, BigDecimal amount, UUID transactionId) {
		return CompletableFuture.failedFuture(
				new UnsupportedOperationException("Provider has not migrated to paid economy operations"));
	}

	default CompletionStage<EconomyResult> deposit(
			EconomyAccount account, BigDecimal amount, UUID transactionId) {
		return CompletableFuture.failedFuture(
				new UnsupportedOperationException("Provider has not migrated to paid economy operations"));
	}

	@Deprecated(forRemoval = true)
	default double getBalance(Player player) {
		throw new UnsupportedOperationException("Use identity-based paid economy operations");
	}

	@Deprecated(forRemoval = true)
	default EconomyResult withdraw(Player player, double amount) {
		throw new UnsupportedOperationException("Use identity-based paid economy operations");
	}

	@Deprecated(forRemoval = true)
	default EconomyResult deposit(Player player, double amount) {
		throw new UnsupportedOperationException("Use identity-based paid economy operations");
	}

	String getName();
}
```

The default methods are a compile-safe migration bridge while Tasks 3, 4, and 12 move Treasury, Vault, and the paid caller in separate commits. Task 12 removes every deprecated compatibility method once no production caller uses it. Reject negative amounts in each adapter; accept zero as a completed `SUCCESS` without invoking the provider.

- [ ] **Step 4: Run the contract test**

Run: `./gradlew test --tests com.airdropmc.economy.EconomyContractTest`

Expected: PASS.

- [ ] **Step 5: Commit the contract**

```bash
git add src/main/java/com/airdropmc/economy src/test/java/com/airdropmc/economy/EconomyContractTest.java
git commit -m "AIRDR-3: define async economy contract"
```

---

### Task 3: Make Treasury genuinely asynchronous

**Files:**
- Modify: `src/main/java/com/airdropmc/economy/TreasuryEconomyProvider.java`
- Modify: `src/test/java/com/airdropmc/economy/TreasuryEconomyProviderTest.java`

- [ ] **Step 1: Replace blocking tests with raw-future tests**

Cover these test methods with Mockito-backed Treasury accounts:

```java
@Test
void resolveAccount_returnsIncompleteStage_withoutBlockingCaller() {
	CompletableFuture<PlayerAccount> accountFuture = new CompletableFuture<>();
	when(playerAccessor.withUniqueId(PAYER_ID)).thenReturn(playerAccessor);
	when(playerAccessor.get()).thenReturn(accountFuture);

	CompletionStage<EconomyAccount> result = provider.resolveAccount(PAYER);

	assertFalse(result.toCompletableFuture().isDone());
}

@Test
void balanceDoesNotStartUntilCoordinatorInvokesItWithResolvedAccount() {
	TreasuryEconomyProvider.TreasuryAccount accountHandle =
			new TreasuryEconomyProvider.TreasuryAccount(PAYER, account);
	when(account.retrieveBalance(currency)).thenReturn(new CompletableFuture<>());

	CompletionStage<BalanceResult> result = provider.getBalance(accountHandle);

	assertFalse(result.toCompletableFuture().isDone());
	verify(account).retrieveBalance(currency);
}

@Test
void withdraw_invokesExactlyOneMutation_andIncludesTransactionIdInReason() {
	UUID transactionId = UUID.randomUUID();
	when(account.withdrawBalance(eq(AMOUNT), any(), eq(currency), eq(EconomyTransactionImportance.NORMAL),
			eq("Airdrop transaction " + transactionId)))
			.thenReturn(CompletableFuture.completedFuture(new BigDecimal("15.00")));

	EconomyResult result = provider.withdraw(ACCOUNT_HANDLE, AMOUNT, transactionId).toCompletableFuture().join();

	assertEquals(EconomyOutcome.SUCCESS, result.outcome());
	verify(account, times(1)).withdrawBalance(eq(AMOUNT), any(), eq(currency),
			eq(EconomyTransactionImportance.NORMAL), eq("Airdrop transaction " + transactionId));
}

@Test
void exceptionalMutation_isLeftExceptional_forCoordinatorClassification() {
	CompletableFuture<BigDecimal> mutation = new CompletableFuture<>();
	mutation.completeExceptionally(new IllegalStateException("database unavailable"));
	when(account.withdrawBalance(any(), any(), any(), any(), any())).thenReturn(mutation);

	CompletionStage<EconomyResult> result = provider.withdraw(ACCOUNT_HANDLE, AMOUNT, UUID.randomUUID());

	assertTrue(result.toCompletableFuture().isCompletedExceptionally());
}
```

The test fixture must stub `treasury.getPrimaryCurrency()`, `currency.getIdentifier()`, `treasury.accountAccessor().player()`, and UUID account lookup. Keep one explicit test for account lookup failure and one for deposit success.

- [ ] **Step 2: Run the Treasury tests and verify the old implementation fails**

Run: `./gradlew test --tests com.airdropmc.economy.TreasuryEconomyProviderTest`

Expected: the new raw-future assertions fail because Treasury still runs the deprecated blocking path and has not overridden the identity-based operations.

- [ ] **Step 3: Compose Treasury stages without blocking**

Implement account lookup and operations as separate stage chains:

```java
record TreasuryAccount(PayerIdentity payer, PlayerAccount account) implements EconomyAccount {}

@Override
public CompletionStage<EconomyAccount> resolveAccount(PayerIdentity payer) {
	return treasury.accountAccessor().player().withUniqueId(payer.uniqueId()).get()
			.thenApply(account -> new TreasuryAccount(payer, account));
}

@Override
public CompletionStage<BalanceResult> getBalance(EconomyAccount account) {
	TreasuryAccount treasuryAccount = requireTreasuryAccount(account);
	return treasuryAccount.account().retrieveBalance(primaryCurrency)
			.thenApply(BalanceResult::available);
}

@Override
public CompletionStage<EconomyResult> withdraw(EconomyAccount account, BigDecimal amount, UUID transactionId) {
	validateAmount(amount);
	if (amount.signum() == 0) {
		return CompletableFuture.completedFuture(EconomyResult.success());
	}
	TreasuryAccount treasuryAccount = requireTreasuryAccount(account);
	return treasuryAccount.account().withdrawBalance(amount, CAUSE, primaryCurrency,
					EconomyTransactionImportance.NORMAL, "Airdrop transaction " + transactionId))
			.thenApply(ignored -> EconomyResult.success());
}

@Override
public CompletionStage<EconomyResult> deposit(EconomyAccount account, BigDecimal amount, UUID transactionId) {
	validateAmount(amount);
	if (amount.signum() == 0) {
		return CompletableFuture.completedFuture(EconomyResult.success());
	}
	TreasuryAccount treasuryAccount = requireTreasuryAccount(account);
	return treasuryAccount.account().depositBalance(amount, CAUSE, primaryCurrency,
					EconomyTransactionImportance.NORMAL, "Airdrop refund " + transactionId))
			.thenApply(ignored -> EconomyResult.success());
}
```

`requireTreasuryAccount` is a complete type check that throws `IllegalArgumentException("Account was not resolved by Treasury")` unless the value is a `TreasuryAccount`. Return `ExecutionModel.NATIVE_ASYNC`. Persist identity as `new ProviderIdentity("treasury", treasury.getClass().getName(), registrarName, primaryCurrency.getIdentifier())`. Keep `TreasuryAccount` package-private so tests can construct a resolved handle without starting another phase. Override the three deprecated live-`Player` operations to throw `UnsupportedOperationException` without waiting on a future. Do not catch stage exceptions inside the adapter; the coordinator needs to know an invoked mutation became ambiguous.

- [ ] **Step 4: Prove no blocking calls remain in the adapter**

Run: `rg -n "\.join\(|\.get\(|orTimeout" src/main/java/com/airdropmc/economy/TreasuryEconomyProvider.java`

Expected: no matches. The accessor method named `get()` is allowed; if the broad pattern reports `.get()`, manually confirm it is only Treasury's future-returning account accessor and narrow the check to `join|orTimeout`.

- [ ] **Step 5: Run the Treasury tests**

Run: `./gradlew test --tests com.airdropmc.economy.TreasuryEconomyProviderTest`

Expected: PASS.

- [ ] **Step 6: Commit the Treasury adapter**

```bash
git add src/main/java/com/airdropmc/economy/TreasuryEconomyProvider.java src/test/java/com/airdropmc/economy/TreasuryEconomyProviderTest.java
git commit -m "AIRDR-3: compose Treasury operations asynchronously"
```

---

### Task 4: Preserve Vault's synchronous compatibility path

**Files:**
- Modify: `src/main/java/com/airdropmc/economy/VaultEconomyProvider.java`
- Create: `src/test/java/com/airdropmc/economy/VaultEconomyProviderTest.java`

- [ ] **Step 1: Write Vault identity and thread-contract tests**

```java
@Test
void executionModel_requiresServerThread() {
	assertEquals(ExecutionModel.SERVER_THREAD, provider.executionModel());
}

@Test
void offlinePayment_usesUuidAndLastKnownName() {
	UUID payerId = UUID.randomUUID();
	OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
	when(server.getOfflinePlayer(payerId)).thenReturn(offlinePlayer);
	when(vault.withdrawPlayer(offlinePlayer, 12.5)).thenReturn(successResponse(12.5));

	EconomyAccount account = provider.resolveAccount(new PayerIdentity(payerId, "Luke"))
			.toCompletableFuture().join();
	EconomyResult result = provider.withdraw(account,
			new BigDecimal("12.5"), UUID.randomUUID()).toCompletableFuture().join();

	assertTrue(result.succeeded());
	verify(vault).withdrawPlayer(offlinePlayer, 12.5);
}

@Test
void rejectedWithdrawal_provesNoEffect() {
	when(vault.withdrawPlayer(any(OfflinePlayer.class), eq(50.0)))
			.thenReturn(failureResponse(50.0, "Insufficient funds"));

	EconomyAccount account = provider.resolveAccount(PAYER).toCompletableFuture().join();
	EconomyResult result = provider.withdraw(account, new BigDecimal("50.0"), UUID.randomUUID())
			.toCompletableFuture().join();

	assertEquals(EconomyOutcome.REJECTED_NO_EFFECT, result.outcome());
}
```

Construct `VaultEconomyProvider` with both `Server` and `Economy`, so it can resolve the recorded UUID after the player disconnects. Add one test that `getOfflinePlayer(UUID)` returning an object without a name still invokes the `OfflinePlayer` overload; the recorded last-known name remains journal data for providers whose `AbstractEconomy` delegates by name.

- [ ] **Step 2: Run the Vault tests and verify they fail**

Run: `./gradlew test --tests com.airdropmc.economy.VaultEconomyProviderTest`

Expected: compilation fails because the adapter still accepts live `Player` values and returns synchronous results.

- [ ] **Step 3: Implement the server-thread adapter**

Use this operation shape for balance, withdrawal, and deposit:

```java
private record VaultAccount(PayerIdentity payer, OfflinePlayer offlinePlayer) implements EconomyAccount {}

@Override
public CompletionStage<EconomyAccount> resolveAccount(PayerIdentity payer) {
	return CompletableFuture.completedFuture(new VaultAccount(payer, server.getOfflinePlayer(payer.uniqueId())));
}

@Override
public CompletionStage<BalanceResult> getBalance(EconomyAccount account) {
	VaultAccount vaultAccount = requireVaultAccount(account);
	return CompletableFuture.completedFuture(
			BalanceResult.available(BigDecimal.valueOf(vault.getBalance(vaultAccount.offlinePlayer()))));
}

@Override
public CompletionStage<EconomyResult> withdraw(EconomyAccount account, BigDecimal amount, UUID transactionId) {
	validateAmount(amount);
	if (amount.signum() == 0) {
		return CompletableFuture.completedFuture(EconomyResult.success());
	}
	VaultAccount vaultAccount = requireVaultAccount(account);
	EconomyResponse response = vault.withdrawPlayer(vaultAccount.offlinePlayer(), amount.doubleValue());
	EconomyResult result = response.transactionSuccess()
			? EconomyResult.success()
			: EconomyResult.rejected(response.errorMessage);
	return CompletableFuture.completedFuture(result);
}

@Override
public CompletionStage<EconomyResult> deposit(EconomyAccount account, BigDecimal amount, UUID transactionId) {
	validateAmount(amount);
	if (amount.signum() == 0) {
		return CompletableFuture.completedFuture(EconomyResult.success());
	}
	VaultAccount vaultAccount = requireVaultAccount(account);
	EconomyResponse response = vault.depositPlayer(vaultAccount.offlinePlayer(), amount.doubleValue());
	EconomyResult result = response.transactionSuccess()
			? EconomyResult.success()
			: EconomyResult.rejected(response.errorMessage);
	return CompletableFuture.completedFuture(result);
}
```

`requireVaultAccount` throws `IllegalArgumentException("Account was not resolved by Vault")` for another provider's handle. Return `ExecutionModel.SERVER_THREAD` and identity `new ProviderIdentity("vault", vault.getClass().getName(), vault.getName(), "")`. Keep the deprecated live-`Player` overloads through Task 12 so existing synchronous callers compile during migration; do not create a background executor in this adapter.

- [ ] **Step 4: Run the Vault tests**

Run: `./gradlew test --tests com.airdropmc.economy.VaultEconomyProviderTest`

Expected: PASS.

- [ ] **Step 5: Commit the Vault adapter**

```bash
git add src/main/java/com/airdropmc/economy/VaultEconomyProvider.java src/test/java/com/airdropmc/economy/VaultEconomyProviderTest.java
git commit -m "AIRDR-3: keep Vault on the server thread"
```

---

### Task 5: Implement the paid transaction state machine

**Files:**
- Create: `src/main/java/com/airdropmc/paid/PaidDropRequest.java`
- Create: `src/main/java/com/airdropmc/paid/DropResources.java`
- Create: `src/main/java/com/airdropmc/paid/PaidDropTransaction.java`
- Test: `src/test/java/com/airdropmc/paid/PaidDropTransactionTest.java`

- [ ] **Step 1: Write exhaustive transition tests**

Test these exact cases:

```java
@Test
void successfulDelivery_releasesOnlyAfterDeliveredAndCharged() {
	PaidDropTransaction transaction = transaction();
	transaction.beginBalance();
	transaction.beginWithdrawal();
	transaction.confirmCharge();
	transaction.markSpawnPending();
	transaction.markDropActive(resources());
	transaction.beginLanding(block());
	transaction.markDelivered();

	assertEquals(RequestState.DELIVERED, transaction.snapshot().requestState());
	assertEquals(SettlementState.CHARGED, transaction.snapshot().settlementState());
	assertTrue(transaction.isPlayerLockReleasable());
}

@Test
void failedDelivery_requiresCleanupBeforeRefund() {
	PaidDropTransaction transaction = chargedDrop();
	transaction.beginCleanup("landing failed", resources());

	assertThrows(IllegalStateException.class, transaction::beginRefund);
	transaction.markCleanupComplete();
	transaction.beginRefund();
	transaction.confirmRefund();
	assertEquals(SettlementState.REFUNDED, transaction.snapshot().settlementState());
}

@Test
void finalRequestOutcome_cannotChange() {
	PaidDropTransaction transaction = transaction();
	transaction.failUncharged("insufficient funds");

	assertThrows(IllegalStateException.class, transaction::markSpawnPending);
}

@Test
void ambiguousSettlement_keepsPlayerLocked() {
	PaidDropTransaction transaction = transaction();
	transaction.beginBalance();
	transaction.beginWithdrawal();
	transaction.markWithdrawalUnknown("timed out");

	assertFalse(transaction.isPlayerLockReleasable());
	assertEquals(SettlementState.OUTCOME_UNKNOWN, transaction.snapshot().settlementState());
}
```

Also parameterize every illegal edge from the approved request and settlement diagrams. Assert duplicate signals return `false` from `tryTimeout`, `tryBeginCleanup`, `tryBeginLanding`, and `tryAcceptCompletion`, leaving the snapshot unchanged.

- [ ] **Step 2: Run the state tests and verify they fail**

Run: `./gradlew test --tests com.airdropmc.paid.PaidDropTransactionTest`

Expected: compilation fails because the paid state model does not exist.

- [ ] **Step 3: Add the Bukkit-free request and snapshot contracts**

`PaidDropRequest` must contain only these values:

```java
public record PaidDropRequest(
		UUID transactionId,
		PayerIdentity payer,
		String packageName,
		String amount,
		ProviderIdentity provider,
		UUID worldId,
		int columnX,
		int surfaceY,
		int columnZ,
		int spawnY) {

	public BigDecimal decimalAmount() {
		return new BigDecimal(amount);
	}
}
```

Define the state enums inside `PaidDropTransaction`:

```java
public enum RequestState { PREPARING, PAYMENT_PENDING, SPAWN_PENDING, DROP_ACTIVE, FAILED, DELIVERED, REVIEW_REQUIRED }
public enum SettlementState { NOT_STARTED, BALANCE_PENDING, WITHDRAW_IN_FLIGHT, UNCHARGED, CHARGED, CLEANUP_PENDING, REFUND_PENDING, REFUND_IN_FLIGHT, REFUNDED, OUTCOME_UNKNOWN }
public enum DeliveryPhase { NOT_STARTED, FALLING, LANDING_IN_PROGRESS }
public enum AdminResolution { DELIVERED, REFUNDED, UNCHARGED }
public record BlockPosition(UUID worldId, int x, int y, int z) {}

public record DropResources(UUID transactionId, List<UUID> entityIds, BlockPosition barrelBlock) {
	public DropResources {
		Objects.requireNonNull(transactionId, "transactionId");
		entityIds = List.copyOf(entityIds);
	}

	public static DropResources empty(UUID transactionId) {
		return new DropResources(transactionId, List.of(), null);
	}
}
```

The immutable `Snapshot` must include transaction ID, payer identity, package, original decimal amount string, provider identity, world/column/spawn coordinates, both state dimensions, delivery phase, entity UUIDs, landing block, created/updated timestamps, and last failure text. Give `Snapshot` the same `playerLockReleasable()` predicate as the mutable transaction so journal retention and admin listing do not recreate settlement logic.

- [ ] **Step 4: Implement guarded transitions**

Keep mutation methods package-private so only coordinator/recovery code can use them. Centralize checks:

```java
private void require(RequestState request, SettlementState settlement) {
	if (requestState != request || settlementState != settlement) {
		throw new IllegalStateException("Illegal paid-drop transition from " + requestState + "/" + settlementState);
	}
}

public boolean isPlayerLockReleasable() {
	return requestState == RequestState.DELIVERED && settlementState == SettlementState.CHARGED
			|| requestState == RequestState.FAILED && settlementState == SettlementState.UNCHARGED
			|| requestState == RequestState.FAILED && settlementState == SettlementState.REFUNDED;
}
```

Make final request outcomes immutable. A late successful withdrawal may move only `OUTCOME_UNKNOWN` to `CHARGED` and then `CLEANUP_PENDING`; it may not change `FAILED` or create a spawn authorization.

- [ ] **Step 5: Run the state tests**

Run: `./gradlew test --tests com.airdropmc.paid.PaidDropTransactionTest`

Expected: PASS.

- [ ] **Step 6: Commit the state model**

```bash
git add src/main/java/com/airdropmc/paid/PaidDropRequest.java src/main/java/com/airdropmc/paid/DropResources.java src/main/java/com/airdropmc/paid/PaidDropTransaction.java src/test/java/com/airdropmc/paid/PaidDropTransactionTest.java
git commit -m "AIRDR-3: model paid drop transaction states"
```

---

### Task 6: Add the durable transaction journal

**Files:**
- Create: `src/main/java/com/airdropmc/paid/PaidDropJournal.java`
- Test: `src/test/java/com/airdropmc/paid/PaidDropJournalTest.java`

- [ ] **Step 1: Write round-trip, retention, and failed-write tests**

```java
@TempDir
Path tempDir;

@Test
void saveAndLoad_preservesProviderCurrencyAndExactAmount() {
	PaidDropJournal journal = new PaidDropJournal(tempDir.resolve("transactions.yml"), 1000);
	PaidDropTransaction.Snapshot original = snapshot("10.1250", "treasury", "vaulty", "dollars");

	assertTrue(journal.save(List.of(original)));
	PaidDropTransaction.Snapshot loaded = journal.load().getFirst();

	assertEquals("10.1250", loaded.amount());
	assertEquals("dollars", loaded.provider().currencyId());
	assertEquals(original.transactionId(), loaded.transactionId());
}

@Test
void save_keepsEveryUnresolvedAndOnlyNewestThousandTerminalRecords() {
	List<PaidDropTransaction.Snapshot> records = recordsWith(2, 1005);

	assertTrue(journal.save(records));
	List<PaidDropTransaction.Snapshot> loaded = journal.load();

	assertEquals(1002, loaded.size());
	assertEquals(2, loaded.stream().filter(snapshot -> !snapshot.playerLockReleasable()).count());
}

@Test
void failedAtomicReplacement_doesNotReplaceLastGoodJournal() throws IOException {
	PaidDropJournal journal = new PaidDropJournal(tempDir.resolve("transactions.yml"), 1000,
			(source, target) -> { throw new IOException("disk full"); });

	assertFalse(journal.save(List.of(snapshot("10", "vault", "Essentials", ""))));
	assertFalse(Files.exists(tempDir.resolve("transactions.yml")));
}
```

Use a package-private move-strategy constructor only for deterministic I/O failure tests.

- [ ] **Step 2: Run the journal tests and verify they fail**

Run: `./gradlew test --tests com.airdropmc.paid.PaidDropJournalTest`

Expected: compilation fails because `PaidDropJournal` does not exist.

- [ ] **Step 3: Implement YAML serialization and atomic replacement**

Store records under `transactions.<uuid>`. Serialize enum names, UUIDs as strings, amount unchanged as a string, timestamps with `Instant.toString()`, entity UUIDs as strings, and optional landing block as its four scalar fields. Implement save using the repository's established pattern:

```java
Path temporary = Files.createTempFile(target.getParent(), "transactions.", ".tmp");
yaml.save(temporary.toFile());
try {
	Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
} catch (AtomicMoveNotSupportedException exception) {
	Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
}
```

Sort terminal records by `updatedAt` descending, keep 1,000, and always retain unresolved records. On malformed individual records, log the transaction key and keep loading other records; on an invalid state combination, load that record as `REVIEW_REQUIRED`/`OUTCOME_UNKNOWN` so it remains visible and locked.

- [ ] **Step 4: Run the journal tests**

Run: `./gradlew test --tests com.airdropmc.paid.PaidDropJournalTest`

Expected: PASS.

- [ ] **Step 5: Commit the journal**

```bash
git add src/main/java/com/airdropmc/paid/PaidDropJournal.java src/test/java/com/airdropmc/paid/PaidDropJournalTest.java
git commit -m "AIRDR-3: persist paid drop transactions"
```

---

### Task 7: Build the deterministic coordinator event loop

**Files:**
- Create: `src/main/java/com/airdropmc/paid/CoordinatorLoop.java`
- Create: `src/main/java/com/airdropmc/paid/SingleThreadCoordinatorLoop.java`
- Create: `src/main/java/com/airdropmc/paid/ServerThreadDispatcher.java`
- Test: `src/test/java/com/airdropmc/paid/SingleThreadCoordinatorLoopTest.java`

- [ ] **Step 1: Write serialization and timer-cancellation tests**

```java
@Test
void execute_serializesEventsOnOneOwnedThread() throws Exception {
	try (SingleThreadCoordinatorLoop loop = new SingleThreadCoordinatorLoop("airdrop-paid-test")) {
		Set<String> threads = ConcurrentHashMap.newKeySet();
		CountDownLatch completed = new CountDownLatch(20);
		for (int index = 0; index < 20; index++) {
			loop.execute(() -> {
				threads.add(Thread.currentThread().getName());
				completed.countDown();
			});
		}
		assertTrue(completed.await(2, TimeUnit.SECONDS));
		assertEquals(Set.of("airdrop-paid-test"), threads);
	}
}

@Test
void cancelledTimer_neverRuns() throws Exception {
	try (SingleThreadCoordinatorLoop loop = new SingleThreadCoordinatorLoop("airdrop-paid-test")) {
		AtomicBoolean ran = new AtomicBoolean();
		CoordinatorLoop.Cancellable timer = loop.schedule(Duration.ofMillis(20), () -> ran.set(true));
		timer.cancel();
		assertTrue(loop.barrier(Duration.ofSeconds(1)));
		assertFalse(ran.get());
	}
}
```

- [ ] **Step 2: Run the event-loop tests and verify they fail**

Run: `./gradlew test --tests com.airdropmc.paid.SingleThreadCoordinatorLoopTest`

Expected: compilation fails because the event-loop contracts do not exist.

- [ ] **Step 3: Implement the narrow scheduling contracts**

```java
public interface CoordinatorLoop extends AutoCloseable {
	void execute(Runnable event);
	Cancellable schedule(Duration delay, Runnable event);
	boolean barrier(Duration timeout) throws InterruptedException;

	interface Cancellable {
		void cancel();
	}
}

public interface ServerThreadDispatcher {
	void dispatch(Runnable task);
	boolean isServerThread();
}
```

Back `SingleThreadCoordinatorLoop` with a `ScheduledThreadPoolExecutor(1, threadFactory)`, enable `setRemoveOnCancelPolicy(true)`, wrap scheduled callbacks so they re-enter the same serialized queue, and implement bounded `barrier` with a submitted latch. `close()` must reject new events, cancel timers, call `shutdown()`, and use `shutdownNow()` only after the bounded wait expires.

- [ ] **Step 4: Run the event-loop tests**

Run: `./gradlew test --tests com.airdropmc.paid.SingleThreadCoordinatorLoopTest`

Expected: PASS.

- [ ] **Step 5: Commit the event loop**

```bash
git add src/main/java/com/airdropmc/paid/CoordinatorLoop.java src/main/java/com/airdropmc/paid/SingleThreadCoordinatorLoop.java src/main/java/com/airdropmc/paid/ServerThreadDispatcher.java src/test/java/com/airdropmc/paid/SingleThreadCoordinatorLoopTest.java
git commit -m "AIRDR-3: serialize paid drop coordination"
```

---

### Task 8: Implement server-thread preflight and tracked drop resources

**Files:**
- Create: `src/main/java/com/airdropmc/paid/PaidDropWorldService.java`
- Modify: `src/main/java/com/airdropmc/Crate.java`
- Modify: `src/main/java/com/airdropmc/ParachuteSystem.java`
- Modify: `src/main/java/com/airdropmc/helpers/CrateManager.java`
- Test: `src/test/java/com/airdropmc/paid/PaidDropWorldServiceTest.java`
- Modify: `src/test/java/com/airdropmc/CrateDestroyTest.java`
- Modify: `src/test/java/com/airdropmc/helpers/CrateManagerTest.java`

- [ ] **Step 1: Write preflight and partial-spawn tests**

Add MockBukkit tests with these assertions:

```java
@Test
void preflight_rejectsCoveredPlayerBeforePayloadRegistration() {
	world.getBlockAt(0, 80, 0).setType(Material.STONE);
	player.teleport(new Location(world, 0, 70, 0));

	assertThrows(SkyNotClearException.class,
			() -> service.preflight(UUID.randomUUID(), player, packageWithItems(1), DropOptions.createDefault()));
	assertEquals(0, service.runtimePayloadCount());
}

@Test
void preflight_rejectsContentsThatCannotFitEmptyBarrel() {
	Package oversized = packageWithItems(28);

	assertThrows(IllegalArgumentException.class,
			() -> service.preflight(UUID.randomUUID(), player, oversized, DropOptions.createDefault()));
}

@Test
void spawnFailure_reportsEveryEntityCreatedBeforeException() {
	UUID transactionId = UUID.randomUUID();
	DropResources partial = new DropResources(transactionId, List.of(UUID.randomUUID()), null);
	Crate partialCrate = mock(Crate.class);
	doThrow(new IllegalStateException("parachute spawn failed")).when(partialCrate).dropCrate();
	when(partialCrate.snapshotResources()).thenReturn(partial);
	service = serviceWithCrateFactory((location, targetWorld, items, dropOptions, paidId) -> partialCrate);
	PaidDropRequest request = service.preflight(transactionId, player, packageWithItems(1), options());

	PaidDropWorldService.SpawnResult result = service.spawn(request);

	assertFalse(result.success());
	assertFalse(result.resources().entityIds().isEmpty());
	assertEquals(transactionId, result.resources().transactionId());
}
```

Also test null world lookup, plugin disabled, world changed between payment and spawn, unsafe landing block, and exact cloning of package contents.

- [ ] **Step 2: Run the world-service tests and verify they fail**

Run: `./gradlew test --tests com.airdropmc.paid.PaidDropWorldServiceTest`

Expected: compilation fails because paid world tracking does not exist.

- [ ] **Step 3: Add immutable Paper result contracts**

Use UUIDs and coordinates only in results returned to the coordinator:

```java
public record SpawnResult(boolean success, DropResources resources, String failure) {}
public record LandingResult(boolean success, DropResources resources, String failure) {}
public record CleanupResult(boolean complete, DropResources remaining, String failure) {}
```

Keep the result records nested in `PaidDropWorldService`; use the existing top-level `DropResources` from Task 5 because the journal and state model also use it.

- [ ] **Step 4: Make `Crate` expose complete paid ownership**

Add an optional transaction UUID constructor used only by paid drops. Mark the falling barrel, slime, chickens, and landed barrel with a plugin `NamespacedKey` named `transaction-id` using `PersistentDataType.STRING`. `ParachuteSystem.ownedEntityIds()` returns the slime and every spawned chicken UUID, including entities created before a later initialization failure. `Crate.snapshotResources()` returns the falling entity, parachute entities, and landed block when present.

Add a rollback-safe paid landing method with this contract:

```java
public void landPaid(Block block, NamespacedKey transactionKey) {
	if (!block.isReplaceable()) {
		throw new IllegalStateException("Landing block is no longer replaceable");
	}
	try {
		block.setType(Material.BARREL, false);
		if (!(block.getState() instanceof Barrel barrel)) {
			throw new IllegalStateException("Failed to create barrel at landed location");
		}
		barrel.getPersistentDataContainer().set(transactionKey, PersistentDataType.STRING,
				transactionId.toString());
		for (ItemStack item : contents) {
			if (!barrel.getInventory().addItem(item.clone()).isEmpty()) {
				throw new IllegalStateException("Package contents did not fit in barrel");
			}
		}
		barrel.update(true, false);
		completeLanding(block, barrel);
	} catch (RuntimeException exception) {
		BlockState state = block.getState();
		if (state instanceof Barrel barrel) {
			barrel.getInventory().clear();
		}
		block.setType(Material.AIR, false);
		throw exception;
	}
}
```

Extract the existing effects/manager logic into `completeLanding` so free `land` retains its existing overflow behavior. Do not drop overflow items from `landPaid`.

- [ ] **Step 5: Implement `PaidDropWorldService` with a server-thread-only runtime map**

Guard every public world method with:

```java
private void requireServerThread() {
	if (!Bukkit.isPrimaryThread()) {
		throw new IllegalStateException("Paid drop world access must run on Paper's server thread");
	}
}
```

`preflight` generates no side effects beyond registering a cloned runtime payload. It checks permission, provider availability, barrel capacity of 27 stacks, `World#getHighestBlockAt`, cover, world min/max height, spawn height, and `Block#isReplaceable` at the intended landing block. Store `RuntimePayload` by transaction ID; it contains cloned items, fully resolved primitive `DropOptions` values, and no `Player` reference. `discard(transactionId)` removes rejected preflights.

Convert the validated package price exactly once with `BigDecimal.valueOf(pkg.getPrice()).toPlainString()` and place that string in `PaidDropRequest`. Define a nested `ResolvedDropOptions` record containing chicken count, falling speed, drop height, landing/continuous/flare flags, smoke flag, and smoke height; build it on the server thread from the supplied `DropOptions`, and reconstruct a concrete `DropOptions` only inside server-thread spawn.

`spawn` looks up the current world by UUID, revalidates the column, stores the `Crate` before calling `dropCrate`, and returns `crate.snapshotResources()` from both success and catch paths. Give `PaidDropWorldService` a package-private constructor accepting a nested `CrateFactory` with `Crate create(Location, World, List<ItemStack>, DropOptions, UUID)`; the public constructor supplies `(location, world, items, options, id) -> new Crate(location, world, items, options, id)`. `cleanup` removes manager entries, cancels crate tasks/effects, removes entities by stored UUID if loaded, clears only a barrel whose PDC transaction ID matches, and reports incomplete cleanup when the world or required chunk cannot be inspected.

Until Task 13 replaces raw status text with localized `MessageKey` values, expose this server-thread method so coordinator commits compile independently:

```java
public void notifyPlayer(UUID payerId, String message) {
	requireServerThread();
	Player player = Bukkit.getPlayer(payerId);
	if (player != null && player.isOnline()) {
		ChatHandler.sendMessage(player, message);
	}
}
```

Use the exact English defaults listed in Task 13 at call sites, then replace those string arguments with message keys and placeholder maps in Task 13.

- [ ] **Step 6: Change `CrateManager` entries to retain paid identity**

Use this record for both falling and landed maps:

```java
public record ManagedCrate(Crate crate, UUID paidTransactionId) {
	public boolean isPaid() {
		return paidTransactionId != null;
	}
}
```

Keep existing `addCrate(FallingBlock, Crate)` and `addCrate(Location, Crate)` overloads delegating with a null transaction ID so free/admin callers remain source-compatible. Add lookup/removal methods returning `ManagedCrate`, plus snapshot methods that list paid IDs in a chunk or world without destroying them.

- [ ] **Step 7: Run world and existing crate tests**

Run: `./gradlew test --tests com.airdropmc.paid.PaidDropWorldServiceTest --tests com.airdropmc.CrateDestroyTest --tests com.airdropmc.helpers.CrateManagerTest --tests com.airdropmc.ParachuteSystemTest`

Expected: PASS.

- [ ] **Step 8: Commit Paper-side resource tracking**

```bash
git add src/main/java/com/airdropmc/paid/DropResources.java src/main/java/com/airdropmc/paid/PaidDropWorldService.java src/main/java/com/airdropmc/Crate.java src/main/java/com/airdropmc/ParachuteSystem.java src/main/java/com/airdropmc/helpers/CrateManager.java src/test/java/com/airdropmc/paid/PaidDropWorldServiceTest.java src/test/java/com/airdropmc/CrateDestroyTest.java src/test/java/com/airdropmc/helpers/CrateManagerTest.java
git commit -m "AIRDR-3: track paid drop world resources"
```

---

### Task 9: Coordinate payment without blocking or duplicate requests

**Files:**
- Create: `src/main/java/com/airdropmc/paid/PaidDropCoordinator.java`
- Test: `src/test/java/com/airdropmc/paid/PaidDropCoordinatorPaymentTest.java`

- [ ] **Step 1: Create a deterministic coordinator fixture**

In the test class, implement a `ManualCoordinatorLoop` that queues `Runnable` events and timers, with `runNext()`, `runUntilIdle()`, and `fireTimer(int index)`. Implement a `RecordingServerDispatcher` that queues server tasks separately. This lets each test choose whether a provider completion, timeout, shutdown, or server callback wins without sleeping.

The fixture must expose:

```java
private final ManualCoordinatorLoop loop = new ManualCoordinatorLoop();
private final RecordingServerDispatcher server = new RecordingServerDispatcher();
private final RecordingJournal journal = new RecordingJournal();
private final ControllableEconomyProvider economy = new ControllableEconomyProvider();
private final RecordingWorldService world = new RecordingWorldService();
private final PaidDropCoordinator coordinator = new PaidDropCoordinator(
		loop, server, journal, () -> Optional.of(economy), world,
		Duration.ofSeconds(5), Duration.ofSeconds(120));
```

- [ ] **Step 2: Write payment flow and duplicate-request tests**

```java
@Test
void incompleteTreasuryFuture_doesNotQueueWorldWorkOrBlockEventLoop() {
	coordinator.request(request());
	loop.runUntilIdle();

	assertEquals(SettlementState.BALANCE_PENDING, journal.last().settlementState());
	assertTrue(server.tasks().isEmpty());
	assertTrue(loop.acceptedSentinelEvent());
}

@Test
void insufficientBalance_failsUnchargedWithoutWithdrawalOrSpawn() {
	coordinator.request(request());
	loop.runUntilIdle();
	economy.completeBalance(new BigDecimal("4.99"));
	loop.runUntilIdle();

	assertEquals(RequestState.FAILED, journal.last().requestState());
	assertEquals(SettlementState.UNCHARGED, journal.last().settlementState());
	assertEquals(0, economy.withdrawCalls());
	assertEquals(0, world.spawnCalls());
}

@Test
void secondRequestFromSamePlayer_isRejectedBeforeSecondCharge() {
	coordinator.request(request(TRANSACTION_ONE, PAYER_ID));
	coordinator.request(request(TRANSACTION_TWO, PAYER_ID));
	loop.runUntilIdle();

	assertEquals(1, economy.balanceCalls());
	assertEquals(List.of(TRANSACTION_TWO), world.discardedPayloads());
	assertEquals(1, world.activeRequestMessages());
}

@Test
void confirmedWithdrawal_persistsBeforeExactlyOneServerThreadSpawn() {
	startThroughWithdrawal();
	economy.completeWithdrawal(EconomyResult.success());
	loop.runUntilIdle();

	assertEquals(SettlementState.CHARGED, journal.last().settlementState());
	assertEquals(RequestState.SPAWN_PENDING, journal.last().requestState());
	assertEquals(1, server.tasks().size());
	assertEquals(0, world.spawnCalls());
	server.runNext();
	assertEquals(1, world.spawnCalls());
}
```

Add tests for a balance lookup timeout (`FAILED/UNCHARGED`), a withdrawal timeout (`FAILED/OUTCOME_UNKNOWN`), exceptional withdrawal (`FAILED/OUTCOME_UNKNOWN`), timeout winning before raw completion, raw completion winning before timeout, late withdrawal rejection, late withdrawal success beginning one cleanup/refund path, journal failure before balance lookup, and journal failure before withdrawal.

- [ ] **Step 3: Run payment coordinator tests and verify they fail**

Run: `./gradlew test --tests com.airdropmc.paid.PaidDropCoordinatorPaymentTest`

Expected: compilation fails because the coordinator does not exist.

- [ ] **Step 4: Implement request reservation and provider dispatch**

The coordinator owns maps by transaction and payer UUID. `request` only posts an event. The event atomically rejects a duplicate payer, persists the new transaction, resolves the exact recorded provider through a `Supplier<Optional<EconomyProvider>>`, and starts account lookup only after a successful save.

Use a unique phase token and untouched raw stage for every provider phase:

```java
private <T> void observeRawPhase(UUID transactionId, Phase phase, CompletionStage<T> raw,
		Duration timeout, BiConsumer<T, Throwable> completion) {
	UUID token = UUID.randomUUID();
	activePhases.put(transactionId, new ActivePhase(phase, token));
	raw.whenComplete((value, error) -> loop.execute(() -> {
		if (matchesPhase(transactionId, phase, token)) {
			completion.accept(value, error);
		} else {
			handleLateEvidence(transactionId, phase, value, error);
		}
	}));
	CoordinatorLoop.Cancellable timer = loop.schedule(timeout,
			() -> onPhaseTimeout(transactionId, phase, token));
	phaseTimers.put(transactionId, timer);
}
```

When a legal raw completion wins, remove the matching `ActivePhase` and cancel its timer before persisting or starting another phase. When a timeout wins, remove the phase and timer before changing transaction state. This makes later timer/completion events observable only through the explicit late-evidence branch.

For `NATIVE_ASYNC`, invoke the adapter from the coordinator loop. For `SERVER_THREAD`, dispatch a server task that invokes the adapter and posts the returned completion back to the coordinator loop. Never access a `Player` object; provider calls use the durable `PayerIdentity`.

Run account lookup, balance retrieval, and withdrawal as distinct phases. Persist `BALANCE_PENDING` before account lookup; accept its account handle before invoking balance; persist `WITHDRAW_IN_FLIGHT` immediately before withdrawal. If account lookup or balance times out, do not invoke the next phase even when its raw future later completes. A balance below the amount and a provider `REJECTED_NO_EFFECT` both become `FAILED/UNCHARGED`. A withdrawal exception or timeout becomes `FAILED/OUTCOME_UNKNOWN`.

- [ ] **Step 5: Implement late evidence rules**

A late raw completion is accepted only if it provides new evidence for the same transaction and phase:

```java
if (phase == Phase.WITHDRAW && transaction.isFailedOutcomeUnknown()
		&& error == null && result.outcome() == EconomyOutcome.SUCCESS) {
	transaction.confirmLateCharge();
	transaction.beginCleanup("Withdrawal completed after request timeout", DropResources.empty(transactionId));
	persistThenScheduleCleanup(transaction);
}
```

A late failure does not prove no effect after mutation invocation. It remains `OUTCOME_UNKNOWN`. A late success never calls spawn because the request outcome is already `FAILED`.

- [ ] **Step 6: Run payment coordinator tests**

Run: `./gradlew test --tests com.airdropmc.paid.PaidDropCoordinatorPaymentTest`

Expected: PASS.

- [ ] **Step 7: Commit payment coordination**

```bash
git add src/main/java/com/airdropmc/paid/PaidDropCoordinator.java src/test/java/com/airdropmc/paid/PaidDropCoordinatorPaymentTest.java
git commit -m "AIRDR-3: coordinate nonblocking paid charges"
```

---

### Task 10: Coordinate spawn, landing, cleanup, and refund

**Files:**
- Modify: `src/main/java/com/airdropmc/paid/PaidDropCoordinator.java`
- Modify: `src/main/java/com/airdropmc/paid/PaidDropWorldService.java`
- Modify: `src/main/java/com/airdropmc/listeners/FallingCrateListener.java`
- Modify: `src/main/java/com/airdropmc/listeners/CrateCleanupListener.java`
- Test: `src/test/java/com/airdropmc/paid/PaidDropCoordinatorDeliveryTest.java`
- Modify: `src/test/java/com/airdropmc/listeners/FallingCrateListenerTest.java`
- Modify: `src/test/java/com/airdropmc/listeners/CrateCleanupListenerTest.java`

- [ ] **Step 1: Write happy-path and failure-order tests**

```java
@Test
void successfulLanding_isPersistedBeforeEventAndReleasesPlayerLock() {
	startChargedDrop();
	completeSpawn();
	coordinator.landingIntent(TRANSACTION_ID, LANDING_BLOCK);
	loop.runUntilIdle();
	server.runNext();
	world.completeLandingSuccess();
	loop.runUntilIdle();

	assertEquals(RequestState.DELIVERED, journal.last().requestState());
	assertEquals(List.of("persist:DELIVERED", "event:land"), orderedEffects.tail(2));
	assertFalse(coordinator.isPlayerReserved(PAYER_ID));
}

@Test
void landingFailure_cleansBeforeRefundInvocation() {
	startActiveDrop();
	world.completeLandingFailure("barrel overflow", partialResources());
	loop.runUntilIdle();

	assertEquals(SettlementState.CLEANUP_PENDING, journal.last().settlementState());
	assertEquals(0, economy.depositCalls());
	server.runNext();
	world.completeCleanupSuccess();
	loop.runUntilIdle();
	assertEquals(SettlementState.REFUND_IN_FLIGHT, journal.last().settlementState());
	assertEquals(1, economy.depositCalls());
}

@Test
void duplicateFailureSignals_startOneCleanupAndOneRefund() {
	startActiveDrop();
	coordinator.deliveryFailed(TRANSACTION_ID, "timeout");
	coordinator.deliveryFailed(TRANSACTION_ID, "entity removed");
	coordinator.landingIntent(TRANSACTION_ID, LANDING_BLOCK);
	loop.runUntilIdle();

	assertEquals(1, server.cleanupTaskCount());
	completeCleanupAndRefund();
	assertEquals(1, economy.depositCalls());
}

@Test
void disconnect_doesNotCancelConfirmedDelivery() {
	startChargedDrop();
	world.setPlayerOffline(PAYER_ID);
	completeSpawnAndLanding();

	assertEquals(RequestState.DELIVERED, journal.last().requestState());
	assertEquals(0, world.offlineMessageAttempts());
}
```

Add cases for spawn exception, null/missing world, revalidation failure, partial spawn, delivery timeout, entity removal, chunk unload, world unload, landing at non-replaceable block, inventory insertion exception, incomplete cleanup, successful refund, rejected refund with proven no effect remaining `REFUND_PENDING`, refund timeout/exception becoming `OUTCOME_UNKNOWN`, and duplicate late refund completions.

- [ ] **Step 2: Run delivery tests and verify they fail**

Run: `./gradlew test --tests com.airdropmc.paid.PaidDropCoordinatorDeliveryTest --tests com.airdropmc.listeners.FallingCrateListenerTest --tests com.airdropmc.listeners.CrateCleanupListenerTest`

Expected: failures because listeners still land/destroy paid crates immediately and coordinator delivery phases are absent.

- [ ] **Step 3: Implement spawn result handling**

After a confirmed charge, persist `SPAWN_PENDING`, dispatch one server-thread `worldService.spawn`, and post its immutable result. On success, merge resource IDs, persist `DROP_ACTIVE/FALLING`, arm the 120-second delivery timer, then dispatch `PackageDropEvent`. On failure, persist `FAILED/CLEANUP_PENDING` with partial resources before dispatching cleanup.

The delivery timer callback must check the current state and phase token before failing the drop. Cancel it after durable `DELIVERED`, `FAILED`, or `REVIEW_REQUIRED`.

- [ ] **Step 4: Route paid landing through the coordinator**

Change the listener decision to this shape:

```java
CrateManager.ManagedCrate managed = CrateManager.removeManagedCrate(fallingBlock);
if (managed == null) {
	return;
}
event.setCancelled(true);
fallingBlock.remove();
if (managed.isPaid()) {
	PaidDropCoordinator coordinator = Airdrop.getPaidDropCoordinator();
	if (coordinator != null) {
		coordinator.landingIntent(managed.paidTransactionId(),
				new PaidDropTransaction.BlockPosition(event.getBlock().getWorld().getUID(),
						event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ()));
	}
	return;
}
managed.crate().land(event.getBlock());
Bukkit.getPluginManager().callEvent(new PackageLandEvent(managed.crate(), event.getBlock().getWorld(),
		event.getBlock().getLocation(), event.getBlock()));
```

The coordinator persists the landing block and `LANDING_IN_PROGRESS` before scheduling `worldService.land`. On success it persists `DELIVERED/CHARGED`, releases the lock, then schedules `PackageLandEvent`. Listener or event-handler exceptions after durable delivery are logged and never start a refund.

- [ ] **Step 5: Route cleanup signals without pre-emptive destruction**

For chunk, world, and entity removal events, obtain paid transaction IDs from `CrateManager` first and call `coordinator.deliveryFailed(id, reason)`. Continue using direct manager destruction for free crates. Do not remove paid manager entries until the coordinator has persisted `CLEANUP_PENDING` and dispatched `worldService.cleanup`.

- [ ] **Step 6: Implement refund phases**

On complete cleanup, persist `REFUND_PENDING`, then resolve a fresh account handle using the recorded payer and exact provider/currency identity. A refund account-lookup timeout happens before a mutation and remains `REFUND_PENDING`. Before deposit invocation, verify the active adapter identity exactly matches the snapshot and persist `REFUND_IN_FLIGHT`.

Classify results as follows:

```java
switch (result.outcome()) {
	case SUCCESS -> transaction.confirmRefund();
	case REJECTED_NO_EFFECT -> transaction.returnToRefundPending(result.message());
	case OUTCOME_UNKNOWN -> transaction.markRefundUnknown(result.message());
}
```

An exception or timeout after deposit invocation becomes `OUTCOME_UNKNOWN` and is never retried. Persist every result before notifying the player. Release the player reservation only after confirmed `REFUNDED`.

- [ ] **Step 7: Run delivery and listener tests**

Run: `./gradlew test --tests com.airdropmc.paid.PaidDropCoordinatorDeliveryTest --tests com.airdropmc.listeners.FallingCrateListenerTest --tests com.airdropmc.listeners.CrateCleanupListenerTest`

Expected: PASS.

- [ ] **Step 8: Commit the delivery lifecycle**

```bash
git add src/main/java/com/airdropmc/paid/PaidDropCoordinator.java src/main/java/com/airdropmc/paid/PaidDropWorldService.java src/main/java/com/airdropmc/listeners/FallingCrateListener.java src/main/java/com/airdropmc/listeners/CrateCleanupListener.java src/test/java/com/airdropmc/paid/PaidDropCoordinatorDeliveryTest.java src/test/java/com/airdropmc/listeners/FallingCrateListenerTest.java src/test/java/com/airdropmc/listeners/CrateCleanupListenerTest.java
git commit -m "AIRDR-3: refund failed paid deliveries"
```

---

### Task 11: Implement exhaustive recovery and bounded shutdown

**Files:**
- Modify: `src/main/java/com/airdropmc/paid/PaidDropCoordinator.java`
- Test: `src/test/java/com/airdropmc/paid/PaidDropRecoveryTest.java`
- Test: `src/test/java/com/airdropmc/paid/PaidDropShutdownTest.java`

- [ ] **Step 1: Write one recovery test per durable state**

Use a parameterized test table matching the approved design:

```java
@ParameterizedTest
@MethodSource("recoveryCases")
void recovery_isExhaustive(Snapshot input, RequestState expectedRequest,
		SettlementState expectedSettlement, RecoveryAction expectedAction) {
	journal.seed(input);

	coordinator.recover();
	loop.runUntilIdle();

	assertEquals(expectedRequest, journal.last().requestState());
	assertEquals(expectedSettlement, journal.last().settlementState());
	assertEquals(expectedAction, world.lastRecoveryAction());
}
```

Define `RecoveryAction` as a test-local enum with `NONE`, `CLEANUP`, `REFUND`, and `MANUAL_REVIEW`; the recording world fixture captures which action the coordinator requested.

Cases must cover `NOT_STARTED`, `BALANCE_PENDING`, `WITHDRAW_IN_FLIGHT`, `CHARGED/SPAWN_PENDING`, `CHARGED/DROP_ACTIVE`, landing in progress, `CLEANUP_PENDING`, `REFUND_PENDING`, `REFUND_IN_FLIGHT`, `OUTCOME_UNKNOWN`, delivered/charged, failed/uncharged, and failed/refunded. Add missing provider, changed provider key, changed Treasury registrar, and changed currency ID cases; each remains locked and unresolved.

- [ ] **Step 2: Write shutdown race tests**

```java
@Test
void shutdownPreparation_neverSchedulesBukkitWhileServerThreadWaits() throws Exception {
	startActiveDrop();

	PaidDropCoordinator.ShutdownPlan plan = coordinator.prepareShutdown(Duration.ofSeconds(2));

	assertEquals(List.of(TRANSACTION_ID), plan.cleanupTransactions());
	assertTrue(server.tasks().isEmpty());
}

@Test
void shutdownCleanup_persistsRefundPending_withoutInvokingProvider() {
	PaidDropCoordinator.ShutdownPlan plan = prepareChargedShutdown();
	List<PaidDropWorldService.CleanupResult> cleanup = world.cleanupNow(plan.cleanupTransactions());

	coordinator.finishShutdown(cleanup, Duration.ofSeconds(2));

	assertEquals(SettlementState.REFUND_PENDING, journal.last().settlementState());
	assertEquals(0, economy.depositCalls());
}

@Test
void lateWithdrawalAfterShutdown_cannotScheduleWorldWork() {
	startWithdrawal();
	coordinator.prepareShutdown(Duration.ofSeconds(2));
	economy.completeWithdrawal(EconomyResult.success());
	loop.runUntilIdle();

	assertTrue(server.tasks().isEmpty());
	assertEquals(SettlementState.OUTCOME_UNKNOWN, journal.last().settlementState());
}
```

- [ ] **Step 3: Run recovery and shutdown tests and verify they fail**

Run: `./gradlew test --tests com.airdropmc.paid.PaidDropRecoveryTest --tests com.airdropmc.paid.PaidDropShutdownTest`

Expected: failures because startup mapping and the two-phase shutdown API are absent.

- [ ] **Step 4: Implement conservative recovery**

Load snapshots on the coordinator loop, rebuild payer reservations for every unresolved record, compare durable provider identity with the active adapter, and apply the exact recovery table from the design. `SPAWN_PENDING`, `DROP_ACTIVE`, and landing-in-progress become `REVIEW_REQUIRED/CHARGED`; they never auto-refund because delivery may have occurred. `CLEANUP_PENDING` dispatches cleanup. `REFUND_PENDING` resumes only with the exact provider/currency identity. `REFUND_IN_FLIGHT` becomes `FAILED/OUTCOME_UNKNOWN`.

- [ ] **Step 5: Implement two-phase bounded shutdown**

`prepareShutdown(timeout)` sets `accepting = false`, posts a preparation event, and blocks the calling server thread only for the coordinator's bounded state/journal result. That event may not call `ServerThreadDispatcher`. It returns immutable IDs requiring cleanup.

The caller runs `PaidDropWorldService.cleanupNow` directly on the current server thread. `finishShutdown(results, timeout)` posts those immutable results, persists successful cleanup as `REFUND_PENDING`, flushes, rejects future callbacks, cancels timers, and closes the event loop. It never invokes a provider. Pre-mutation requests become `FAILED/UNCHARGED`; invoked unfinished mutations become `FAILED/OUTCOME_UNKNOWN`; confirmed charged undelivered requests become `FAILED/CLEANUP_PENDING` before direct cleanup.

- [ ] **Step 6: Run recovery and shutdown tests**

Run: `./gradlew test --tests com.airdropmc.paid.PaidDropRecoveryTest --tests com.airdropmc.paid.PaidDropShutdownTest`

Expected: PASS.

- [ ] **Step 7: Commit recovery and shutdown**

```bash
git add src/main/java/com/airdropmc/paid/PaidDropCoordinator.java src/test/java/com/airdropmc/paid/PaidDropRecoveryTest.java src/test/java/com/airdropmc/paid/PaidDropShutdownTest.java
git commit -m "AIRDR-3: recover and stop paid drops safely"
```

---

### Task 12: Wire paid requests into commands and plugin lifecycle

**Files:**
- Modify: `src/main/java/com/airdropmc/Airdrop.java`
- Modify: `src/main/java/com/airdropmc/commands/DropCommand.java`
- Modify: `src/main/java/com/airdropmc/controllers/DropController.java`
- Modify: `src/main/java/com/airdropmc/packages/Package.java`
- Delete: `src/main/java/com/airdropmc/exceptions/CannotAffordException.java`
- Delete: `src/main/java/com/airdropmc/exceptions/EconomyUnavailableException.java`
- Delete: `src/test/java/com/airdropmc/controllers/DropControllerEconomyFlowTest.java`
- Create: `src/test/java/com/airdropmc/controllers/DropControllerFreeDropTest.java`
- Create: `src/test/java/com/airdropmc/commands/DropCommandPaidFlowTest.java`
- Modify: `src/test/java/com/airdropmc/commands/CmdAirdropLifecycleSafetyTest.java`

- [ ] **Step 1: Write command-return and lifecycle-order tests**

```java
@Test
void dropCommand_returnsBeforeIncompleteTreasuryPayment() {
	CompletableFuture<BalanceResult> balance = new CompletableFuture<>();
	economy.setBalanceStage(balance);

	assertTimeoutPreemptively(Duration.ofMillis(100),
			() -> DropCommand.onCommand(player, new String[]{"starter"}));
	assertFalse(balance.isDone());
	assertEquals(0, CrateManager.paidCrateCount());
}

@Test
void disable_preparesThenCleansThenClosesCoordinator() {
	plugin.onDisable();

	assertEquals(List.of("prepare", "world-cleanup", "finish", "cancel-bukkit", "clear-free-crates"),
			lifecycle.calls());
}
```

Also test package-not-found, permission rejection, economy unavailable, sky blocked, active request, disconnect, and command during shutdown. Assert all preflight Bukkit calls occur on `Bukkit.isPrimaryThread()`.

- [ ] **Step 2: Run command tests and verify they fail**

Run: `./gradlew test --tests com.airdropmc.commands.DropCommandPaidFlowTest --tests com.airdropmc.commands.CmdAirdropLifecycleSafetyTest`

Expected: failures because the command still calls synchronous `DropController.playerInitiatedDropPackage`.

- [ ] **Step 3: Route only paid player drops through the coordinator**

Use this command flow:

```java
UUID transactionId = UUID.randomUUID();
try {
	PaidDropRequest request = Airdrop.getPaidDropWorldService().preflight(
			transactionId, player, pkg, DropOptions.createDefault());
	Airdrop.getPaidDropCoordinator().request(request);
} catch (SkyNotClearException exception) {
	ChatHandler.sendError(player, MessageKey.ERROR_SKY_NOT_CLEAR);
} catch (InsufficientPermissionsException exception) {
	ChatHandler.sendError(player, MessageKey.ERROR_INSUFFICIENT_PERMISSIONS,
			Map.of("package", exception.getPackageName()));
} catch (IllegalStateException exception) {
	ChatHandler.sendError(player, MessageKey.ERROR_ECONOMY_UNAVAILABLE);
}
```

Remove `DropController.playerInitiatedDropPackage`, `attemptRefundOnDropFailure`, `Package.canAfford`, and `Package.chargeUser`. Delete the two obsolete synchronous economy exception classes and their tests. Remove the deprecated live-`Player` methods and the default migration failures from `EconomyProvider`, and remove deprecated `EconomyResult.ok`, `fail`, and `success` aliases after `rg` confirms no callers. Keep `DropController.dropPackage` and `dropPackageOnPlayer` unchanged for free/admin calls.

- [ ] **Step 4: Wire startup and shutdown in `Airdrop`**

After economy and package setup, construct the journal at `getDataFolder().toPath().resolve("transactions.yml")`, event loop, Bukkit dispatcher, world service, and coordinator; pass the coordinator a supplier of the currently registered provider; register listeners with coordinator/world-service dependencies instead of hidden construction; call `coordinator.recover()`. Use `Duration.ofSeconds(5)` and `Duration.ofSeconds(120)` at this commit boundary; Task 13 replaces those literals with validated `ConfigKeys` values.

If economy is enabled but no provider is registered, keep the plugin enabled so unresolved transactions can still be listed or manually resolved. Log economy unavailability, reject new paid preflights, and let recovery preserve provider-dependent settlements as unresolved. Do not keep the current `disablePlugin` behavior.

Change `SYSTEM_ECONOMY_MISSING` to `"No economy provider is available; paid drops are disabled until Treasury or Vault is registered"` so startup output no longer claims the plugin is being disabled.

For Vault construction use `new VaultEconomyProvider(getServer(), vault)` and log:

```text
Using legacy Vault economy provider on Paper's server thread; provider calls may affect tick time. Treasury is recommended for non-blocking paid drops.
```

In `onDisable`, call prepare, direct world cleanup, and finish before `Bukkit.getScheduler().cancelTasks(this)` and `CrateManager.clearAll()`. Null static accessors only after coordinator shutdown finishes.

- [ ] **Step 5: Run command and lifecycle tests**

Run: `./gradlew test --tests com.airdropmc.commands.DropCommandPaidFlowTest --tests com.airdropmc.commands.CmdAirdropLifecycleSafetyTest --tests com.airdropmc.controllers.DropControllerFreeDropTest`

Expected: PASS.

- [ ] **Step 6: Commit command and lifecycle wiring**

```bash
git add src/main/java/com/airdropmc/Airdrop.java src/main/java/com/airdropmc/commands/DropCommand.java src/main/java/com/airdropmc/controllers/DropController.java src/main/java/com/airdropmc/packages/Package.java src/main/java/com/airdropmc/exceptions src/test/java/com/airdropmc/commands src/test/java/com/airdropmc/controllers src/test/java/com/airdropmc/exceptions
git commit -m "AIRDR-3: route paid drops through coordinator"
```

---

### Task 13: Add configuration and localized status messages

**Files:**
- Modify: `src/main/java/com/airdropmc/config/ConfigKeys.java`
- Modify: `src/main/resources/config.yml`
- Modify: `src/main/java/com/airdropmc/lang/MessageKey.java`
- Modify: `src/main/resources/lang/en.yml`
- Test: `src/test/java/com/airdropmc/config/ConfigKeysTest.java`
- Test: `src/test/java/com/airdropmc/lang/PaidDropMessageKeyTest.java`

- [ ] **Step 1: Write config validation tests**

```java
@ParameterizedTest
@ValueSource(ints = {-1, 0})
void economyTimeout_nonPositiveValue_fallsBackToFiveSeconds(int configured) {
	config.set("economy.operation-timeout-seconds", configured);
	assertEquals(5, ConfigKeys.getEconomyOperationTimeoutSeconds());
}

@ParameterizedTest
@ValueSource(ints = {-1, 0})
void deliveryTimeout_nonPositiveValue_fallsBackToOneHundredTwentySeconds(int configured) {
	config.set("drop.delivery-timeout-seconds", configured);
	assertEquals(120, ConfigKeys.getDeliveryTimeoutSeconds());
}
```

Add tests for positive values and missing keys.

- [ ] **Step 2: Write message-key coverage test**

Assert both the enum and shipped `en.yml` contain keys for `drop.payment-pending`, `drop.active-request`, `drop.payment-timeout`, `drop.payment-unknown`, `drop.charge-rejected`, `drop.delivery-failed`, `drop.refund-pending`, `drop.refund-complete`, and `drop.review-required`.

- [ ] **Step 3: Run config/message tests and verify they fail**

Run: `./gradlew test --tests com.airdropmc.config.ConfigKeysTest --tests com.airdropmc.lang.PaidDropMessageKeyTest`

Expected: failures because the keys and defaults are absent.

- [ ] **Step 4: Add validated defaults and messages**

Add these config values:

```yaml
drop:
  delivery-timeout-seconds: 120

economy:
  enabled: true
  operation-timeout-seconds: 5
```

Use player-facing text that distinguishes known rejection from ambiguity:

```yaml
drop:
  payment-pending: "Your airdrop payment is being processed. Transaction: {transaction}"
  active-request: "You already have an unsettled paid airdrop. Transaction: {transaction}"
  payment-timeout: "Payment timed out before any charge. No crate was created."
  payment-unknown: "The payment result is uncertain. No crate was created; transaction {transaction} needs administrator review."
  charge-rejected: "Payment was declined: {reason}. No crate was created."
  delivery-failed: "Airdrop delivery failed after payment. Cleanup and refund are being processed."
  refund-pending: "Cleanup completed. Your refund is pending for transaction {transaction}."
  refund-complete: "Your airdrop payment of {amount} was refunded."
  review-required: "Transaction {transaction} requires administrator review; another paid drop cannot start yet."
```

Replace the temporary `notifyPlayer(UUID, String)` method and raw call-site strings with `notifyPlayer(UUID, MessageKey, Map<String, String>)`. The coordinator sends messages by dispatching a server-thread task and re-resolving the player UUID. It silently skips offline players.

- [ ] **Step 5: Run config/message tests**

Run: `./gradlew test --tests com.airdropmc.config.ConfigKeysTest --tests com.airdropmc.lang.PaidDropMessageKeyTest`

Expected: PASS.

- [ ] **Step 6: Commit configuration and messages**

```bash
git add src/main/java/com/airdropmc/config/ConfigKeys.java src/main/resources/config.yml src/main/java/com/airdropmc/lang/MessageKey.java src/main/resources/lang/en.yml src/test/java/com/airdropmc/config/ConfigKeysTest.java src/test/java/com/airdropmc/lang/PaidDropMessageKeyTest.java
git commit -m "AIRDR-3: configure paid drop timeouts and messages"
```

---

### Task 14: Add administrator transaction resolution

**Files:**
- Create: `src/main/java/com/airdropmc/commands/TransactionCommand.java`
- Modify: `src/main/java/com/airdropmc/commands/CmdAirdrop.java`
- Modify: `src/main/java/com/airdropmc/AirdropTabCompleter.java`
- Modify: `src/main/java/com/airdropmc/paid/PaidDropCoordinator.java`
- Modify: `src/main/java/com/airdropmc/lang/MessageKey.java`
- Modify: `src/main/resources/lang/en.yml`
- Test: `src/test/java/com/airdropmc/commands/TransactionCommandTest.java`
- Modify: `src/test/java/com/airdropmc/commands/TabCompletionPermissionsTest.java`

- [ ] **Step 1: Write permission, listing, and resolution tests**

```java
@Test
void list_requiresAdminAndShowsOnlyUnresolvedTransactions() {
	TransactionCommand.onCommand(nonAdmin, new String[]{"transaction", "list"});
	assertLastMessageContains(nonAdmin, "airdrop.admin");

	TransactionCommand.onCommand(admin, new String[]{"transaction", "list"});
	assertLastMessageContains(admin, unresolvedId.toString());
	assertLastMessageDoesNotContain(admin, deliveredId.toString());
}

@ParameterizedTest
@ValueSource(strings = {"delivered", "refunded", "uncharged"})
void resolve_recordsExternallyVerifiedOutcome_withoutEconomyMutation(String resolution) {
	TransactionCommand.onCommand(admin,
			new String[]{"transaction", "resolve", unresolvedId.toString(), resolution});
	coordinatorLoop.runUntilIdle();

	assertFalse(coordinator.isPlayerReserved(payerId));
	assertEquals(0, economy.withdrawCalls());
	assertEquals(0, economy.depositCalls());
	assertLogContains(admin.getName(), unresolvedId.toString(), resolution);
}
```

Also test malformed UUID, unknown transaction, resolution incompatible with known delivery state, use during shutdown, and non-admin tab completion hiding `transaction`.

- [ ] **Step 2: Run command tests and verify they fail**

Run: `./gradlew test --tests com.airdropmc.commands.TransactionCommandTest --tests com.airdropmc.commands.TabCompletionPermissionsTest`

Expected: failures because transaction administration is absent.

- [ ] **Step 3: Implement async-safe admin queries**

Expose coordinator methods returning completion stages of immutable values:

```java
CompletionStage<List<PaidDropTransaction.Snapshot>> listUnresolved();
CompletionStage<ResolveResult> resolve(UUID transactionId, AdminResolution resolution, String actor);
```

The command checks `PermissionsHelper.isAdmin` on the server thread, invokes the coordinator method, and dispatches completion output back to the server thread. `resolve` changes only journal state after the administrator has externally verified the result; it never calls an economy adapter or world service. Write an audit log containing timestamp, actor, transaction ID, old states, resolution, and new states.

Route syntax exactly as:

```text
/airdrop transaction list
/airdrop transaction resolve <transaction-id> <delivered|refunded|uncharged>
```

- [ ] **Step 4: Add admin-only tab completion**

Admins receive `transaction` at argument one, `list|resolve` at argument two, unresolved transaction UUIDs at argument three for `resolve`, and `delivered|refunded|uncharged` at argument four. Non-admins receive none of those values.

- [ ] **Step 5: Run transaction command tests**

Run: `./gradlew test --tests com.airdropmc.commands.TransactionCommandTest --tests com.airdropmc.commands.TabCompletionPermissionsTest`

Expected: PASS.

- [ ] **Step 6: Commit administration commands**

```bash
git add src/main/java/com/airdropmc/commands/TransactionCommand.java src/main/java/com/airdropmc/commands/CmdAirdrop.java src/main/java/com/airdropmc/AirdropTabCompleter.java src/main/java/com/airdropmc/paid/PaidDropCoordinator.java src/main/java/com/airdropmc/lang/MessageKey.java src/main/resources/lang/en.yml src/test/java/com/airdropmc/commands/TransactionCommandTest.java src/test/java/com/airdropmc/commands/TabCompletionPermissionsTest.java
git commit -m "AIRDR-3: add transaction review commands"
```

---

### Task 15: Verify acceptance behavior and document the real-Paper checks

**Files:**
- Create: `docs/testing/AIRDR-3-paper-integration.md`
- Modify: `README.md` if it currently documents economy providers or paid drops

- [ ] **Step 1: Add a static blocking-call regression check**

Run:

```bash
rg -n "\.join\(|Future<.*>.*\.get\(|orTimeout" src/main/java/com/airdropmc/economy src/main/java/com/airdropmc/paid src/main/java/com/airdropmc/commands
```

Expected: no economy or paid-flow blocking matches. Bounded coordinator shutdown waiting is allowed only in `Airdrop.onDisable` through the named shutdown API, not by directly joining a provider future.

- [ ] **Step 2: Run focused race and recovery suites repeatedly**

Run:

```bash
for run in 1 2 3 4 5; do
	./gradlew test --tests 'com.airdropmc.paid.*' --tests 'com.airdropmc.economy.*' || exit 1
done
```

Expected: all five runs pass with no timing-dependent failure.

- [ ] **Step 3: Run the full automated suite and build**

Run: `./gradlew clean test build`

Expected: `BUILD SUCCESSFUL` and all JUnit tests pass.

- [ ] **Step 4: Write the Paper integration checklist**

Document exact setup and observations:

```markdown
# AIRDR-3 Paper Integration

1. Run `./gradlew runServer` with Java 21 and accept the local test server EULA.
2. Install a Treasury test provider whose account/balance future can be held incomplete and released manually.
3. Start a paid `/airdrop starter`; while the future is held, verify a repeating one-tick heartbeat logs continuously for at least 10 seconds and TPS does not freeze.
4. Let the five-second operation timeout win; verify no falling crate or barrel exists and the player receives the no-crate timeout message.
5. Complete the untouched withdrawal future successfully after timeout; verify no crate spawns, one cleanup/refund path starts, and `transactions.yml` records the late evidence.
6. Test columns over normal terrain, an untouched empty chunk, water, leaves, minimum height, and maximum height. Record the observed `getHighestBlockAt` landing Y and verify Airdrop either selects a replaceable landing block or rejects before charging.
7. Complete a successful charge and disconnect the player; verify exactly one drop lands with every item.
8. Remove the falling entity, unload its chunk, unload its world, and force a landing insertion exception in separate transactions; verify cleanup precedes one refund.
9. Stop the server during `SPAWN_PENDING`, `DROP_ACTIVE`, landing, and `REFUND_IN_FLIGHT`; restart and verify each state follows the recovery table without an automatic ambiguous refund.
10. Run `/airdrop transaction list` and resolve a test transaction only after externally verifying its economy/delivery result.
```

- [ ] **Step 5: Execute the real-Paper checklist**

Run: `./gradlew runServer`

Expected: every checklist item is recorded as PASS with server version, economy provider/version, observed landing coordinates, tick evidence, and transaction UUIDs. Any failure returns to the task that owns that behavior; do not weaken the assertion.

- [ ] **Step 6: Update provider documentation**

If README economy documentation exists, state that Treasury paid operations are non-blocking, Vault is a synchronous compatibility fallback, unresolved transactions are stored in `plugins/Airdrop/transactions.yml`, and administrators can inspect them with `/airdrop transaction list`.

- [ ] **Step 7: Commit integration evidence and documentation**

```bash
git add -f docs/testing/AIRDR-3-paper-integration.md
git add README.md
git commit -m "AIRDR-3: document paid drop verification"
```

---

## Final verification checklist

- [ ] `git diff --check` reports no whitespace errors.
- [ ] `./gradlew clean test build` reports `BUILD SUCCESSFUL`.
- [ ] `rg -n "\.join\(|orTimeout" src/main/java/com/airdropmc/economy src/main/java/com/airdropmc/paid` finds no provider-future blocking.
- [ ] Treasury incomplete futures do not stop the Paper heartbeat.
- [ ] Legacy Vault calls are asserted and logged as server-thread operations.
- [ ] Insufficient funds creates no crate and stores `FAILED/UNCHARGED`.
- [ ] Ambiguous withdrawal creates no crate and stores `FAILED/OUTCOME_UNKNOWN`.
- [ ] A confirmed charged delivery survives disconnect and lands exactly once.
- [ ] Every failed charged delivery completes cleanup before refund invocation.
- [ ] Duplicate timeout, cleanup, landing, and future callbacks create no duplicate charge, crate, or refund.
- [ ] Shutdown performs no provider mutation and schedules no Bukkit work while waiting.
- [ ] Every durable recovery state has an asserted action.
- [ ] Missing or changed provider/currency remains unresolved.
- [ ] Admin resolution records an externally verified result without moving money.
- [ ] `transactions.yml` retains all unresolved plus the newest 1,000 terminal records.
- [ ] The real-Paper empty-column and tick-continuity results are recorded in `docs/testing/AIRDR-3-paper-integration.md`.
