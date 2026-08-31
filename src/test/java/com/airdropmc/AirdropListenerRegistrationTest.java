package com.airdropmc;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

import com.airdropmc.listeners.CrateHopperListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AirdropListenerRegistrationTest {

	private ServerMock server;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		var vaultPlugin = MockBukkit.createMockPlugin("Vault");
		Economy economy = mock(Economy.class);
		when(economy.getName()).thenReturn("TestEconomy");
		server.getServicesManager().register(
				Economy.class,
				economy,
				vaultPlugin,
				ServicePriority.Normal);
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void onEnable_registersCrateHopperListener() throws Exception {
		Airdrop plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(plugin.getDataFolder().toPath());
		Files.writeString(plugin.getDataFolder().toPath().resolve("packages.yml"), "packages: {}\n");
		server.getPluginManager().enablePlugin(plugin);
		awaitReady(plugin);

		assertTrue(plugin.isEnabled());
		assertEquals(1, HandlerList.getRegisteredListeners(plugin).stream()
				.filter(listener -> listener.getListener() instanceof CrateHopperListener)
				.count());
	}

	private void awaitReady(Airdrop plugin) {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline && plugin.isEnabled() && !Airdrop.isReady()) {
			server.getScheduler().performOneTick();
			LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
		}
		assertTrue(Airdrop.isReady(), "Timed out waiting for asynchronous startup");
	}
}
