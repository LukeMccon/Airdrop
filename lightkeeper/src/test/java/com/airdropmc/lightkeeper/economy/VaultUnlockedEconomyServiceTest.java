package com.airdropmc.lightkeeper.economy;

import net.milkbowl.vault2.economy.AsyncEconomy;
import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.EconomyResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.assertj.core.api.Assertions.assertThat;

class VaultUnlockedEconomyServiceTest {

	@Test
	void heldWithdrawalAllowsOtherAccountsToProgressAndPreventsResetUntilReleased() throws Exception {
		EconomyLedger ledger = new EconomyLedger();
		UUID player = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		ledger.reset(player, new BigDecimal("100.00"));
		ledger.reset(other, new BigDecimal("100.00"));
		try (var executor = Executors.newSingleThreadExecutor(); var controls = new EconomyFaultControls()) {
			controls.arm(player, EconomyOperationType.WITHDRAW, EconomyFaultControls.Mode.HOLD);
			var async = VaultUnlockedEconomyService.create(ledger, executor, operation -> {
				assertThatThrownBy(() -> ledger.reset(operation.playerId(), BigDecimal.ZERO))
						.isInstanceOf(IllegalArgumentException.class);
			}, controls).async().orElseThrow();
			var held = async.withdraw("Airdrop", player, new BigDecimal("10.25"));
			// FIFO single executor: this completion proves the earlier held operation didn't block it.
			assertThat(async.withdraw("Airdrop", other, new BigDecimal("10.25")).get(2, TimeUnit.SECONDS)
					.transactionSuccess()).isTrue();
			assertThat(held).isNotDone();
			assertThat(controls.hasPending()).isTrue();
			assertThat(ledger.snapshot(player).balance()).isEqualByComparingTo("89.75");
			assertThatThrownBy(() -> ledger.reset(player, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
			assertThat(controls.release(player, EconomyOperationType.WITHDRAW)).isTrue();
			assertThat(held.get(2, TimeUnit.SECONDS).transactionSuccess()).isTrue();
			assertThat(controls.hasPending()).isFalse();
			assertThat(controls.release(player, EconomyOperationType.WITHDRAW)).isFalse();
			assertThat(ledger.snapshot(player).withdrawals()).isEqualTo(1);
			ledger.reset(player, BigDecimal.ZERO);
		}
	}

	@Test
	void refundFaultsCountAttemptsWithoutInventingCreditsAndAreOneShot() {
		for (var mode : List.of(EconomyFaultControls.Mode.REJECT, EconomyFaultControls.Mode.EXCEPTION)) {
			EconomyLedger ledger = new EconomyLedger();
			UUID player = UUID.randomUUID();
			ledger.reset(player, new BigDecimal("89.75"));
			List<VaultUnlockedEconomyService.Operation> operations = new ArrayList<>();
			try (var controls = new EconomyFaultControls()) {
				controls.arm(player, EconomyOperationType.DEPOSIT, mode);
				var async = VaultUnlockedEconomyService.create(ledger, Runnable::run, operations::add, controls)
						.async().orElseThrow();
				var refund = async.deposit("Airdrop", player, new BigDecimal("10.25"));
				if (mode == EconomyFaultControls.Mode.EXCEPTION) {
					assertThatThrownBy(refund::join).isInstanceOf(CompletionException.class)
							.hasRootCauseMessage(EconomyFaultControls.EXCEPTIONAL_REFUND);
				} else {
					assertThat(refund.join().transactionSuccess()).isFalse();
				}
				assertThat(operations).hasSize(1);
				assertThat(operations.getFirst().transaction().success()).isFalse();
				assertThat(ledger.snapshot(player)).isEqualTo(new EconomyLedger.Snapshot(new BigDecimal("89.75"), 0, 0, 1));
				assertThat(controls.hasPending()).isFalse();
				assertThat(async.deposit("Airdrop", player, new BigDecimal("10.25")).join().transactionSuccess()).isTrue();
				assertThat(ledger.snapshot(player)).isEqualTo(new EconomyLedger.Snapshot(new BigDecimal("100.00"), 0, 0, 2));
			}
		}
	}

	@Test
	void heldRefundAppliesOnceAndCloseSettlesItsConfirmation() {
		EconomyLedger ledger = new EconomyLedger();
		UUID player = UUID.randomUUID();
		ledger.reset(player, new BigDecimal("89.75"));
		var controls = new EconomyFaultControls();
		controls.arm(player, EconomyOperationType.DEPOSIT, EconomyFaultControls.Mode.HOLD);
		var async = VaultUnlockedEconomyService.create(ledger, Runnable::run, ignored -> {}, controls).async().orElseThrow();
		var refund = async.deposit("Airdrop", player, new BigDecimal("10.25"));
		assertThat(refund).isNotDone();
		assertThat(ledger.snapshot(player)).isEqualTo(new EconomyLedger.Snapshot(new BigDecimal("100.00"), 0, 0, 1));
		controls.close();
		assertThat(refund.join().transactionSuccess()).isTrue();
		assertThat(controls.hasPending()).isFalse();
		ledger.reset(player, BigDecimal.ZERO);
	}

	@Test
	void heldOperationCanBeReleasedFromItsObserver() {
		EconomyLedger ledger = new EconomyLedger();
		UUID player = UUID.randomUUID();
		ledger.reset(player, new BigDecimal("100.00"));
		try (var controls = new EconomyFaultControls()) {
			controls.arm(player, EconomyOperationType.WITHDRAW, EconomyFaultControls.Mode.HOLD);
			var async = VaultUnlockedEconomyService.create(ledger, Runnable::run, operation -> {
				assertThat(controls.release(player, operation.operation())).isTrue();
				assertThatThrownBy(() -> ledger.reset(player, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
			}, controls).async().orElseThrow();
			assertThat(async.withdraw("Airdrop", player, new BigDecimal("10.25")).join().transactionSuccess()).isTrue();
			assertThat(controls.hasPending()).isFalse();
		}
	}

	@Test
	void closeSettlesQueuedOperationsBeforeExecutorShutdownWithoutApplyingThem() {
		EconomyLedger ledger = new EconomyLedger();
		UUID player = UUID.randomUUID();
		ledger.reset(player, new BigDecimal("100.00"));
		var controls = new EconomyFaultControls();
		List<Runnable> queued = new ArrayList<>();
		List<VaultUnlockedEconomyService.Operation> operations = new ArrayList<>();
		var async = VaultUnlockedEconomyService.create(ledger, queued::add, operations::add, controls).async().orElseThrow();
		var withdrawal = async.withdraw("Airdrop", player, new BigDecimal("10.25"));
		assertThat(controls.hasPending()).isTrue();
		assertThatThrownBy(() -> ledger.reset(player, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
		controls.close();
		assertThatThrownBy(withdrawal::join).isInstanceOf(CompletionException.class)
				.hasRootCauseMessage("Fixture closed before operation completed");
		assertThat(controls.hasPending()).isFalse();
		queued.forEach(Runnable::run);
		assertThat(operations).isEmpty();
		assertThat(ledger.snapshot(player)).isEqualTo(new EconomyLedger.Snapshot(new BigDecimal("100.00"), 0, 0, 0));
		ledger.reset(player, BigDecimal.ZERO);
	}

	@Test
	void modernAsyncServiceMutatesTheLedgerAndReportsEveryOperation() {
		EconomyLedger ledger = new EconomyLedger();
		UUID playerId = UUID.randomUUID();
		ledger.reset(playerId, new BigDecimal("100.00"));
		List<VaultUnlockedEconomyService.Operation> operations = new ArrayList<>();
		Economy economy = VaultUnlockedEconomyService.create(ledger, Runnable::run, operations::add);

		assertThat(economy.isEnabled()).isTrue();
		assertThat(economy.getName()).isEqualTo("LightKeeper Economy");
		assertThat(economy.supportsAsync()).isTrue();
		AsyncEconomy async = economy.async().orElseThrow();

		EconomyResponse affordability = async.canWithdraw("Airdrop", playerId, new BigDecimal("10.25")).join();
		EconomyResponse withdrawal = async.withdraw("Airdrop", playerId, new BigDecimal("10.25")).join();
		EconomyResponse refund = async.deposit("Airdrop", playerId, new BigDecimal("10.25")).join();

		assertThat(affordability.transactionSuccess()).isTrue();
		assertThat(withdrawal.transactionSuccess()).isTrue();
		assertThat(withdrawal.balance).isEqualByComparingTo("89.75");
		assertThat(refund.transactionSuccess()).isTrue();
		assertThat(refund.balance).isEqualByComparingTo("100.00");
		assertThat(operations).extracting(VaultUnlockedEconomyService.Operation::operation)
				.containsExactly(
						EconomyOperationType.CAN_WITHDRAW,
						EconomyOperationType.WITHDRAW,
						EconomyOperationType.DEPOSIT);
		assertThat(operations).allSatisfy(operation -> {
			assertThat(operation.caller()).isEqualTo("Airdrop");
			assertThat(operation.playerId()).isEqualTo(playerId);
			assertThat(operation.amount()).isEqualByComparingTo("10.25");
		});
	}
}
