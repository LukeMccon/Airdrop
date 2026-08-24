package com.airdropmc;

import com.airdropmc.config.DropOptions;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLimitSettings;
import com.airdropmc.limits.DropLocationKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class CrateDestroyTest {

	@AfterEach
	void tearDown() throws Exception {
		clearCrateManager();
		Airdrop.setPluginInstance(null);
	}

	@Test
	void destroy_removesFallingCrateAndRestoresGravity_whenEntityStillAlive() throws Exception {
		World world = mock(World.class);
		when(world.getUID()).thenReturn(UUID.randomUUID());
		FallingBlock fallingBlock = mock(FallingBlock.class);
		when(fallingBlock.isDead()).thenReturn(false);
		DropAdmissionController admission = new DropAdmissionController();
		DropAdmissionController.Lease lease = acquireFallingLease(admission, world);

		Crate crate = new Crate(new Location(world, 100, 100, 100), world,
				List.of(), DropOptions.createDefault(), lease);
		setFallingCrate(crate, fallingBlock);

		crate.destroy();
		crate.destroy();

		verify(fallingBlock, times(1)).setGravity(true);
		verify(fallingBlock, times(1)).remove();
		assertSnapshotEmpty(admission);
	}

	@Test
	void destroy_skipsEntityRemoval_whenFallingCrateAlreadyDead() throws Exception {
		World world = mock(World.class);
		when(world.getUID()).thenReturn(UUID.randomUUID());
		FallingBlock fallingBlock = mock(FallingBlock.class);
		when(fallingBlock.isDead()).thenReturn(true);
		DropAdmissionController admission = new DropAdmissionController();
		DropAdmissionController.Lease lease = acquireFallingLease(admission, world);

		Crate crate = new Crate(new Location(world, 100, 100, 100), world,
				List.of(), DropOptions.createDefault(), lease);
		setFallingCrate(crate, fallingBlock);

		crate.destroy();

		verify(fallingBlock, never()).setGravity(true);
		verify(fallingBlock, never()).remove();
		assertSnapshotEmpty(admission);
	}

	@Test
	void destroy_continuesCleanupAndClosesLeaseWhenEntityCleanupThrows() throws Exception {
		World world = mock(World.class);
		when(world.getUID()).thenReturn(UUID.randomUUID());
		FallingBlock fallingBlock = mock(FallingBlock.class);
		when(fallingBlock.isDead()).thenReturn(false);
		org.mockito.Mockito.doThrow(new IllegalStateException("entity cleanup failed"))
				.when(fallingBlock).setGravity(true);
		ParachuteSystem parachuteSystem = mock(ParachuteSystem.class);
		DropAdmissionController admission = new DropAdmissionController();
		DropAdmissionController.Lease lease = acquireFallingLease(admission, world);
		Crate crate = new Crate(new Location(world, 100, 100, 100), world,
				List.of(), DropOptions.createDefault(), lease);
		setFallingCrate(crate, fallingBlock);
		setField(crate, "parachuteSystem", parachuteSystem);

		crate.destroy();

		verify(fallingBlock).remove();
		verify(parachuteSystem).cancel();
		assertSnapshotEmpty(admission);
	}

	@Test
	void land_dropsOverflowItemsWhenBarrelIsFull() throws Exception {
		World world = mock(World.class);
		when(world.getUID()).thenReturn(UUID.randomUUID());

		Block block = mock(Block.class);
		Barrel barrel = mock(Barrel.class);
		Inventory inventory = mock(Inventory.class);
		PersistentDataContainer persistentData = mock(PersistentDataContainer.class);
		Location landedLocation = new Location(world, 10, 64, 10);
		ItemStack overflowStack = new ItemStack(Material.DIRT, 1);
		Map<Integer, ItemStack> overflowMap = new HashMap<>();
		overflowMap.put(0, overflowStack);

		when(block.getLocation()).thenReturn(landedLocation);
		when(block.getState()).thenReturn(barrel);
		when(barrel.getInventory()).thenReturn(inventory);
		when(barrel.getLocation()).thenReturn(landedLocation);
		when(barrel.getPersistentDataContainer()).thenReturn(persistentData);
		when(barrel.update(true, false)).thenReturn(true);
		when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>(overflowMap));
		Airdrop plugin = mock(Airdrop.class);
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("CrateDestroyTest"));
		Airdrop.setPluginInstance(plugin);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(scheduler.runTaskLater(any(), any(Runnable.class), org.mockito.ArgumentMatchers.anyLong()))
				.thenReturn(mock(BukkitTask.class));

		DropAdmissionController admission = new DropAdmissionController();
		DropAdmissionController.Lease lease = admission.acquireSystem(
				DropLocationKey.from(landedLocation),
				new DropLimitSettings(Duration.ofSeconds(30), 3, 10, Duration.ofSeconds(600)));
		lease.commitSpawn();
		DropOptions options = DropOptions.createDefault()
				.withLandingEffects(false)
				.withContinuousEffects(false)
				.withSmokeEnabled(false);
		Crate crate = new Crate(new Location(world, 10, 100, 10), world,
				List.of(new ItemStack(Material.STONE, 1)), options, lease);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
			bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
			crate.land(block);
		}

		verify(persistentData).set(any(NamespacedKey.class), eq(PersistentDataType.STRING), anyString());
		verify(barrel).update(true, false);
		verify(world).dropItemNaturally(any(Location.class), any(ItemStack.class));
	}

	private void setFallingCrate(Crate crate, FallingBlock fallingBlock) throws Exception {
		setField(crate, "fallingCrate", fallingBlock);
	}

	private void setField(Crate crate, String fieldName, Object value) throws Exception {
		Field field = Crate.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(crate, value);
	}

	private DropAdmissionController.Lease acquireFallingLease(DropAdmissionController admission, World world)
			throws Exception {
		DropAdmissionController.Lease lease = admission.acquireSystem(
				new DropLocationKey(world.getUID(), 100, 65, 100),
				new DropLimitSettings(Duration.ofSeconds(30), 3, 10, Duration.ofSeconds(600)));
		lease.commitSpawn();
		return lease;
	}

	private void assertSnapshotEmpty(DropAdmissionController admission) {
		org.junit.jupiter.api.Assertions.assertEquals(0, admission.snapshot().falling());
		org.junit.jupiter.api.Assertions.assertEquals(0, admission.snapshot().landedClaims());
		org.junit.jupiter.api.Assertions.assertEquals(0, admission.snapshot().locations());
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
