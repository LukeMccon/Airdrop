package com.airdropmc.listeners;

import com.airdropmc.Airdrop;
import com.airdropmc.Crate;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLimitSettings;
import com.airdropmc.limits.DropLocationKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrateCleanupListenerTest {

	private Airdrop plugin;
	private CrateCleanupListener listener;
	private World world;

	@BeforeEach
	void setUp() {
		plugin = mock(Airdrop.class);
		listener = new CrateCleanupListener(plugin);
		world = mock(World.class);
		when(world.getUID()).thenReturn(UUID.randomUUID());
		Airdrop.setPluginInstance(null);
		clearCrateManager();
	}

	@AfterEach
	void tearDown() {
		clearCrateManager();
		Airdrop.setPluginInstance(null);
	}

	@Test
	void onBlockExplode_defersCleanupAndDetachesAfterOwnedBarrelIsDestroyed() {
		Location location = new Location(world, 16, 64, 16);
		Crate crate = mock(Crate.class);
		Barrel ownedBarrel = mock(Barrel.class);
		Block block = blockAt(location);
		when(block.getState()).thenReturn(ownedBarrel, mock(BlockState.class));
		when(crate.ownsLandedBarrel(ownedBarrel)).thenReturn(true);
		BlockExplodeEvent event = mock(BlockExplodeEvent.class);
		when(event.blockList()).thenReturn(List.of(block));
		CrateManager.addCrate(location, crate);

		List<Runnable> reconciliations = captureScheduledTasks(() -> listener.onBlockExplode(event));

		assertSame(crate, CrateManager.getCrate(location));
		verify(crate, never()).detachLandedBarrel();
		verify(crate, never()).destroy();
		assertEquals(1, reconciliations.size());

		reconciliations.getFirst().run();

		assertNull(CrateManager.getCrate(location));
		verify(crate).detachLandedBarrel();
		verify(crate, never()).destroy();
	}

	@Test
	void onEntityExplode_detachesAfterOwnedBarrelIsReplaced() {
		Location location = new Location(world, 18, 64, 18);
		Crate crate = mock(Crate.class);
		Barrel ownedBarrel = mock(Barrel.class);
		Barrel replacementBarrel = mock(Barrel.class);
		Block block = blockAt(location);
		when(block.getState()).thenReturn(ownedBarrel, replacementBarrel);
		when(crate.ownsLandedBarrel(ownedBarrel)).thenReturn(true);
		EntityExplodeEvent event = mock(EntityExplodeEvent.class);
		when(event.blockList()).thenReturn(List.of(block));
		CrateManager.addCrate(location, crate);

		List<Runnable> reconciliations = captureScheduledTasks(() -> listener.onEntityExplode(event));
		reconciliations.getFirst().run();

		assertNull(CrateManager.getCrate(location));
		verify(crate).detachLandedBarrel();
		verify(crate, never()).destroy();
	}

	@Test
	void explosionReconciliationKeepsOwnedBarrelWhenExplosionIsCancelledLater() {
		Location location = new Location(world, 20, 64, 20);
		Crate crate = mock(Crate.class);
		Barrel ownedBarrel = mock(Barrel.class);
		Block block = blockAt(location);
		when(block.getState()).thenReturn(ownedBarrel);
		when(crate.ownsLandedBarrel(ownedBarrel)).thenReturn(true);
		BlockExplodeEvent event = mock(BlockExplodeEvent.class);
		when(event.blockList()).thenReturn(List.of(block));
		CrateManager.addCrate(location, crate);

		List<Runnable> reconciliations = captureScheduledTasks(() -> listener.onBlockExplode(event));
		reconciliations.getFirst().run();

		assertSame(crate, CrateManager.getCrate(location));
		verify(block, never()).setType(any(Material.class));
		verify(crate, never()).detachLandedBarrel();
		verify(crate, never()).destroy();
	}

	@Test
	void overlappingExplosionSignalsDetachExpectedCrateOnlyOnce() {
		Location location = new Location(world, 22, 64, 22);
		Crate crate = mock(Crate.class);
		Barrel ownedBarrel = mock(Barrel.class);
		BlockState destroyedState = mock(BlockState.class);
		Block block = blockAt(location);
		when(block.getState()).thenReturn(ownedBarrel, ownedBarrel, destroyedState, destroyedState);
		when(crate.ownsLandedBarrel(ownedBarrel)).thenReturn(true);
		BlockExplodeEvent event = mock(BlockExplodeEvent.class);
		when(event.blockList()).thenReturn(List.of(block));
		CrateManager.addCrate(location, crate);

		List<Runnable> reconciliations = captureScheduledTasks(() -> {
			listener.onBlockExplode(event);
			listener.onBlockExplode(event);
		});
		assertEquals(2, reconciliations.size());

		reconciliations.forEach(Runnable::run);

		assertNull(CrateManager.getCrate(location));
		verify(crate, times(1)).detachLandedBarrel();
		verify(crate, never()).destroy();
	}

	@Test
	void delayedExplosionSignalDoesNotDetachReplacementCrateAtSameLocation() {
		Location location = new Location(world, 24, 64, 24);
		Crate expectedCrate = mock(Crate.class);
		Barrel ownedBarrel = mock(Barrel.class);
		Block block = blockAt(location);
		when(block.getState()).thenReturn(ownedBarrel);
		when(expectedCrate.ownsLandedBarrel(ownedBarrel)).thenReturn(true);
		BlockExplodeEvent event = mock(BlockExplodeEvent.class);
		when(event.blockList()).thenReturn(List.of(block));
		CrateManager.addCrate(location, expectedCrate);

		List<Runnable> reconciliations = captureScheduledTasks(() -> listener.onBlockExplode(event));
		CrateManager.removeCrateAndDetach(location);
		Crate replacementCrate = mock(Crate.class);
		CrateManager.addCrate(location, replacementCrate);

		reconciliations.getFirst().run();

		assertSame(replacementCrate, CrateManager.getCrate(location));
		verify(expectedCrate, times(1)).detachLandedBarrel();
		verify(replacementCrate, never()).detachLandedBarrel();
		verify(replacementCrate, never()).destroy();
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
		DropAdmissionController admission = new DropAdmissionController();
		DropLimitSettings limits = new DropLimitSettings(
				Duration.ofSeconds(30), 3, 10, Duration.ofSeconds(600));
		DropAdmissionController.Lease fallingLease;
		DropAdmissionController.Lease landedLease;
		try {
			fallingLease = admission.acquireSystem(DropLocationKey.from(fallingLocation), limits);
			fallingLease.commitSpawn();
			landedLease = admission.acquireSystem(
					DropLocationKey.from(new Location(world, 18, 64, 18)), limits);
			landedLease.commitSpawn();
			landedLease.markLanded();
		} catch (Exception failure) {
			throw new AssertionError(failure);
		}
		Crate fallingCrate = mock(Crate.class);
		doAnswer(invocation -> {
			fallingLease.close();
			return null;
		}).when(fallingCrate).destroy();
		CrateManager.addCrate(fallingBlock, fallingCrate);

		Location landedLocation = new Location(world, 18, 64, 18);
		Crate landedCrate = mock(Crate.class);
		doAnswer(invocation -> {
			landedLease.close();
			return null;
		}).when(landedCrate).destroy();
		CrateManager.addCrate(landedLocation, landedCrate);

		listener.onChunkUnload(event);

		assertNull(CrateManager.getCrate(fallingBlock));
		assertNull(CrateManager.getCrate(landedLocation));
		verify(fallingCrate).destroy();
		verify(landedCrate).destroy();
		assertEquals(new DropAdmissionController.Snapshot(0, 0, 0, 0, 0, true), admission.snapshot());
	}

	@Test
	void onWorldUnload_removesAllCratesAndReleasesTheirLeases() throws Exception {
		DropAdmissionController admission = new DropAdmissionController();
		DropLimitSettings limits = new DropLimitSettings(
				Duration.ofSeconds(30), 3, 10, Duration.ofSeconds(600));
		Location fallingLocation = new Location(world, 1, 80, 1);
		FallingBlock fallingBlock = mock(FallingBlock.class);
		when(fallingBlock.getWorld()).thenReturn(world);
		when(fallingBlock.getLocation()).thenReturn(fallingLocation);
		DropAdmissionController.Lease fallingLease = admission.acquireSystem(
				DropLocationKey.from(fallingLocation), limits);
		fallingLease.commitSpawn();
		Crate fallingCrate = mock(Crate.class);
		doAnswer(invocation -> {
			fallingLease.close();
			return null;
		}).when(fallingCrate).destroy();
		CrateManager.addCrate(fallingBlock, fallingCrate);

		Location landedLocation = new Location(world, 32, 64, 32);
		DropAdmissionController.Lease landedLease = admission.acquireSystem(
				DropLocationKey.from(landedLocation), limits);
		landedLease.commitSpawn();
		landedLease.markLanded();
		Crate landedCrate = mock(Crate.class);
		doAnswer(invocation -> {
			landedLease.close();
			return null;
		}).when(landedCrate).destroy();
		CrateManager.addCrate(landedLocation, landedCrate);

		Airdrop.setPluginInstance(mock(Airdrop.class));
		WorldUnloadEvent event = mock(WorldUnloadEvent.class);
		when(event.getWorld()).thenReturn(world);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
			listener.onWorldUnload(event);
		}

		assertNull(CrateManager.getCrate(fallingBlock));
		assertNull(CrateManager.getCrate(landedLocation));
		assertEquals(new DropAdmissionController.Snapshot(0, 0, 0, 0, 0, true), admission.snapshot());
	}

	private Block blockAt(Location location) {
		Block block = mock(Block.class);
		when(block.getType()).thenReturn(Material.BARREL);
		when(block.getLocation()).thenReturn(location);
		when(world.getBlockAt(location)).thenReturn(block);
		return block;
	}

	private List<Runnable> captureScheduledTasks(Runnable action) {
		List<Runnable> tasks = new ArrayList<>();
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
			tasks.add(invocation.getArgument(1));
			return mock(BukkitTask.class);
		});
		try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
			action.run();
		}
		return tasks;
	}

	private void clearCrateManager() {
		CrateManager.clearAll();
	}
}
