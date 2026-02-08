package com.airdropmc.listeners;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Crate;
import com.airdropmc.helpers.CrateManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CrateDestroyListenerTest {

	private ServerMock server;
	private WorldMock world;
	private Plugin plugin;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("test_world");
		plugin = MockBukkit.createMockPlugin();
		clearCrateManager();
	}

	@AfterEach
	void tearDown() throws Exception {
		clearCrateManager();
		MockBukkit.unmock();
	}

	@Test
	void onBlockBreak_doesNotRemoveCrate_whenEventGetsCancelled() {
		server.getPluginManager().registerEvents(new CancelBreakListener(), plugin);
		server.getPluginManager().registerEvents(new CrateDestroyListener(), plugin);

		PlayerMock player = server.addPlayer();
		Block block = world.getBlockAt(12, 64, 12);
		block.setType(Material.BARREL);
		Location barrelLocation = block.getLocation();

		Crate crate = mock(Crate.class);
		CrateManager.addCrate(barrelLocation, crate);

		server.getPluginManager().callEvent(new BlockBreakEvent(block, player));

		assertSame(crate, CrateManager.getCrate(barrelLocation));
		verify(crate, never()).destroy();
	}

	@Test
	void onBlockBreak_removesCrate_whenEventNotCancelled() {
		server.getPluginManager().registerEvents(new CrateDestroyListener(), plugin);

		PlayerMock player = server.addPlayer();
		Block block = world.getBlockAt(14, 64, 14);
		block.setType(Material.BARREL);
		Location barrelLocation = block.getLocation();

		Crate crate = mock(Crate.class);
		CrateManager.addCrate(barrelLocation, crate);

		server.getPluginManager().callEvent(new BlockBreakEvent(block, player));

		assertNull(CrateManager.getCrate(barrelLocation));
		verify(crate).destroy();
	}

	private static class CancelBreakListener implements Listener {
		@EventHandler(priority = EventPriority.HIGH)
		public void onBlockBreak(BlockBreakEvent e) {
			e.setCancelled(true);
		}
	}

	private void clearCrateManager() throws Exception {
		Field crateMapField = CrateManager.class.getDeclaredField("crateMap");
		crateMapField.setAccessible(true);
		((Map<?, ?>) crateMapField.get(null)).clear();

		Field landedCrateMapField = CrateManager.class.getDeclaredField("landedCrateMap");
		landedCrateMapField.setAccessible(true);
		((Map<?, ?>) landedCrateMapField.get(null)).clear();
	}
}
