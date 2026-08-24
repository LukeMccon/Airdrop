package com.airdropmc;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import me.lokka30.treasury.api.common.service.ServicePriority;
import me.lokka30.treasury.api.common.service.ServiceRegistry;
import me.lokka30.treasury.api.economy.currency.Currency;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import com.airdropmc.listeners.CrateHopperListener;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AirdropListenerRegistrationTest {

	private static final String REGISTRAR = "airdrop-listener-test";

	private ServerMock server;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		MockBukkit.createMockPlugin("LuckPerms");
		me.lokka30.treasury.api.economy.EconomyProvider treasury =
				mock(me.lokka30.treasury.api.economy.EconomyProvider.class);
		when(treasury.getPrimaryCurrency()).thenReturn(mock(Currency.class));
		ServiceRegistry.INSTANCE.registerService(
				me.lokka30.treasury.api.economy.EconomyProvider.class,
				treasury,
				REGISTRAR,
				ServicePriority.NORMAL);
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
		ServiceRegistry.INSTANCE.unregisterAll(REGISTRAR);
	}

	@Test
	void onEnable_registersCrateHopperListener() throws Exception {
		Airdrop plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(plugin.getDataFolder().toPath());
		Files.writeString(plugin.getDataFolder().toPath().resolve("packages.yml"), "packages: {}\n");
		server.getPluginManager().enablePlugin(plugin);

		assertTrue(plugin.isEnabled());
		assertTrue(HandlerList.getRegisteredListeners(plugin).stream()
				.anyMatch(listener -> listener.getListener() instanceof CrateHopperListener));
	}
}
