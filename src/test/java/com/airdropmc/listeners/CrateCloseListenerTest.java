package com.airdropmc.listeners;

import com.airdropmc.Crate;
import com.airdropmc.helpers.CrateManager;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrateCloseListenerTest {

	private final CrateCloseListener listener = new CrateCloseListener();
	private Location barrelLocation;
	private InventoryCloseEvent event;
	private Inventory eventInventory;
	private Inventory barrelInventory;
	private Barrel barrel;
	private Block block;
	private World world;

	@BeforeEach
	void setUp() {
		clearCrateManager();

		world = mock(World.class);
		when(world.getUID()).thenReturn(UUID.randomUUID());
		block = mock(Block.class);
		barrel = mock(Barrel.class);
		event = mock(InventoryCloseEvent.class);
		eventInventory = mock(Inventory.class);
		barrelInventory = mock(Inventory.class);
		barrelLocation = new Location(world, 24, 64, 24);

		when(event.getInventory()).thenReturn(eventInventory);
		when(eventInventory.getType()).thenReturn(InventoryType.BARREL);
		when(eventInventory.getHolder()).thenReturn(barrel);

		when(barrel.getBlock()).thenReturn(block);
		when(barrel.getInventory()).thenReturn(barrelInventory);
		when(barrel.getWorld()).thenReturn(world);
		when(barrel.getLocation()).thenReturn(barrelLocation);

		when(block.getLocation()).thenReturn(barrelLocation);
		when(barrelInventory.isEmpty()).thenReturn(true);
	}

	@AfterEach
	void tearDown() {
		clearCrateManager();
	}

	@Test
	void onInventoryClose_keepsEmptyBarrel_whenNotTrackedCrate() {
		listener.onInventoryClose(event);

		verify(world, never()).playEffect(barrelLocation, Effect.STEP_SOUND, Material.BARREL);
		verify(block, never()).setType(Material.AIR);
	}

	@Test
	void onInventoryClose_removesEmptyBarrel_whenTrackedCrate() {
		Crate crate = mock(Crate.class);
		CrateManager.addCrate(barrelLocation, crate);

		listener.onInventoryClose(event);

		verify(world).playEffect(barrelLocation, Effect.STEP_SOUND, Material.BARREL);
		verify(block, never()).setType(Material.AIR);
		assertNull(CrateManager.getCrate(barrelLocation));
		verify(crate).destroy();
	}

	private void clearCrateManager() {
		CrateManager.clearAll();
	}
}
