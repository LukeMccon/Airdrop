package com.airdropmc;

import com.airdropmc.helpers.CrateManager;
import com.airdropmc.packages.Package;
import com.airdropmc.packages.PackageManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FirstRunOnboardingTest {

	private ServerMock server;

	@AfterEach
	void tearDown() {
		CrateManager.clearAll();
		PackageManager.clear();
		MockBukkit.unmock();
	}

	@Test
	void missingFilesProvisionOneFreeStarterAndAllowAnOperatorDropWithoutOptionalDependencies()
			throws Exception {
		Airdrop plugin = loadUnconfiguredPlugin();

		assertNull(server.getPluginManager().getPlugin("LuckPerms"));
		assertNull(Airdrop.getEconomyProvider());
		assertTrue(plugin.isEnabled());
		assertTrue(Airdrop.isReady());
		assertEquals(Set.of("starter"), PackageManager.getPackages());
		Package starter = PackageManager.get("starter");
		assertEquals(0.0, starter.getPrice());

		Path packagesFile = plugin.getDataFolder().toPath().resolve("packages.yml");
		assertTrue(Files.exists(packagesFile));
		YamlConfiguration persisted = YamlConfiguration.loadConfiguration(packagesFile.toFile());
		assertEquals(0.0, persisted.getDouble("packages.starter.price"));
		assertEquals(1, persisted.getConfigurationSection("packages").getKeys(false).size());

		PlayerMock operator = server.addPlayer();
		operator.setOp(true);
		assertTrue(server.dispatchCommand(operator, "airdrop starter"));
		assertEquals(1, Airdrop.getDropAdmissionController().snapshot().falling(),
				"the free starter should reach the real drop path without an economy provider");
	}

	@Test
	void startupKeepsAnExistingPackageRegistryAndDoesNotDuplicateStarter() throws Exception {
		server = MockBukkit.mock();
		Airdrop plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Path dataDirectory = plugin.getDataFolder().toPath();
		Files.createDirectories(dataDirectory);
		Files.writeString(dataDirectory.resolve("config.yml"),
				"language: en\neconomy:\n  enabled: true\n", StandardCharsets.UTF_8);
		String packages = """
				packages:
				  starter:
				    price: 7.5
				    items: []
				  custom:
				    price: 0.0
				    items: []
				""";
		Files.writeString(dataDirectory.resolve("packages.yml"), packages, StandardCharsets.UTF_8);

		server.getPluginManager().enablePlugin(plugin);
		awaitReady(plugin);

		assertEquals(List.of("custom", "starter"), PackageManager.getPackages().stream().sorted().toList());
		assertEquals(7.5, PackageManager.get("starter").getPrice());
		YamlConfiguration persisted = YamlConfiguration.loadConfiguration(
				dataDirectory.resolve("packages.yml").toFile());
		assertEquals(2, persisted.getConfigurationSection("packages").getKeys(false).size());
		assertEquals(7.5, persisted.getDouble("packages.starter.price"));
	}

	@Test
	void bundledPackagesDocumentIsAnExplicitEmptyRegistry() throws Exception {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream("packages.yml")) {
			assertNotNull(input);
			String contents = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			assertEquals("packages: {}\n", contents);
		}
	}

	private Airdrop loadUnconfiguredPlugin() {
		server = MockBukkit.mock();
		Airdrop plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		server.getPluginManager().enablePlugin(plugin);
		awaitReady(plugin);
		return plugin;
	}

	private void awaitReady(Airdrop plugin) {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline && plugin.isEnabled() && !Airdrop.isReady()) {
			server.getScheduler().performOneTick();
			LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
		}
		assertTrue(plugin.isEnabled(), "Airdrop disabled during first-run startup");
		assertTrue(Airdrop.isReady(), "Timed out waiting for first-run startup");
	}
}
