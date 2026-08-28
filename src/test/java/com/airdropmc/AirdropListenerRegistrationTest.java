package com.airdropmc;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

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
		MockBukkit.createMockPlugin("LuckPerms");
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

		assertTrue(plugin.isEnabled());
		assertEquals(1, HandlerList.getRegisteredListeners(plugin).stream()
				.filter(listener -> listener.getListener() instanceof CrateHopperListener)
				.count());
	}
}
