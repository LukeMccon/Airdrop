package com.airdropmc.listeners;

import com.airdropmc.Crate;
import com.airdropmc.helpers.CrateManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

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
		when(world.getUID()).thenReturn(UUID.randomUUID());
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

	@Test
	void onChunkUnload_removesFallingAndLandedCratesInChunk() {
		Chunk chunk = mock(Chunk.class);
		when(chunk.getWorld()).thenReturn(world);
		when(chunk.getX()).thenReturn(1);
		when(chunk.getZ()).thenReturn(1);
		ChunkUnloadEvent event = mock(ChunkUnloadEvent.class);
		when(event.getChunk()).thenReturn(chunk);

		Location fallingLocation = new Location(world, 17, 80, 17);
		FallingBlock fallingBlock = mock(FallingBlock.class);
		when(fallingBlock.getWorld()).thenReturn(world);
		when(fallingBlock.getLocation()).thenReturn(fallingLocation);
		Crate fallingCrate = mock(Crate.class);
		CrateManager.addCrate(fallingBlock, fallingCrate);

		Location landedLocation = new Location(world, 18, 64, 18);
		Crate landedCrate = mock(Crate.class);
		CrateManager.addCrate(landedLocation, landedCrate);

		listener.onChunkUnload(event);

		assertNull(CrateManager.getCrate(fallingBlock));
		assertNull(CrateManager.getCrate(landedLocation));
		verify(fallingCrate).destroy();
		verify(landedCrate).destroy();
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
