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
		net.milkbowl.vault2.economy.Economy modern = healthyModern();
		server.getServicesManager().register(
				net.milkbowl.vault2.economy.Economy.class, modern, registrar, ServicePriority.Normal);

		net.milkbowl.vault.economy.Economy legacy = healthyLegacy();
		server.getServicesManager().register(
				net.milkbowl.vault.economy.Economy.class, legacy, registrar, ServicePriority.Normal);

		EconomyProvider provider = EconomyProviderDiscovery.discover(server.getServicesManager()).orElseThrow();

		assertInstanceOf(VaultUnlockedEconomyProvider.class, provider);
		assertTrue(provider.nativeAsync());
	}

	@Test
	void modernServiceWithoutNativeAsyncFallsBackToLegacyVault() {
		net.milkbowl.vault2.economy.Economy modern = mock(net.milkbowl.vault2.economy.Economy.class);
		when(modern.isEnabled()).thenReturn(true);
		when(modern.supportsAsync()).thenReturn(false);
		when(modern.async()).thenReturn(Optional.empty());
		server.getServicesManager().register(
				net.milkbowl.vault2.economy.Economy.class, modern, registrar, ServicePriority.Normal);

		net.milkbowl.vault.economy.Economy legacy = healthyLegacy();
		server.getServicesManager().register(
				net.milkbowl.vault.economy.Economy.class, legacy, registrar, ServicePriority.Normal);

		EconomyProvider provider = EconomyProviderDiscovery.discover(server.getServicesManager()).orElseThrow();

		assertInstanceOf(VaultEconomyProvider.class, provider);
	}

	@Test
	void noRegisteredEconomyReturnsEmpty() {
		assertTrue(EconomyProviderDiscovery.discover(server.getServicesManager()).isEmpty());
	}

	@Test
	void disabledLegacyProviderIsUnavailable() {
		net.milkbowl.vault.economy.Economy legacy = healthyLegacy();
		when(legacy.isEnabled()).thenReturn(false);
		server.getServicesManager().register(
				net.milkbowl.vault.economy.Economy.class, legacy, registrar, ServicePriority.Normal);

		assertTrue(EconomyProviderDiscovery.discover(server.getServicesManager()).isEmpty());
	}

	@Test
	void disabledModernProviderFallsBackToHealthyLegacy() {
		net.milkbowl.vault2.economy.Economy modern = healthyModern();
		when(modern.isEnabled()).thenReturn(false);
		server.getServicesManager().register(
				net.milkbowl.vault2.economy.Economy.class, modern, registrar, ServicePriority.Normal);

		net.milkbowl.vault.economy.Economy legacy = healthyLegacy();
		server.getServicesManager().register(
				net.milkbowl.vault.economy.Economy.class, legacy, registrar, ServicePriority.Normal);

		assertInstanceOf(VaultEconomyProvider.class,
				EconomyProviderDiscovery.discover(server.getServicesManager()).orElseThrow());
	}

	@Test
	void modernEnabledLinkageFailureFallsBackToHealthyLegacy() {
		net.milkbowl.vault2.economy.Economy modern = healthyModern();
		when(modern.isEnabled()).thenThrow(new NoClassDefFoundError("provider dependency"));
		server.getServicesManager().register(
				net.milkbowl.vault2.economy.Economy.class, modern, registrar, ServicePriority.Normal);

		net.milkbowl.vault.economy.Economy legacy = healthyLegacy();
		server.getServicesManager().register(
				net.milkbowl.vault.economy.Economy.class, legacy, registrar, ServicePriority.Normal);

		assertInstanceOf(VaultEconomyProvider.class,
				EconomyProviderDiscovery.discover(server.getServicesManager()).orElseThrow());
	}

	@Test
	void legacyEnabledRuntimeFailureIsUnavailable() {
		net.milkbowl.vault.economy.Economy legacy = healthyLegacy();
		when(legacy.isEnabled()).thenThrow(new IllegalStateException("provider unavailable"));
		server.getServicesManager().register(
				net.milkbowl.vault.economy.Economy.class, legacy, registrar, ServicePriority.Normal);

		assertTrue(EconomyProviderDiscovery.discover(server.getServicesManager()).isEmpty());
	}

	private net.milkbowl.vault.economy.Economy healthyLegacy() {
		net.milkbowl.vault.economy.Economy economy = mock(net.milkbowl.vault.economy.Economy.class);
		when(economy.isEnabled()).thenReturn(true);
		return economy;
	}

	private net.milkbowl.vault2.economy.Economy healthyModern() {
		net.milkbowl.vault2.economy.Economy economy = mock(net.milkbowl.vault2.economy.Economy.class);
		when(economy.isEnabled()).thenReturn(true);
		when(economy.supportsAsync()).thenReturn(true);
		when(economy.async()).thenReturn(Optional.of(mock(AsyncEconomy.class)));
		when(economy.getName()).thenReturn("ModernEco");
		return economy;
	}
}
