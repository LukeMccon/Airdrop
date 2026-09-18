package com.airdropmc.lightkeeper.economy;

import net.milkbowl.vault2.economy.AsyncEconomy;
import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.EconomyResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VaultUnlockedEconomyServiceTest {

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
