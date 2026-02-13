package com.airdropmc.economy;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import me.lokka30.treasury.api.common.service.ServicePriority;
import me.lokka30.treasury.api.common.service.ServiceRegistry;
import me.lokka30.treasury.api.economy.account.PlayerAccount;
import me.lokka30.treasury.api.economy.account.accessor.AccountAccessor;
import me.lokka30.treasury.api.economy.account.accessor.PlayerAccountAccessor;
import me.lokka30.treasury.api.economy.currency.Currency;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TreasuryEconomyProviderTest {

	private static final String REGISTRAR = "airdrop-test";

	@AfterEach
	void tearDown() {
		ServiceRegistry.INSTANCE.unregisterAll(REGISTRAR);
	}

	@Test
	void fromServiceRegistry_returnsRegisteredProvider() {
		me.lokka30.treasury.api.economy.EconomyProvider treasury = mock(me.lokka30.treasury.api.economy.EconomyProvider.class);
		Currency currency = mock(Currency.class);
		when(treasury.getPrimaryCurrency()).thenReturn(currency);

		ServiceRegistry.INSTANCE.registerService(
				me.lokka30.treasury.api.economy.EconomyProvider.class, treasury, REGISTRAR, ServicePriority.NORMAL);

		Optional<TreasuryEconomyProvider> provider = TreasuryEconomyProvider.fromServiceRegistry();
		assertTrue(provider.isPresent());
		assertTrue(provider.orElseThrow().getName().contains(REGISTRAR));
	}

	@Test
	void withdraw_returnsFailure_whenBalanceIsInsufficient() {
		me.lokka30.treasury.api.economy.EconomyProvider treasury = mock(me.lokka30.treasury.api.economy.EconomyProvider.class);
		AccountAccessor accountAccessor = mock(AccountAccessor.class);
		PlayerAccountAccessor playerAccessor = mock(PlayerAccountAccessor.class);
		PlayerAccount account = mock(PlayerAccount.class);
		Currency currency = mock(Currency.class);
		Player player = mock(Player.class);
		UUID uuid = UUID.randomUUID();

		when(treasury.getPrimaryCurrency()).thenReturn(currency);
		when(treasury.accountAccessor()).thenReturn(accountAccessor);
		when(accountAccessor.player()).thenReturn(playerAccessor);
		when(player.getUniqueId()).thenReturn(uuid);
		when(playerAccessor.withUniqueId(uuid)).thenReturn(playerAccessor);
		when(playerAccessor.get()).thenReturn(CompletableFuture.completedFuture(account));
		when(account.retrieveBalance(currency)).thenReturn(CompletableFuture.completedFuture(BigDecimal.valueOf(5)));

		ServiceRegistry.INSTANCE.registerService(
				me.lokka30.treasury.api.economy.EconomyProvider.class, treasury, REGISTRAR, ServicePriority.NORMAL);
		TreasuryEconomyProvider provider = TreasuryEconomyProvider.fromServiceRegistry().orElseThrow();

		EconomyResult result = provider.withdraw(player, 10);
		assertFalse(result.success());
		verify(account, never()).withdrawBalance(any(), any(), eq(currency));
	}

	@Test
	void deposit_returnsSuccess_whenTransactionCompletes() {
		me.lokka30.treasury.api.economy.EconomyProvider treasury = mock(me.lokka30.treasury.api.economy.EconomyProvider.class);
		AccountAccessor accountAccessor = mock(AccountAccessor.class);
		PlayerAccountAccessor playerAccessor = mock(PlayerAccountAccessor.class);
		PlayerAccount account = mock(PlayerAccount.class);
		Currency currency = mock(Currency.class);
		Player player = mock(Player.class);
		UUID uuid = UUID.randomUUID();

		when(treasury.getPrimaryCurrency()).thenReturn(currency);
		when(treasury.accountAccessor()).thenReturn(accountAccessor);
		when(accountAccessor.player()).thenReturn(playerAccessor);
		when(player.getUniqueId()).thenReturn(uuid);
		when(playerAccessor.withUniqueId(uuid)).thenReturn(playerAccessor);
		when(playerAccessor.get()).thenReturn(CompletableFuture.completedFuture(account));
		when(account.depositBalance(argThat(value -> value.compareTo(BigDecimal.TEN) == 0), any(), eq(currency)))
				.thenReturn(CompletableFuture.completedFuture(BigDecimal.valueOf(10)));

		ServiceRegistry.INSTANCE.registerService(
				me.lokka30.treasury.api.economy.EconomyProvider.class, treasury, REGISTRAR, ServicePriority.NORMAL);
		TreasuryEconomyProvider provider = TreasuryEconomyProvider.fromServiceRegistry().orElseThrow();

		EconomyResult result = provider.deposit(player, 10);
		assertTrue(result.success());
	}

	@Test
	void getBalance_returnsZero_whenLookupFails() {
		me.lokka30.treasury.api.economy.EconomyProvider treasury = mock(me.lokka30.treasury.api.economy.EconomyProvider.class);
		AccountAccessor accountAccessor = mock(AccountAccessor.class);
		PlayerAccountAccessor playerAccessor = mock(PlayerAccountAccessor.class);
		Currency currency = mock(Currency.class);
		Player player = mock(Player.class);
		UUID uuid = UUID.randomUUID();

		when(treasury.getPrimaryCurrency()).thenReturn(currency);
		when(treasury.accountAccessor()).thenReturn(accountAccessor);
		when(accountAccessor.player()).thenReturn(playerAccessor);
		when(player.getUniqueId()).thenReturn(uuid);
		when(playerAccessor.withUniqueId(uuid)).thenReturn(playerAccessor);
		when(playerAccessor.get()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("missing account")));

		ServiceRegistry.INSTANCE.registerService(
				me.lokka30.treasury.api.economy.EconomyProvider.class, treasury, REGISTRAR, ServicePriority.NORMAL);
		TreasuryEconomyProvider provider = TreasuryEconomyProvider.fromServiceRegistry().orElseThrow();

		assertEquals(0.0, provider.getBalance(player));
	}
}
