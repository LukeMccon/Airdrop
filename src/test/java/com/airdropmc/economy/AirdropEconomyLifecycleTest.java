package com.airdropmc.economy;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.commands.CmdAirdrop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault2.economy.AsyncEconomy;
import org.bukkit.command.Command;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AirdropEconomyLifecycleTest {

	private ServerMock server;
	private MockPlugin registrar;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		MockBukkit.createMockPlugin("LuckPerms");
		registrar = MockBukkit.createMockPlugin("EconomyRegistrar");
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void serviceEventsRepublishBestAvailableProvider() throws Exception {
		Airdrop plugin = loadPlugin(true);
		assertTrue(plugin.isEnabled());
		assertNull(Airdrop.getEconomyProvider());

		net.milkbowl.vault.economy.Economy legacyA = legacy("Legacy A");
		net.milkbowl.vault.economy.Economy legacyB = legacy("Legacy B");
		net.milkbowl.vault.economy.Economy legacyC = legacy("Legacy C");
		net.milkbowl.vault2.economy.Economy modern = modern("Modern");

		register(net.milkbowl.vault.economy.Economy.class, legacyA, ServicePriority.Normal);
		assertProvider(VaultEconomyProvider.class, "Legacy A");

		register(net.milkbowl.vault.economy.Economy.class, legacyB, ServicePriority.High);
		assertProvider(VaultEconomyProvider.class, "Legacy B");

		register(net.milkbowl.vault2.economy.Economy.class, modern, ServicePriority.Normal);
		assertProvider(VaultUnlockedEconomyProvider.class, "Modern");

		register(net.milkbowl.vault.economy.Economy.class, legacyC, ServicePriority.Highest);
		assertProvider(VaultUnlockedEconomyProvider.class, "Modern");
		server.getServicesManager().unregister(net.milkbowl.vault.economy.Economy.class, legacyC);
		assertProvider(VaultUnlockedEconomyProvider.class, "Modern");

		server.getServicesManager().unregister(net.milkbowl.vault2.economy.Economy.class, modern);
		assertProvider(VaultEconomyProvider.class, "Legacy B");
		server.getServicesManager().unregister(net.milkbowl.vault.economy.Economy.class, legacyB);
		assertProvider(VaultEconomyProvider.class, "Legacy A");
		server.getServicesManager().unregister(net.milkbowl.vault.economy.Economy.class, legacyA);
		assertNull(Airdrop.getEconomyProvider());

		register(net.milkbowl.vault.economy.Economy.class, legacyC, ServicePriority.Normal);
		assertProvider(VaultEconomyProvider.class, "Legacy C");
	}

	@Test
	void reloadTracksEconomyEnablementAndReportsOutcome() throws Exception {
		net.milkbowl.vault.economy.Economy legacyA = legacy("Legacy A");
		register(net.milkbowl.vault.economy.Economy.class, legacyA, ServicePriority.Normal);
		Airdrop plugin = loadPlugin(false);
		assertNull(Airdrop.getEconomyProvider());

		PlayerMock operator = server.addPlayer();
		operator.setOp(true);

		writeConfig(plugin, true);
		assertTrue(runReload(operator).contains("Legacy A"));
		assertProvider(VaultEconomyProvider.class, "Legacy A");

		writeConfig(plugin, false);
		assertTrue(runReload(operator).toLowerCase().contains("disabled"));
		assertNull(Airdrop.getEconomyProvider());

		net.milkbowl.vault.economy.Economy legacyB = legacy("Legacy B");
		register(net.milkbowl.vault.economy.Economy.class, legacyB, ServicePriority.High);
		server.getServicesManager().unregister(net.milkbowl.vault.economy.Economy.class, legacyA);
		assertNull(Airdrop.getEconomyProvider());

		writeConfig(plugin, true);
		assertTrue(runReload(operator).contains("Legacy B"));
		assertProvider(VaultEconomyProvider.class, "Legacy B");

		server.getServicesManager().unregister(net.milkbowl.vault.economy.Economy.class, legacyB);
		assertNull(Airdrop.getEconomyProvider());
		assertTrue(runReload(operator).toLowerCase().contains("no economy provider"));
	}

	@Test
	void malformedReloadRetainsPublishedConfigurationPackagesLanguageEconomyAndAdmission() throws Exception {
		net.milkbowl.vault.economy.Economy legacy = legacy("Legacy");
		register(net.milkbowl.vault.economy.Economy.class, legacy, ServicePriority.Normal);
		Airdrop plugin = loadPlugin(true);
		PlayerMock operator = server.addPlayer();
		operator.setOp(true);

		Object configuration = Airdrop.getConfiguration();
		Object packagesConfiguration = Airdrop.getPackagesConfiguration();
		Object economyProvider = Airdrop.getEconomyProvider();
		Object admission = Airdrop.getDropAdmissionController();
		String language = plugin.getLanguageManager().getCurrentLanguage();

		writeConfig(plugin, false);
		Files.writeString(plugin.getDataFolder().toPath().resolve("packages.yml"), "packages: [\n");

		String response = runReload(operator);

		assertTrue(response.toLowerCase().contains("previous configuration remains active"));
		assertSame(configuration, Airdrop.getConfiguration());
		assertSame(packagesConfiguration, Airdrop.getPackagesConfiguration());
		assertSame(economyProvider, Airdrop.getEconomyProvider());
		assertSame(admission, Airdrop.getDropAdmissionController());
		assertEquals(language, plugin.getLanguageManager().getCurrentLanguage());
		assertTrue(Airdrop.isReady());
	}

	@Test
	void malformedStartupDisablesWithoutPublishingReadyState() throws Exception {
		Airdrop plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(plugin.getDataFolder().toPath());
		Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"),
				"language: en\neconomy:\n  enabled: true\n");
		Files.writeString(plugin.getDataFolder().toPath().resolve("packages.yml"), "packages: [\n");

		server.getPluginManager().enablePlugin(plugin);
		awaitCondition(() -> !plugin.isEnabled());

		assertFalse(plugin.isEnabled());
		assertFalse(Airdrop.isReady());
		assertNull(Airdrop.getConfiguration());
		assertTrue(com.airdropmc.packages.PackageManager.getPackages().isEmpty());
	}

	private Airdrop loadPlugin(boolean economyEnabled) throws Exception {
		Airdrop loaded = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(loaded.getDataFolder().toPath());
		Files.writeString(loaded.getDataFolder().toPath().resolve("packages.yml"), "packages: {}\n");
		writeConfig(loaded, economyEnabled);
		server.getPluginManager().enablePlugin(loaded);
		awaitReady(loaded);
		return loaded;
	}

	private void writeConfig(Airdrop target, boolean economyEnabled) throws Exception {
		Path config = target.getDataFolder().toPath().resolve("config.yml");
		Files.writeString(config, "language: en\neconomy:\n  enabled: " + economyEnabled + "\n");
	}

	private String runReload(PlayerMock operator) {
		boolean handled = new CmdAirdrop().onCommand(
				operator, mock(Command.class), "airdrop", new String[]{"reload"});
		assertTrue(handled);
		Component started = operator.nextComponentMessage();
		assertTrue(plain(started).toLowerCase().contains("reloading configuration"));
		return awaitMessage(operator);
	}

	private void awaitReady(Airdrop plugin) {
		awaitCondition(() -> Airdrop.isReady() || !plugin.isEnabled());
		assertTrue(plugin.isEnabled());
		assertTrue(Airdrop.isReady());
	}

	private String awaitMessage(PlayerMock player) {
		java.util.concurrent.atomic.AtomicReference<Component> message =
				new java.util.concurrent.atomic.AtomicReference<>();
		awaitCondition(() -> {
			Component next = player.nextComponentMessage();
			if (next == null) {
				return false;
			}
			message.set(next);
			return true;
		});
		return plain(message.get());
	}

	private void awaitCondition(java.util.function.BooleanSupplier condition) {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			server.getScheduler().performOneTick();
			LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
		}
		assertTrue(condition.getAsBoolean(), "Timed out waiting for asynchronous configuration work");
	}

	private static String plain(Component message) {
		assertTrue(message != null, "Expected a command response");
		return PlainTextComponentSerializer.plainText().serialize(message);
	}

	private <T> void register(Class<T> service, T provider, ServicePriority priority) {
		server.getServicesManager().register(service, provider, registrar, priority);
	}

	private void assertProvider(Class<? extends EconomyProvider> type, String name) {
		EconomyProvider selected = Airdrop.getEconomyProvider();
		assertInstanceOf(type, selected);
		assertEquals(name, selected.getName());
	}

	private net.milkbowl.vault.economy.Economy legacy(String name) {
		net.milkbowl.vault.economy.Economy economy = mock(net.milkbowl.vault.economy.Economy.class);
		when(economy.isEnabled()).thenReturn(true);
		when(economy.getName()).thenReturn(name);
		return economy;
	}

	private net.milkbowl.vault2.economy.Economy modern(String name) {
		net.milkbowl.vault2.economy.Economy economy = mock(net.milkbowl.vault2.economy.Economy.class);
		AsyncEconomy async = mock(AsyncEconomy.class);
		when(economy.isEnabled()).thenReturn(true);
		when(economy.supportsAsync()).thenReturn(true);
		when(economy.async()).thenReturn(Optional.of(async));
		when(economy.getName()).thenReturn(name);
		return economy;
	}
}
