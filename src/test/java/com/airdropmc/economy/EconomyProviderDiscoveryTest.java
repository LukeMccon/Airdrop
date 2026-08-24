package com.airdropmc.economy;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import net.milkbowl.vault2.economy.AsyncEconomy;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EconomyProviderDiscoveryTest {

	private ServerMock server;
	private MockPlugin registrar;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		registrar = MockBukkit.createMockPlugin("EconomyRegistrar");
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void nativeVaultUnlockedIsPreferredOverLegacyVault() {
		net.milkbowl.vault2.economy.Economy modern = mock(net.milkbowl.vault2.economy.Economy.class);
		AsyncEconomy async = mock(AsyncEconomy.class);
		when(modern.supportsAsync()).thenReturn(true);
		when(modern.async()).thenReturn(Optional.of(async));
		when(modern.getName()).thenReturn("ModernEco");
		server.getServicesManager().register(
				net.milkbowl.vault2.economy.Economy.class, modern, registrar, ServicePriority.Normal);

		net.milkbowl.vault.economy.Economy legacy = mock(net.milkbowl.vault.economy.Economy.class);
		server.getServicesManager().register(
				net.milkbowl.vault.economy.Economy.class, legacy, registrar, ServicePriority.Normal);

		EconomyProvider provider = EconomyProviderDiscovery.discover(server.getServicesManager()).orElseThrow();

		assertInstanceOf(VaultUnlockedEconomyProvider.class, provider);
		assertTrue(provider.nativeAsync());
	}

	@Test
	void modernServiceWithoutNativeAsyncFallsBackToLegacyVault() {
		net.milkbowl.vault2.economy.Economy modern = mock(net.milkbowl.vault2.economy.Economy.class);
		when(modern.supportsAsync()).thenReturn(false);
		when(modern.async()).thenReturn(Optional.empty());
		server.getServicesManager().register(
				net.milkbowl.vault2.economy.Economy.class, modern, registrar, ServicePriority.Normal);

		net.milkbowl.vault.economy.Economy legacy = mock(net.milkbowl.vault.economy.Economy.class);
		server.getServicesManager().register(
				net.milkbowl.vault.economy.Economy.class, legacy, registrar, ServicePriority.Normal);

		EconomyProvider provider = EconomyProviderDiscovery.discover(server.getServicesManager()).orElseThrow();

		assertInstanceOf(VaultEconomyProvider.class, provider);
	}

	@Test
	void noRegisteredEconomyReturnsEmpty() {
		assertTrue(EconomyProviderDiscovery.discover(server.getServicesManager()).isEmpty());
	}
}
