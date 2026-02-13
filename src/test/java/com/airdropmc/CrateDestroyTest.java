package com.airdropmc;

import com.airdropmc.config.DropOptions;
import com.airdropmc.helpers.CrateManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.FallingBlock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrateDestroyTest {

	@AfterEach
	void tearDown() throws Exception {
		clearCrateManager();
	}

	@Test
	void destroy_removesFallingCrateAndRestoresGravity_whenEntityStillAlive() throws Exception {
		World world = mock(World.class);
		FallingBlock fallingBlock = mock(FallingBlock.class);
		when(fallingBlock.isDead()).thenReturn(false);

		Crate crate = new Crate(new Location(world, 100, 100, 100), world, List.of(), DropOptions.createDefault());
		setFallingCrate(crate, fallingBlock);

		crate.destroy();

		verify(fallingBlock).setGravity(true);
		verify(fallingBlock).remove();
	}

	@Test
	void destroy_skipsEntityRemoval_whenFallingCrateAlreadyDead() throws Exception {
		World world = mock(World.class);
		FallingBlock fallingBlock = mock(FallingBlock.class);
		when(fallingBlock.isDead()).thenReturn(true);

		Crate crate = new Crate(new Location(world, 100, 100, 100), world, List.of(), DropOptions.createDefault());
		setFallingCrate(crate, fallingBlock);

		crate.destroy();

		verify(fallingBlock, never()).setGravity(true);
		verify(fallingBlock, never()).remove();
	}

	@Test
	void land_dropsOverflowItemsWhenBarrelIsFull() throws Exception {
		World world = mock(World.class);
		when(world.getUID()).thenReturn(UUID.randomUUID());

		Block block = mock(Block.class);
		Barrel barrel = mock(Barrel.class);
		Inventory inventory = mock(Inventory.class);
		Location landedLocation = new Location(world, 10, 64, 10);
		ItemStack overflowStack = new ItemStack(Material.DIRT, 1);
		Map<Integer, ItemStack> overflowMap = new HashMap<>();
		overflowMap.put(0, overflowStack);

		when(block.getLocation()).thenReturn(landedLocation);
		when(block.getState()).thenReturn(barrel);
		when(barrel.getInventory()).thenReturn(inventory);
		when(barrel.getLocation()).thenReturn(landedLocation);
		when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>(overflowMap));

		Crate crate = new Crate(new Location(world, 10, 100, 10), world,
				List.of(new ItemStack(Material.STONE, 1)), DropOptions.createDefault());

		crate.land(block);

		verify(world).dropItemNaturally(any(Location.class), any(ItemStack.class));
	}

	private void setFallingCrate(Crate crate, FallingBlock fallingBlock) throws Exception {
		Field fallingCrateField = Crate.class.getDeclaredField("fallingCrate");
		fallingCrateField.setAccessible(true);
		fallingCrateField.set(crate, fallingBlock);
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
