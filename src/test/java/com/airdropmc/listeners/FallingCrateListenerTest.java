package com.airdropmc.listeners;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import com.airdropmc.Crate;
import com.airdropmc.helpers.CrateManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FallingCrateListenerTest {

	private ServerMock server;
	private WorldMock world;
	private Plugin plugin;
	private final FallingCrateListener listener = new FallingCrateListener();

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("test_world");
		plugin = MockBukkit.createMockPlugin();
		clearCrateManager();
	}

	@AfterEach
	void tearDown() {
		clearCrateManager();
		MockBukkit.unmock();
	}

	@Test
	void onEntityChangeBlockEvent_removesFallingCrateAndLands() {
		FallingBlock fallingBlock = mock(FallingBlock.class);
		Crate crate = mock(Crate.class);
		Location entityLocation = new Location(world, 40, 90, 40);
		Block eventBlock = world.getBlockAt(12, 64, 12);
		EntityChangeBlockEvent event = mock(EntityChangeBlockEvent.class);

		when(event.getEntity()).thenReturn(fallingBlock);
		when(event.getBlock()).thenReturn(eventBlock);
		when(fallingBlock.getLocation()).thenReturn(entityLocation);
		CrateManager.addCrate(fallingBlock, crate);

		listener.onEntityChangeBlockEvent(event);

		assertFalse(CrateManager.hasCrate(fallingBlock));
		verify(event).setCancelled(true);
		verify(crate).land(eventBlock);
	}

	@Test
	void onEntityChangeBlockEvent_destroysOwnedCrate_whenEventAlreadyCancelled() {
		server.getPluginManager().registerEvents(listener, plugin);
		FallingBlock fallingBlock = mock(FallingBlock.class);
		Crate crate = mock(Crate.class);
		Block eventBlock = world.getBlockAt(12, 64, 12);
		EntityChangeBlockEvent event = new EntityChangeBlockEvent(
				fallingBlock, eventBlock, Material.BARREL.createBlockData());
		event.setCancelled(true);
		CrateManager.addCrate(fallingBlock, crate);

		server.getPluginManager().callEvent(event);

		assertFalse(CrateManager.hasCrate(fallingBlock));
		verify(crate).destroy();
		verify(crate, never()).land(any());
	}

	@Test
	void onEntityChangeBlockEvent_allowsHighPriorityProtectionToRejectLanding() {
		CancelLandingListener protectionListener = new CancelLandingListener();
		server.getPluginManager().registerEvents(listener, plugin);
		server.getPluginManager().registerEvents(protectionListener, plugin);
		FallingBlock fallingBlock = mock(FallingBlock.class);
		Crate crate = mock(Crate.class);
		Block eventBlock = world.getBlockAt(14, 64, 14);
		EntityChangeBlockEvent event = new EntityChangeBlockEvent(
				fallingBlock, eventBlock, Material.BARREL.createBlockData());
		CrateManager.addCrate(fallingBlock, crate);

		server.getPluginManager().callEvent(event);

		assertEquals(1, protectionListener.invocations);
		assertTrue(event.isCancelled());
		assertFalse(CrateManager.hasCrate(fallingBlock));
		verify(crate).destroy();
		verify(crate, never()).land(any());
	}

	@Test
	void onEntityChangeBlockEvent_ignoresUntrackedFallingBlock() {
		FallingBlock fallingBlock = mock(FallingBlock.class);
		EntityChangeBlockEvent event = mock(EntityChangeBlockEvent.class);

		when(event.getEntity()).thenReturn(fallingBlock);

		listener.onEntityChangeBlockEvent(event);

		assertTrue(CrateManager.getCrateMap().isEmpty());
		verify(event, never()).setCancelled(true);
	}

	@Test
	void onEntityChangeBlockEvent_destroysCrate_whenLandingFails() {
		FallingBlock fallingBlock = mock(FallingBlock.class);
		Crate crate = mock(Crate.class);
		Block eventBlock = world.getBlockAt(12, 64, 12);
		EntityChangeBlockEvent event = mock(EntityChangeBlockEvent.class);

		when(event.getEntity()).thenReturn(fallingBlock);
		when(event.getBlock()).thenReturn(eventBlock);
		doThrow(new IllegalStateException("failed to land")).when(crate).land(any());
		CrateManager.addCrate(fallingBlock, crate);

		assertThrows(IllegalStateException.class, () -> listener.onEntityChangeBlockEvent(event));
		assertFalse(CrateManager.hasCrate(fallingBlock));
		verify(crate).destroy();
	}

	@Test
	void onEntityChangeBlockEvent_ignoresNonFallingEntities() {
		Entity entity = mock(Entity.class);
		EntityChangeBlockEvent event = mock(EntityChangeBlockEvent.class);

		when(event.getEntity()).thenReturn(entity);

		listener.onEntityChangeBlockEvent(event);

		verify(event, never()).setCancelled(true);
	}

	private static class CancelLandingListener implements Listener {
		private int invocations;

		@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
		public void onEntityChangeBlock(EntityChangeBlockEvent event) {
			invocations++;
			event.setCancelled(true);
		}
	}

	private void clearCrateManager() {
		CrateManager.clearAll();
	}
}
