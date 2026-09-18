package com.airdropmc.api.event;

import com.airdropmc.Airdrop;
import com.airdropmc.Crate;
import com.airdropmc.api.AirdropApi;
import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.api.LandedAirdropView;
import com.airdropmc.api.RetirementReason;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.packages.PackageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExpectedCrateRetirementTest {

	private ServerMock server;
	private WorldMock world;
	private AirdropApi api;
	private RetirementRecorder recorder;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("expected_retirement_world");
		Airdrop plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(plugin.getDataFolder().toPath());
		Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"),
				"language: en\neconomy:\n  enabled: false\n", StandardCharsets.UTF_8);
		Files.writeString(plugin.getDataFolder().toPath().resolve("packages.yml"),
				"packages:\n  starter:\n    price: 0\n    items: []\n", StandardCharsets.UTF_8);
		server.getPluginManager().enablePlugin(plugin);
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (!Airdrop.isReady() && plugin.isEnabled() && System.nanoTime() < deadline) {
			server.getScheduler().performOneTick();
			LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
		}
		assertTrue(Airdrop.isReady(), "Airdrop must become ready before the startup deadline");
		world.loadChunk(0, 0);
		api = server.getServicesManager().load(AirdropApi.class);
		assertNotNull(api);
		recorder = new RetirementRecorder();
		server.getPluginManager().registerEvents(recorder, plugin);
	}

	@AfterEach
	void tearDown() {
		CrateManager.clearAll();
		PackageManager.clear();
		MockBukkit.unmock();
	}

	@Test
	void overlappingExplosionsRetireTheRemovedCrateOnceOnTheNextTick() {
		LandedCrate landed = landCrate();
		signalExplosions(landed.block());

		assertTracked(landed);
		assertTrue(recorder.events.isEmpty());
		landed.block().setType(Material.AIR);
		server.getScheduler().performTicks(2);

		assertRetiredOnce(landed, RetirementReason.EXPLODED);
		assertEquals(Material.AIR, landed.block().getType());
	}

	@Test
	void deferredExplosionsDoNotRetireAnIntactOwnedBarrel() {
		LandedCrate landed = landCrate();
		signalExplosions(landed.block());

		server.getScheduler().performTicks(2);

		assertTracked(landed);
		assertTrue(recorder.events.isEmpty());
		assertEquals(1, Airdrop.getDropAdmissionController().snapshot().landedClaims());
	}

	@Test
	void deferredExplosionsDoNotRetireAReplacementCrateAtTheSameLocation() {
		LandedCrate original = landCrate();
		signalExplosions(original.block());
		assertTrue(CrateManager.removeCrateAndDestroy(
				original.block().getLocation(), original.crate(), RetirementReason.BROKEN));
		assertRetiredOnce(original, RetirementReason.BROKEN);
		LandedCrate replacement = landCrate();
		assertEquals(original.block().getLocation(), replacement.block().getLocation());

		server.getScheduler().performTicks(2);

		assertTracked(replacement);
		assertEquals(1, recorder.events.size(), "Stale explosion tasks must not publish another retirement");
		assertEquals(original.view().crateId(), recorder.events.getFirst().airdrop().crateId());
		assertEquals(1, Airdrop.getDropAdmissionController().snapshot().landedClaims());
	}

	@Test
	void repeatedEmptyCloseRetiresTheExpectedCrateOnceWithClosedEmptyReason() {
		LandedCrate landed = landCrate();
		Inventory inventory = currentInventory(landed);
		InventoryCloseEvent close = mock(InventoryCloseEvent.class);
		when(close.getInventory()).thenReturn(inventory);
		when(close.getHandlers()).thenReturn(InventoryCloseEvent.getHandlerList());

		server.getPluginManager().callEvent(close);
		server.getPluginManager().callEvent(close);

		assertRetiredOnce(landed, RetirementReason.CLOSED_EMPTY);
		assertEquals(Material.AIR, landed.block().getType());
	}

	@Test
	void repeatedLastItemExtractionsRetireTheExpectedCrateOnceWithHopperEmptyReason() {
		LandedCrate landed = landCrate();
		Inventory source = currentInventory(landed);
		ItemStack item = new ItemStack(Material.DIAMOND);
		source.setItem(0, item);
		Inventory hopper = Bukkit.createInventory(null, InventoryType.HOPPER);
		server.getPluginManager().callEvent(new InventoryMoveItemEvent(source, item, hopper, false));
		server.getPluginManager().callEvent(new InventoryMoveItemEvent(source, item, hopper, false));
		assertTracked(landed);
		assertTrue(recorder.events.isEmpty());

		source.clear();
		server.getScheduler().performTicks(2);

		assertRetiredOnce(landed, RetirementReason.HOPPER_EMPTY);
		assertEquals(Material.AIR, landed.block().getType());
	}

	private LandedCrate landCrate() {
		// MockBukkit exposes a mutable block location that the drop resolver centers in place.
		Location ground = world.getHighestBlockAt(8, 8).getLocation();
		ground.setX(8);
		ground.setZ(8);
		DropHandle handle = api.requestSystemDrop(new Location(world, 8, 100, 8), "starter",
				DropRequestOptions.defaults().withDropHeight(20).withChickenCount(1)
						.withFlareEffects(false).withLandingEffects(false)
						.withContinuousEffects(false).withSmokeEnabled(false));
		assertTrue(handle.spawn().toCompletableFuture().isDone());
		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();
		Block landingBlock = handle.context().orElseThrow().landingLocation().getBlock();
		server.getPluginManager().callEvent(new EntityChangeBlockEvent(
				falling, landingBlock, Material.BARREL.createBlockData()));
		assertInstanceOf(DropOutcome.Landed.class, handle.outcome().toCompletableFuture().getNow(null));
		Crate crate = CrateManager.getCrate(landingBlock.getLocation());
		LandedAirdropView view = assertInstanceOf(
				LandedAirdropView.class, api.findByRequestId(handle.requestId()).orElseThrow());
		LandedCrate landed = new LandedCrate(handle, crate, landingBlock, view);
		assertTracked(landed);
		return landed;
	}

	private void signalExplosions(Block block) {
		BlockExplodeEvent blockExplosion = mock(BlockExplodeEvent.class);
		when(blockExplosion.blockList()).thenReturn(List.of(block));
		when(blockExplosion.getHandlers()).thenReturn(BlockExplodeEvent.getHandlerList());
		EntityExplodeEvent entityExplosion = mock(EntityExplodeEvent.class);
		when(entityExplosion.blockList()).thenReturn(List.of(block));
		when(entityExplosion.getHandlers()).thenReturn(EntityExplodeEvent.getHandlerList());
		server.getPluginManager().callEvent(blockExplosion);
		server.getPluginManager().callEvent(entityExplosion);
	}

	private Inventory currentInventory(LandedCrate landed) {
		Barrel barrel = (Barrel) landed.block().getState();
		Inventory inventory = barrel.getInventory();
		// MockBukkit retains the inventory holder from before the crate marker was persisted.
		Barrel holder = (Barrel) inventory.getHolder();
		NamespacedKey identity = NamespacedKey.fromString("airdrop:crate_id");
		holder.getPersistentDataContainer().set(
				identity, PersistentDataType.STRING, landed.crate().getCrateId());
		assertTrue(landed.crate().ownsLandedBarrel(holder));
		return inventory;
	}

	private void assertTracked(LandedCrate landed) {
		assertSame(landed.crate(), CrateManager.getCrate(landed.block().getLocation()));
		assertSame(landed.view(), api.findByCrateId(landed.view().crateId()).orElseThrow());
		assertSame(landed.view(), api.findByRequestId(landed.handle().requestId()).orElseThrow());
		assertSame(landed.view(), api.findByLandedBlock(landed.block()).orElseThrow());
		assertTrue(landed.crate().ownsLandedBarrel((Barrel) landed.block().getState()));
	}

	private void assertRetiredOnce(LandedCrate landed, RetirementReason reason) {
		assertEquals(1, recorder.events.size());
		AirdropRetiredEvent event = recorder.events.getFirst();
		assertEquals(reason, event.reason());
		assertSame(landed.view(), event.airdrop());
		assertTrue(recorder.observedAfterRemoval);
		assertTrue(recorder.primaryThreadOnly);
		assertFalse(recorder.observedInsideManagerLock);
		assertNull(CrateManager.getCrate(landed.block().getLocation()));
		assertTrue(api.activeDrops().isEmpty());
		assertEquals(0, Airdrop.getDropAdmissionController().snapshot().landedClaims());
		assertInstanceOf(DropOutcome.Landed.class, landed.handle().outcome().toCompletableFuture().getNow(null));
	}

	private record LandedCrate(DropHandle handle, Crate crate, Block block, LandedAirdropView view) {
	}

	private final class RetirementRecorder implements Listener {
		private final List<AirdropRetiredEvent> events = new ArrayList<>();
		private boolean observedAfterRemoval = true;
		private boolean primaryThreadOnly = true;
		private boolean observedInsideManagerLock;

		@EventHandler
		public void onRetired(AirdropRetiredEvent event) {
			events.add(event);
			var position = event.airdrop().position();
			Location retiredLocation = new Location(world, position.x(), position.y(), position.z());
			observedAfterRemoval &= api.findByCrateId(event.airdrop().crateId()).isEmpty()
					&& api.findByRequestId(event.airdrop().requestId().orElseThrow()).isEmpty()
					&& api.activeDrops().isEmpty()
					&& CrateManager.getCrate(retiredLocation) == null;
			primaryThreadOnly &= Bukkit.isPrimaryThread() && !event.isAsynchronous();
			observedInsideManagerLock |= Thread.holdsLock(CrateManager.class);
		}
	}
}
