package com.airdropmc.listeners;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import com.airdropmc.Airdrop;
import com.airdropmc.Crate;
import com.airdropmc.config.DropOptions;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLimitSettings;
import com.airdropmc.limits.DropLocationKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrateOpenListenerTest {

	private ServerMock server;
	private WorldMock world;
	private MockPlugin eventPlugin;
	private Airdrop airdropPlugin;
	private DropAdmissionController admission;
	private Block barrelBlock;
	private Location barrelLocation;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("open_world");
		world.loadChunk(0, 0);
		eventPlugin = MockBukkit.createMockPlugin("AirdropOpenHarness");
		airdropPlugin = mock(Airdrop.class);
		when(airdropPlugin.isEnabled()).thenReturn(true);
		when(airdropPlugin.getLogger()).thenReturn(Logger.getLogger("CrateOpenListenerTest"));
		Airdrop.setPluginInstance(airdropPlugin);
		admission = new DropAdmissionController();
		CrateManager.clearAll();

		barrelBlock = world.getBlockAt(8, 64, 8);
		barrelBlock.setType(Material.BARREL);
		barrelLocation = barrelBlock.getLocation();

		server.getPluginManager().registerEvents(new CrateOpenListener(), eventPlugin);
	}

	@AfterEach
	void tearDown() {
		CrateManager.clearAll();
		admission.clear();
		Airdrop.setPluginInstance(null);
		MockBukkit.unmock();
	}

	@Test
	void onInventoryOpen_observesFinalCancellationState() throws Exception {
		Method handlerMethod = CrateOpenListener.class.getMethod(
				"onInventoryOpen", InventoryOpenEvent.class);
		EventHandler handler = handlerMethod.getAnnotation(EventHandler.class);

		assertNotNull(handler);
		assertEquals(EventPriority.MONITOR, handler.priority());
		assertTrue(handler.ignoreCancelled());
	}

	@Test
	void onInventoryOpen_ignoresCancellationAtLowestPriority() {
		server.getPluginManager().registerEvents(new CancelOpenAtLowestListener(), eventPlugin);
		Crate crate = trackOwnedMockCrate();
		InventoryOpenEvent event = openEvent(currentBarrelEventInventory());

		server.getPluginManager().callEvent(event);

		assertTrue(event.isCancelled());
		verify(crate, never()).setOpened(true);
	}

	@Test
	void onInventoryOpen_ignoresCancellationAfterLowPriority() {
		server.getPluginManager().registerEvents(new CancelOpenAtHighestListener(), eventPlugin);
		Crate crate = trackOwnedMockCrate();
		InventoryOpenEvent event = openEvent(currentBarrelEventInventory());

		server.getPluginManager().callEvent(event);

		assertTrue(event.isCancelled());
		verify(crate, never()).setOpened(true);
	}

	@Test
	void onInventoryOpen_ignoresOrdinaryBarrel() {
		server.getPluginManager().callEvent(openEvent(currentBarrelEventInventory()));

		assertEquals(Material.BARREL, barrelBlock.getType());
		assertNull(CrateManager.getCrate(barrelLocation));
	}

	@Test
	void onInventoryOpen_ignoresTrackedCrateBeforeBarrelIdentityIsPublished() throws Exception {
		Crate crate = newCrate(DropOptions.createDefault());
		assertTrue(CrateManager.addCrate(barrelLocation, crate));

		server.getPluginManager().callEvent(openEvent(currentBarrelEventInventory()));

		assertFalse(crate.getOpened());
	}

	@Test
	void onInventoryOpen_ignoresStaleOwnedBarrelAfterBlockReplacement() throws Exception {
		Crate crate = landCrateWithoutEffects();
		Barrel staleBarrel = (Barrel) barrelBlock.getState();
		Inventory staleInventory = eventInventory(staleBarrel);
		assertTrue(crate.ownsLandedBarrel(staleBarrel));
		Barrel replacement = replaceWithFreshBarrel();
		assertFalse(crate.ownsLandedBarrel(replacement));

		server.getPluginManager().callEvent(openEvent(staleInventory));

		assertFalse(crate.getOpened());
	}

	@Test
	void onInventoryOpen_ignoresFreshReplacementBarrelAtTrackedLocation() throws Exception {
		Crate crate = landCrateWithoutEffects();
		Barrel replacement = replaceWithFreshBarrel();
		assertFalse(crate.ownsLandedBarrel(replacement));

		server.getPluginManager().callEvent(openEvent(eventInventory(replacement)));

		assertFalse(crate.getOpened());
	}

	@Test
	void onInventoryOpen_stopsEffectsOnceForRepeatedSuccessfulCurrentOwnedOpens() throws Exception {
		DropOptions options = DropOptions.createDefault()
				.withLandingEffects(false)
				.withContinuousEffects(false)
				.withSmokeEnabled(false)
				.withFlareEffects(false);
		CountingCrate crate = new CountingCrate(
				new Location(world, 8.5, 100, 8.5), world, options, newLease());
		crate.land(barrelBlock);

		Barrel eventBarrel = (Barrel) barrelBlock.getState();
		assertSame(crate, CrateManager.getCrate(barrelLocation));
		assertTrue(crate.ownsLandedBarrel(eventBarrel));
		Inventory inventory = eventInventory(eventBarrel);

		server.getPluginManager().callEvent(openEvent(inventory));
		server.getPluginManager().callEvent(openEvent(inventory));

		assertTrue(crate.getOpened());
		assertEquals(1, crate.stopEffectsCalls());
	}

	private Crate landCrateWithoutEffects() throws Exception {
		DropOptions options = DropOptions.createDefault()
				.withLandingEffects(false)
				.withContinuousEffects(false)
				.withSmokeEnabled(false)
				.withFlareEffects(false);
		Crate crate = newCrate(options);
		crate.land(barrelBlock);
		return crate;
	}

	private Crate newCrate(DropOptions options) throws Exception {
		return new Crate(new Location(world, 8.5, 100, 8.5), world, List.of(), options, newLease());
	}

	private DropAdmissionController.Lease newLease() throws Exception {
		DropAdmissionController.Lease lease = admission.acquireSystem(
				DropLocationKey.from(barrelLocation),
				new DropLimitSettings(Duration.ofSeconds(30), 3, 10, Duration.ofSeconds(600)));
		lease.commitSpawn();
		return lease;
	}

	private Crate trackOwnedMockCrate() {
		Crate crate = mock(Crate.class);
		when(crate.ownsLandedBarrel(any(Barrel.class))).thenReturn(true);
		assertTrue(CrateManager.addCrate(barrelLocation, crate));
		return crate;
	}

	private Inventory currentBarrelEventInventory() {
		return eventInventory((Barrel) barrelBlock.getState());
	}

	private Inventory eventInventory(Barrel barrel) {
		Inventory inventory = mock(Inventory.class);
		when(inventory.getType()).thenReturn(InventoryType.BARREL);
		when(inventory.getHolder()).thenReturn(barrel);
		return inventory;
	}

	private Barrel replaceWithFreshBarrel() {
		barrelBlock.setType(Material.STONE);
		barrelBlock.setType(Material.BARREL);
		return (Barrel) barrelBlock.getState();
	}

	private InventoryOpenEvent openEvent(Inventory inventory) {
		InventoryView view = mock(InventoryView.class);
		when(view.getTopInventory()).thenReturn(inventory);
		return new InventoryOpenEvent(view);
	}

	private static class CountingCrate extends Crate {
		private int stopEffectsCalls;

		private CountingCrate(Location location, WorldMock world, DropOptions options,
				DropAdmissionController.Lease lease) {
			super(location, world, List.of(), options, lease);
		}

		@Override
		public synchronized void stopEffects() {
			stopEffectsCalls++;
			super.stopEffects();
		}

		private int stopEffectsCalls() {
			return stopEffectsCalls;
		}
	}

	private static class CancelOpenAtLowestListener implements Listener {
		@EventHandler(priority = EventPriority.LOWEST)
		public void onInventoryOpen(InventoryOpenEvent e) {
			e.setCancelled(true);
		}
	}

	private static class CancelOpenAtHighestListener implements Listener {
		@EventHandler(priority = EventPriority.HIGHEST)
		public void onInventoryOpen(InventoryOpenEvent e) {
			e.setCancelled(true);
		}
	}
}
