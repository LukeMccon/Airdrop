package com.airdropmc.lightkeeper.economy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EconomyLedgerTest {

	@Test
	void withdrawalAndRefundConserveExactBalanceAndTrackOperations() {
		EconomyLedger ledger = new EconomyLedger();
		UUID playerId = UUID.randomUUID();
		ledger.reset(playerId, new BigDecimal("100.00"));

		EconomyLedger.Transaction affordability = ledger.canWithdraw(playerId, new BigDecimal("10.25"));
		EconomyLedger.Transaction withdrawal = ledger.withdraw(playerId, new BigDecimal("10.25"));
		EconomyLedger.Transaction refund = ledger.deposit(playerId, new BigDecimal("10.25"));

		assertThat(affordability.success()).isTrue();
		assertThat(affordability.balance()).isEqualByComparingTo("100.00");
		assertThat(withdrawal.success()).isTrue();
		assertThat(withdrawal.balance()).isEqualByComparingTo("89.75");
		assertThat(refund.success()).isTrue();
		assertThat(refund.balance()).isEqualByComparingTo("100.00");
		assertThat(ledger.snapshot(playerId)).isEqualTo(new EconomyLedger.Snapshot(
				new BigDecimal("100.00"), 1, 1, 1));
	}

	@Test
	void rejectedWithdrawalDoesNotChangeBalance() {
		EconomyLedger ledger = new EconomyLedger();
		UUID playerId = UUID.randomUUID();
		ledger.reset(playerId, new BigDecimal("5.00"));

		EconomyLedger.Transaction withdrawal = ledger.withdraw(playerId, new BigDecimal("10.25"));

		assertThat(withdrawal.success()).isFalse();
		assertThat(withdrawal.errorMessage()).isEqualTo("Insufficient funds");
		assertThat(ledger.snapshot(playerId)).isEqualTo(new EconomyLedger.Snapshot(
				new BigDecimal("5.00"), 0, 1, 0));
	}
}
