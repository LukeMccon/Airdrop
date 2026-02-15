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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrateCleanupListenerTest {

	private final CrateCleanupListener listener = new CrateCleanupListener();
	private World world;

	@BeforeEach
	void setUp() {
		world = mock(World.class);
		clearCrateManager();
	}

	@AfterEach
	void tearDown() {
		clearCrateManager();
	}

	@Test
	void onBlockExplode_removesCrate_whenEventNotCancelled() {
		Location location = new Location(world, 16, 64, 16);
		Block block = blockAt(location);
		BlockExplodeEvent event = mock(BlockExplodeEvent.class);
		when(event.blockList()).thenReturn(List.of(block));

		Crate crate = mock(Crate.class);
		CrateManager.addCrate(location, crate);

		listener.onBlockExplode(event);

		assertNull(CrateManager.getCrate(location));
		verify(crate).destroy();
	}

	@Test
	void onEntityExplode_removesCrateInAffectedBlocks() {
		Location location = new Location(world, 18, 64, 18);
		Block block = blockAt(location);
		EntityExplodeEvent event = mock(EntityExplodeEvent.class);
		when(event.blockList()).thenReturn(List.of(block));

		Crate crate = mock(Crate.class);
		CrateManager.addCrate(location, crate);

		listener.onEntityExplode(event);

		assertNull(CrateManager.getCrate(location));
		verify(crate).destroy();
	}

	@Test
	void onBlockBurn_removesTrackedBarrelCrate() {
		Location location = new Location(world, 20, 64, 20);
		Block block = blockAt(location);
		BlockBurnEvent event = mock(BlockBurnEvent.class);
		when(event.getBlock()).thenReturn(block);

		Crate crate = mock(Crate.class);
		CrateManager.addCrate(location, crate);

		listener.onBlockBurn(event);

		assertNull(CrateManager.getCrate(location));
		verify(crate).destroy();
	}

	private Block blockAt(Location location) {
		Block block = mock(Block.class);
		when(block.getType()).thenReturn(Material.BARREL);
		when(block.getLocation()).thenReturn(location);
		return block;
	}

	private void clearCrateManager() {
		CrateManager.clearAll();
	}
}
