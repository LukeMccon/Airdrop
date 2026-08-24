package com.airdropmc.economy;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VaultEconomyProviderTest {

	private ServerMock server;
	private PlayerMock player;
	private Economy economy;
	private VaultEconomyProvider provider;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		player = server.addPlayer("Luke");
		economy = mock(Economy.class);
		when(economy.getName()).thenReturn("LegacyEco");
		provider = new VaultEconomyProvider(economy);
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void affordabilityRunsImmediatelyAndReturnsCompletedStage() {
		when(economy.has(player, 10.0)).thenReturn(true);

		CompletionStage<EconomyResult> stage = provider.canAfford(
				new EconomyPlayer(player.getUniqueId(), player.getName()), BigDecimal.TEN);

		assertFalse(provider.nativeAsync());
		assertTrue(stage.toCompletableFuture().isDone());
		assertTrue(stage.toCompletableFuture().join().success());
		verify(economy).has(player, 10.0);
	}

	@Test
	void withdrawMapsLegacyResponse() {
		when(economy.withdrawPlayer(player, 4.5)).thenReturn(new EconomyResponse(
				4.5, 5.5, EconomyResponse.ResponseType.SUCCESS, ""));

		EconomyResult result = provider.withdraw(
				new EconomyPlayer(player.getUniqueId(), player.getName()), new BigDecimal("4.5"))
				.toCompletableFuture().join();

		assertTrue(result.success());
		verify(economy).withdrawPlayer(player, 4.5);
	}

	@Test
	void negativeAmountIsRejectedWithoutCallingProvider() {
		EconomyResult result = provider.deposit(
				new EconomyPlayer(player.getUniqueId(), player.getName()), new BigDecimal("-1"))
				.toCompletableFuture().join();

		assertFalse(result.success());
		verify(economy, never()).depositPlayer(player, -1.0);
	}
}
