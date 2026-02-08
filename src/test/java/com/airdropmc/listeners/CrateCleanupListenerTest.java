package com.airdropmc.listeners;

import com.airdropmc.Crate;
import com.airdropmc.helpers.CrateManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrateCleanupListenerTest {

	private final CrateCleanupListener listener = new CrateCleanupListener();
	private World world;

	@BeforeEach
	void setUp() throws Exception {
		world = mock(World.class);
		clearCrateManager();
	}

	@AfterEach
	void tearDown() throws Exception {
		clearCrateManager();
	}

	@Test
	void onBlockBurn_doesNotRemoveCrate_whenEventCancelled() {
		Location location = new Location(world, 10, 64, 10);
		Block block = blockAt(location);
		BlockBurnEvent event = mock(BlockBurnEvent.class);
		when(event.isCancelled()).thenReturn(true);
		when(event.getBlock()).thenReturn(block);

		Crate crate = mock(Crate.class);
		CrateManager.addCrate(location, crate);

		listener.onBlockBurn(event);

		assertSame(crate, CrateManager.getCrate(location));
		verify(crate, never()).destroy();
	}

	@Test
	void onBlockExplode_doesNotRemoveCrate_whenEventCancelled() {
		Location location = new Location(world, 12, 64, 12);
		Block block = blockAt(location);
		BlockExplodeEvent event = mock(BlockExplodeEvent.class);
		when(event.isCancelled()).thenReturn(true);
		when(event.blockList()).thenReturn(List.of(block));

		Crate crate = mock(Crate.class);
		CrateManager.addCrate(location, crate);

		listener.onBlockExplode(event);

		assertSame(crate, CrateManager.getCrate(location));
		verify(crate, never()).destroy();
	}

	@Test
	void onEntityExplode_doesNotRemoveCrate_whenEventCancelled() {
		Location location = new Location(world, 14, 64, 14);
		Block block = blockAt(location);
		EntityExplodeEvent event = mock(EntityExplodeEvent.class);
		when(event.isCancelled()).thenReturn(true);
		when(event.blockList()).thenReturn(List.of(block));

		Crate crate = mock(Crate.class);
		CrateManager.addCrate(location, crate);

		listener.onEntityExplode(event);

		assertSame(crate, CrateManager.getCrate(location));
		verify(crate, never()).destroy();
	}

	@Test
	void onBlockExplode_removesCrate_whenEventNotCancelled() {
		Location location = new Location(world, 16, 64, 16);
		Block block = blockAt(location);
		BlockExplodeEvent event = mock(BlockExplodeEvent.class);
		when(event.isCancelled()).thenReturn(false);
		when(event.blockList()).thenReturn(List.of(block));

		Crate crate = mock(Crate.class);
		CrateManager.addCrate(location, crate);

		listener.onBlockExplode(event);

		assertNull(CrateManager.getCrate(location));
		verify(crate).destroy();
	}

	private Block blockAt(Location location) {
		Block block = mock(Block.class);
		when(block.getType()).thenReturn(Material.BARREL);
		when(block.getLocation()).thenReturn(location);
		return block;
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
