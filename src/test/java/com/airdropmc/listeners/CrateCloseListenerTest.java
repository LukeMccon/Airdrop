package com.airdropmc.listeners;

import com.airdropmc.Crate;
import com.airdropmc.helpers.CrateManager;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrateCloseListenerTest {

	private final CrateCloseListener listener = new CrateCloseListener();
	private Location barrelLocation;
	private InventoryCloseEvent event;
	private Inventory eventInventory;
	private Inventory currentInventory;
	private Barrel eventBarrel;
	private Barrel currentBarrel;
	private Block block;
	private World world;
	private BlockData barrelData;

	@BeforeEach
	void setUp() {
		CrateManager.clearAll();

		world = mock(World.class);
		when(world.getUID()).thenReturn(UUID.randomUUID());
		block = mock(Block.class);
		eventBarrel = mock(Barrel.class);
		barrelData = mock(BlockData.class);
		currentBarrel = mock(Barrel.class);
		event = mock(InventoryCloseEvent.class);
		currentInventory = mock(Inventory.class);
		eventInventory = currentInventory;
		barrelLocation = new Location(world, 24, 64, 24);

		when(event.getInventory()).thenReturn(eventInventory);
		when(eventInventory.getType()).thenReturn(InventoryType.BARREL);
		when(eventInventory.getHolder()).thenReturn(eventBarrel);

		when(eventBarrel.getBlock()).thenReturn(block);
		when(eventBarrel.getWorld()).thenReturn(world);
		when(eventBarrel.getLocation()).thenReturn(barrelLocation);
		when(eventBarrel.getBlockData()).thenReturn(barrelData);

		when(world.getBlockAt(24, 64, 24)).thenReturn(block);
		when(block.getLocation()).thenReturn(barrelLocation);
		when(block.getType()).thenReturn(Material.BARREL);
		when(block.getState()).thenReturn(currentBarrel);
		when(currentBarrel.getInventory()).thenReturn(currentInventory);
		when(currentInventory.isEmpty()).thenReturn(true);
	}

	@AfterEach
	void tearDown() {
		CrateManager.clearAll();
	}

	@Test
	void onInventoryClose_keepsEmptyBarrel_whenNotTrackedCrate() {
		listener.onInventoryClose(event);

		verify(world, never()).playEffect(barrelLocation, Effect.STEP_SOUND, barrelData);
		verify(currentInventory, never()).isEmpty();
	}

	@Test
	void onInventoryClose_removesCurrentOwnedBarrel_whenFreshInventoryIsEmpty() {
		Crate crate = trackOwnedCrate();

		listener.onInventoryClose(event);

		verify(world).playEffect(barrelLocation, Effect.STEP_SOUND, barrelData);
		verify(currentInventory).isEmpty();
		assertNull(CrateManager.getCrate(barrelLocation));
		verify(crate).destroy();
	}

	@Test
	void onInventoryClose_keepsCurrentOwnedBarrel_whenFreshInventoryHasItems() {
		Crate crate = trackOwnedCrate();
		when(currentInventory.isEmpty()).thenReturn(false);

		listener.onInventoryClose(event);

		assertSame(crate, CrateManager.getCrate(barrelLocation));
		verify(crate, never()).destroy();
		verify(world, never()).playEffect(barrelLocation, Effect.STEP_SOUND, barrelData);
	}

	@Test
	void onInventoryClose_ignoresUnownedEventBarrel_withoutReadingCurrentInventory() {
		Crate crate = mock(Crate.class);
		when(crate.ownsLandedBarrel(eventBarrel)).thenReturn(false);
		when(crate.ownsLandedBarrel(currentBarrel)).thenReturn(true);
		assertTrue(CrateManager.addCrate(barrelLocation, crate));

		listener.onInventoryClose(event);

		assertSame(crate, CrateManager.getCrate(barrelLocation));
		verify(currentInventory, never()).isEmpty();
		verify(crate, never()).destroy();
	}

	@Test
	void onInventoryClose_ignoresReplacedCurrentBarrel_withoutReadingItsInventory() {
		Crate crate = mock(Crate.class);
		when(crate.ownsLandedBarrel(eventBarrel)).thenReturn(true);
		when(crate.ownsLandedBarrel(currentBarrel)).thenReturn(false);
		assertTrue(CrateManager.addCrate(barrelLocation, crate));

		listener.onInventoryClose(event);

		assertSame(crate, CrateManager.getCrate(barrelLocation));
		verify(currentInventory, never()).isEmpty();
		verify(crate, never()).destroy();
	}

	@Test
	void onInventoryClose_staleEventDoesNotRemoveReboundCrate() {
		Crate original = trackOwnedCrate();
		assertSame(original, CrateManager.removeCrate(barrelLocation));
		Crate replacement = mock(Crate.class);
		when(replacement.ownsLandedBarrel(eventBarrel)).thenReturn(false);
		when(replacement.ownsLandedBarrel(currentBarrel)).thenReturn(true);
		assertTrue(CrateManager.addCrate(barrelLocation, replacement));

		listener.onInventoryClose(event);

		assertSame(replacement, CrateManager.getCrate(barrelLocation));
		verify(currentInventory, never()).isEmpty();
		verify(original, never()).destroy();
		verify(replacement, never()).destroy();
	}

	@Test
	void onInventoryClose_ignoresStaleInventoryWhoseHolderResolvesReplacementBarrel() {
		Crate original = trackOwnedCrate();
		assertSame(original, CrateManager.removeCrate(barrelLocation));
		Crate replacement = trackOwnedCrate();
		Inventory staleInventory = mock(Inventory.class);
		when(staleInventory.getType()).thenReturn(InventoryType.BARREL);
		// Paper resolves a retained inventory's holder from the current block position.
		when(staleInventory.getHolder()).thenReturn(eventBarrel);
		when(event.getInventory()).thenReturn(staleInventory);

		listener.onInventoryClose(event);

		assertSame(replacement, CrateManager.getCrate(barrelLocation));
		verify(currentInventory, never()).isEmpty();
		verify(replacement, never()).destroy();
	}

	@Test
	void onInventoryClose_emptyCleanupIsIdempotent() {
		Crate crate = trackOwnedCrate();

		listener.onInventoryClose(event);
		listener.onInventoryClose(event);

		assertNull(CrateManager.getCrate(barrelLocation));
		verify(crate, times(1)).destroy();
		verify(world, times(1)).playEffect(barrelLocation, Effect.STEP_SOUND, barrelData);
	}

	private Crate trackOwnedCrate() {
		Crate crate = mock(Crate.class);
		when(crate.ownsLandedBarrel(eventBarrel)).thenReturn(true);
		when(crate.ownsLandedBarrel(currentBarrel)).thenReturn(true);
		assertTrue(CrateManager.addCrate(barrelLocation, crate));
		return crate;
	}
}
