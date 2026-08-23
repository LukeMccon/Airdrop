package com.airdropmc.listeners;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import com.airdropmc.Crate;
import com.airdropmc.helpers.CrateManager;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.block.Block;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
	private final FallingCrateListener listener = new FallingCrateListener();

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("test_world");
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

	private void clearCrateManager() {
		CrateManager.clearAll();
	}
}
