package com.airdropmc.listeners;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrateHopperListenerTest {

	private ServerMock server;
	private WorldMock world;
	private MockPlugin plugin;
	private DropAdmissionController admission;
	private Block barrelBlock;
	private Location barrelLocation;
	private Inventory barrelInventory;
	private Inventory hopperInventory;
	private Queue<Runnable> nextTickTasks;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("hopper_world");
		world.loadChunk(0, 0);
		plugin = MockBukkit.createMockPlugin("AirdropHopperHarness");
		admission = new DropAdmissionController();
		CrateManager.clearAll();
		Airdrop.setPluginInstance(null);

		barrelBlock = world.getBlockAt(8, 64, 8);
		barrelBlock.setType(Material.BARREL);
		barrelLocation = barrelBlock.getLocation();
		barrelInventory = ((Barrel) barrelBlock.getState()).getInventory();
		hopperInventory = Bukkit.createInventory(null, InventoryType.HOPPER);
		nextTickTasks = new ArrayDeque<>();

		server.getPluginManager().registerEvents(new CrateHopperListener(nextTickTasks::add), plugin);
	}

	@AfterEach
	void tearDown() {
		CrateManager.clearAll();
		admission.clear();
		Airdrop.setPluginInstance(null);
		MockBukkit.unmock();
	}

	@Test
	void onInventoryMoveItem_observesFinalCancellationState() throws Exception {
		Method handlerMethod = CrateHopperListener.class.getMethod(
				"onInventoryMoveItem", InventoryMoveItemEvent.class);
		EventHandler handler = handlerMethod.getAnnotation(EventHandler.class);

		assertNotNull(handler);
		assertEquals(EventPriority.MONITOR, handler.priority());
		assertTrue(handler.ignoreCancelled());
	}

	@Test
	void onInventoryMoveItem_removesTrackedCrateOnNextTick_whenLastItemExtracted() {
		Crate crate = trackMockCrate();
		ItemStack item = new ItemStack(Material.DIAMOND);
		barrelInventory.setItem(0, item);
		barrelInventory.clear();

		server.getPluginManager().callEvent(extractionEvent(item));

		assertSame(crate, CrateManager.getCrate(barrelLocation));
		verify(crate, never()).destroy();
		assertEquals(1, nextTickTasks.size());

		runNextTickTasks();

		assertNull(CrateManager.getCrate(barrelLocation));
		verify(crate).destroy();
	}

	@Test
	void onInventoryMoveItem_rechecksFreshBarrelInventoryInsteadOfEventSourceReference() {
		Crate crate = trackMockCrate();
		Inventory staleEventSource = mock(Inventory.class);
		when(staleEventSource.getType()).thenReturn(InventoryType.BARREL);
		when(staleEventSource.getLocation()).thenReturn(barrelLocation);
		when(staleEventSource.isEmpty()).thenReturn(true, false);
		ItemStack item = new ItemStack(Material.DIAMOND);

		server.getPluginManager().callEvent(new InventoryMoveItemEvent(
				staleEventSource, item, hopperInventory, false));
		runNextTickTasks();

		assertTrue(barrelInventory.isEmpty());
		assertNull(CrateManager.getCrate(barrelLocation));
		verify(crate).destroy();
	}

	@Test
	void onInventoryMoveItem_removesBarrelAndReleasesLease_whenLastItemExtracted() throws Exception {
		ItemStack item = new ItemStack(Material.DIAMOND);
		Crate crate = landRealCrate(List.of(item));
		Barrel landedBarrel = (Barrel) barrelBlock.getState();
		Inventory landedInventory = landedBarrel.getInventory();
		assertTrue(crate.ownsLandedBarrel(landedBarrel));
		landedInventory.clear();

		server.getPluginManager().callEvent(extractionEvent(item, landedInventory));
		runNextTickTasks();

		assertEquals(Material.AIR, barrelBlock.getType());
		assertNull(CrateManager.getCrate(barrelLocation));
		assertEquals(0, admission.snapshot().landedClaims());
	}

	@Test
	void onInventoryMoveItem_preservesFreshEmptyBarrel_whenSourceBlockIsReplacedBeforeNextTick() throws Exception {
		ItemStack item = new ItemStack(Material.DIAMOND);
		Crate crate = landRealCrate(List.of(item));
		Inventory originalInventory = ((Barrel) barrelBlock.getState()).getInventory();
		originalInventory.clear();

		server.getPluginManager().callEvent(extractionEvent(item, originalInventory));
		Barrel replacement = replaceWithFreshBarrel();
		assertFalse(crate.ownsLandedBarrel(replacement));
		runNextTickTasks();

		assertEquals(Material.BARREL, barrelBlock.getType());
		assertTrue(((Barrel) barrelBlock.getState()).getInventory().isEmpty());
		assertNull(CrateManager.getCrate(barrelLocation));
		assertEquals(0, admission.snapshot().landedClaims());
	}

	@Test
	void onInventoryMoveItem_preservesFreshNonEmptyBarrel_whenSourceBlockIsReplacedBeforeNextTick() throws Exception {
		ItemStack item = new ItemStack(Material.DIAMOND);
		Crate crate = landRealCrate(List.of(item));
		Inventory originalInventory = ((Barrel) barrelBlock.getState()).getInventory();
		originalInventory.clear();

		server.getPluginManager().callEvent(extractionEvent(item, originalInventory));
		Barrel replacement = replaceWithFreshBarrel();
		ItemStack replacementItem = new ItemStack(Material.EMERALD);
		replacement.getInventory().setItem(0, replacementItem);
		assertFalse(crate.ownsLandedBarrel(replacement));
		runNextTickTasks();

		assertEquals(Material.BARREL, barrelBlock.getType());
		assertEquals(replacementItem, ((Barrel) barrelBlock.getState()).getInventory().getItem(0));
		assertNull(CrateManager.getCrate(barrelLocation));
		assertEquals(0, admission.snapshot().landedClaims());
	}

	@Test
	void onInventoryMoveItem_releasesCrateResources_whenSourceBlockBecomesNonBarrel() throws Exception {
		ItemStack item = new ItemStack(Material.DIAMOND);
		landRealCrate(List.of(item));
		Inventory originalInventory = ((Barrel) barrelBlock.getState()).getInventory();
		originalInventory.clear();

		server.getPluginManager().callEvent(extractionEvent(item, originalInventory));
		barrelBlock.setType(Material.STONE);
		runNextTickTasks();

		assertEquals(Material.STONE, barrelBlock.getType());
		assertNull(CrateManager.getCrate(barrelLocation));
		assertEquals(0, admission.snapshot().landedClaims());
	}

	@Test
	void onInventoryMoveItem_keepsTrackedCrate_whenPartialExtractionLeavesItems() {
		Crate crate = trackMockCrate();
		ItemStack extracted = new ItemStack(Material.DIAMOND);
		barrelInventory.setItem(0, extracted);
		barrelInventory.setItem(1, new ItemStack(Material.EMERALD));
		barrelInventory.setItem(0, null);

		server.getPluginManager().callEvent(extractionEvent(extracted));

		assertTrue(nextTickTasks.isEmpty());

		assertSame(crate, CrateManager.getCrate(barrelLocation));
		verify(crate, never()).destroy();
	}

	@Test
	void onInventoryMoveItem_keepsTrackedCrate_whenUncancelledTransferIsRestored() {
		Crate crate = trackMockCrate();
		ItemStack item = new ItemStack(Material.DIAMOND);
		barrelInventory.setItem(0, item);
		barrelInventory.clear();

		server.getPluginManager().callEvent(extractionEvent(item));
		barrelInventory.setItem(0, item);
		runNextTickTasks();

		assertFalse(barrelInventory.isEmpty());
		assertSame(crate, CrateManager.getCrate(barrelLocation));
		verify(crate, never()).destroy();
	}

	@Test
	void onInventoryMoveItem_ignoresCancelledExtraction() {
		server.getPluginManager().registerEvents(new CancelMoveListener(), plugin);
		Crate crate = trackMockCrate();
		ItemStack item = new ItemStack(Material.DIAMOND);
		barrelInventory.setItem(0, item);
		InventoryMoveItemEvent event = extractionEvent(item);

		server.getPluginManager().callEvent(event);
		barrelInventory.clear();

		assertTrue(event.isCancelled());
		assertTrue(nextTickTasks.isEmpty());
		assertSame(crate, CrateManager.getCrate(barrelLocation));
		verify(crate, never()).destroy();
	}

	@Test
	void onInventoryMoveItem_ignoresInsertionIntoTrackedCrate() {
		Crate crate = trackMockCrate();
		ItemStack item = new ItemStack(Material.DIAMOND);
		hopperInventory.setItem(0, item);

		server.getPluginManager().callEvent(new InventoryMoveItemEvent(
				hopperInventory, item, barrelInventory, true));

		assertTrue(barrelInventory.isEmpty());
		assertTrue(nextTickTasks.isEmpty());
		assertSame(crate, CrateManager.getCrate(barrelLocation));
		verify(crate, never()).destroy();
	}

	@Test
	void onInventoryMoveItem_cleansUpOnlyOnce_whenMultipleMovesOccurInOneTick() {
		Crate crate = trackMockCrate();
		ItemStack first = new ItemStack(Material.DIAMOND);
		ItemStack second = new ItemStack(Material.EMERALD);
		barrelInventory.setItem(0, first);
		barrelInventory.setItem(1, second);
		barrelInventory.clear();

		server.getPluginManager().callEvent(extractionEvent(first));
		server.getPluginManager().callEvent(extractionEvent(second));
		assertEquals(2, nextTickTasks.size());
		runNextTickTasks();

		assertNull(CrateManager.getCrate(barrelLocation));
		verify(crate, times(1)).destroy();
	}

	@Test
	void onInventoryMoveItem_ignoresUntrackedBarrel() {
		ItemStack item = new ItemStack(Material.DIAMOND);
		barrelInventory.setItem(0, item);

		server.getPluginManager().callEvent(extractionEvent(item));
		barrelInventory.clear();

		assertEquals(Material.BARREL, barrelBlock.getType());
		assertTrue(nextTickTasks.isEmpty());
		assertNull(CrateManager.getCrate(barrelLocation));
	}

	@Test
	void onInventoryMoveItem_doesNotRemoveReplacementCrate() {
		Crate original = trackMockCrate();
		Crate replacement = mock(Crate.class);
		ItemStack item = new ItemStack(Material.DIAMOND);
		barrelInventory.setItem(0, item);
		barrelInventory.clear();

		server.getPluginManager().callEvent(extractionEvent(item));
		assertSame(original, CrateManager.removeCrate(barrelLocation));
		assertTrue(CrateManager.addCrate(barrelLocation, replacement));
		barrelInventory.clear();
		runNextTickTasks();

		assertSame(replacement, CrateManager.getCrate(barrelLocation));
		verify(original, never()).destroy();
		verify(replacement, never()).destroy();
	}

	private Crate landRealCrate(List<ItemStack> contents) throws Exception {
		Airdrop airdrop = mock(Airdrop.class);
		when(airdrop.isEnabled()).thenReturn(true);
		when(airdrop.getLogger()).thenReturn(Logger.getLogger("CrateHopperListenerTest"));
		Airdrop.setPluginInstance(airdrop);
		DropAdmissionController.Lease lease = admission.acquireSystem(
				DropLocationKey.from(barrelLocation),
				new DropLimitSettings(Duration.ofSeconds(30), 3, 10, Duration.ofSeconds(600)));
		lease.commitSpawn();
		DropOptions options = DropOptions.createDefault()
				.withLandingEffects(false)
				.withContinuousEffects(false)
				.withSmokeEnabled(false)
				.withFlareEffects(false);
		Crate crate = new Crate(
				new Location(world, 8.5, 100, 8.5), world, contents, options, lease);
		crate.land(barrelBlock);
		return crate;
	}

	private Barrel replaceWithFreshBarrel() {
		barrelBlock.setType(Material.STONE);
		barrelBlock.setType(Material.BARREL);
		return (Barrel) barrelBlock.getState();
	}

	private Crate trackMockCrate() {
		Crate crate = mock(Crate.class);
		when(crate.ownsLandedBarrel(any(Barrel.class))).thenReturn(true);
		assertTrue(CrateManager.addCrate(barrelLocation, crate));
		return crate;
	}

	private InventoryMoveItemEvent extractionEvent(ItemStack item) {
		return extractionEvent(item, barrelInventory);
	}

	private InventoryMoveItemEvent extractionEvent(ItemStack item, Inventory contents) {
		Inventory source = mock(Inventory.class);
		when(source.getType()).thenReturn(InventoryType.BARREL);
		when(source.getLocation()).thenReturn(barrelLocation);
		when(source.isEmpty()).thenAnswer(ignored -> contents.isEmpty());
		return new InventoryMoveItemEvent(source, item, hopperInventory, false);
	}

	private void runNextTickTasks() {
		List<Runnable> tasks = List.copyOf(nextTickTasks);
		nextTickTasks.clear();
		tasks.forEach(Runnable::run);
		assertTrue(nextTickTasks.isEmpty(), "Cleanup must not leave follow-up work queued");
	}

	private static class CancelMoveListener implements Listener {
		@EventHandler(priority = EventPriority.HIGHEST)
		public void onInventoryMoveItem(InventoryMoveItemEvent e) {
			e.setCancelled(true);
		}
	}
}
