package com.airdropmc.economy;

import net.milkbowl.vault2.economy.AsyncEconomy;
import net.milkbowl.vault2.economy.EconomyResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VaultUnlockedEconomyProviderTest {

	private static final String CALLER = "Airdrop";

	@Test
	void withdrawUsesUuidAndExactDecimal() {
		AsyncEconomy economy = mock(AsyncEconomy.class);
		UUID playerId = UUID.randomUUID();
		BigDecimal amount = new BigDecimal("10.25");
		EconomyResponse response = response(amount, EconomyResponse.ResponseType.SUCCESS, "");
		when(economy.withdraw(CALLER, playerId, amount))
				.thenReturn(CompletableFuture.completedFuture(response));
		VaultUnlockedEconomyProvider provider = new VaultUnlockedEconomyProvider(economy, "ModernEco");

		EconomyResult result = provider.withdraw(new EconomyPlayer(playerId, "Luke"), amount)
				.toCompletableFuture().join();

		assertTrue(provider.nativeAsync());
		assertTrue(result.success());
		assertEquals("ModernEco", provider.getName());
		verify(economy).withdraw(CALLER, playerId, amount);
	}

	@Test
	void affordabilityMapsProviderFailureToRejection() {
		AsyncEconomy economy = mock(AsyncEconomy.class);
		UUID playerId = UUID.randomUUID();
		BigDecimal amount = BigDecimal.TEN;
		when(economy.canWithdraw(CALLER, playerId, amount)).thenReturn(CompletableFuture.completedFuture(
				response(BigDecimal.ZERO, EconomyResponse.ResponseType.FAILURE, "insufficient funds")));
		VaultUnlockedEconomyProvider provider = new VaultUnlockedEconomyProvider(economy, "ModernEco");

		EconomyResult result = provider.canAfford(new EconomyPlayer(playerId, "Luke"), amount)
				.toCompletableFuture().join();

		assertEquals(EconomyResult.Outcome.REJECTED, result.outcome());
		assertEquals("insufficient funds", result.message());
	}

	@Test
	void exceptionalMutationIsUnknown() {
		AsyncEconomy economy = mock(AsyncEconomy.class);
		UUID playerId = UUID.randomUUID();
		when(economy.deposit(CALLER, playerId, BigDecimal.ONE))
				.thenReturn(CompletableFuture.failedFuture(new IllegalStateException("database unavailable")));
		VaultUnlockedEconomyProvider provider = new VaultUnlockedEconomyProvider(economy, "ModernEco");

		EconomyResult result = provider.deposit(new EconomyPlayer(playerId, "Luke"), BigDecimal.ONE)
				.toCompletableFuture().join();

		assertEquals(EconomyResult.Outcome.UNKNOWN, result.outcome());
		assertEquals("database unavailable", result.message());
	}

	@Test
	void negativeAmountIsRejectedWithoutCallingProvider() {
		AsyncEconomy economy = mock(AsyncEconomy.class);
		VaultUnlockedEconomyProvider provider = new VaultUnlockedEconomyProvider(economy, "ModernEco");
		EconomyPlayer player = new EconomyPlayer(UUID.randomUUID(), "Luke");

		EconomyResult result = provider.withdraw(player, new BigDecimal("-1"))
				.toCompletableFuture().join();

		assertFalse(result.success());
		assertEquals(EconomyResult.Outcome.REJECTED, result.outcome());
		verify(economy, never()).withdraw(CALLER, player.uniqueId(), new BigDecimal("-1"));
	}

	private static EconomyResponse response(BigDecimal amount, EconomyResponse.ResponseType type, String message) {
		return new EconomyResponse(amount, BigDecimal.ZERO, type, message);
	}
}
