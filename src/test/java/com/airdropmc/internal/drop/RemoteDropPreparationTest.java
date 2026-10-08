package com.airdropmc.internal.drop;

import com.airdropmc.Airdrop;
import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.DropRejectionReason;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.api.DropSpawnResult;
import com.airdropmc.api.PaymentStatus;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.packages.PackageManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteDropPreparationTest {

	private ServerMock server;
	private Airdrop plugin;
	private ColdWorld world;
	private DropRequestCoordinator coordinator;
	private final java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong();

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		world = new ColdWorld();
		world.setName("remote_world");
		server.addWorld(world);
		plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(plugin.getDataFolder().toPath());
		Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"),
				"language: en\neconomy:\n  enabled: false\n");
		Files.writeString(plugin.getDataFolder().toPath().resolve("packages.yml"),
				"packages:\n  starter:\n    price: 0\n    items: []\n");
		server.getPluginManager().enablePlugin(plugin);
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (!Airdrop.isReady() && System.nanoTime() < deadline) {
			server.getScheduler().performOneTick();
			LockSupport.parkNanos(1_000_000);
		}
		assertTrue(Airdrop.isReady());
		coordinator = new DropRequestCoordinator(plugin, new DropSettingsResolver(), clock::get);
		coordinator.startAccepting();
	}

	@AfterEach
	void tearDown() {
		coordinator.stop();
		CrateManager.clearAll();
		PackageManager.clear();
		MockBukkit.unmock();
	}

	@Test
	void waitsForColdTerrainWithoutInspectingSurfaceOrSpawning() {
		DropHandle handle = request(1600);
		assertFalse(handle.spawn().toCompletableFuture().isDone());
		assertTrue(handle.context().isEmpty());
		assertEquals(1, world.loads.size());
		assertEquals(0, world.surfaceReads);
		assertEquals(1, Airdrop.getDropAdmissionController().snapshot().falling());
		assertEquals(0, Airdrop.getDropAdmissionController().snapshot().locations());
		assertTrue(CrateManager.getCrateMap().isEmpty());
	}

	@Test
	void retainedRemoteChunkWaitsForEntityTickingThenKeepsFreeCrateThroughExpiry() {
		Airdrop.getConfiguration().getConfig().set("drop.limits.landed-lifetime-seconds", 30);
		DropHandle handle = request(1600);
		Chunk chunk = world.complete(0);
		org.mockito.Mockito.doReturn(Chunk.LoadLevel.BORDER).when(chunk).getLoadLevel();
		server.getScheduler().performOneTick();
		assertFalse(handle.spawn().toCompletableFuture().isDone());
		assertEquals(0, world.surfaceReads);
		org.mockito.Mockito.when(chunk.getLoadLevel()).thenReturn(Chunk.LoadLevel.ENTITY_TICKING);
		server.getScheduler().performOneTick();
		assertInstanceOf(DropSpawnResult.Spawned.class, handle.spawn().toCompletableFuture().getNow(null));
		assertEquals(1, world.tickets);
		var falling = CrateManager.getCrateMap().keySet().iterator().next();
		assertFalse(falling.doesAutoExpire());
		assertFalse(falling.getDropItem());
		server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityChangeBlockEvent(falling,
				handle.context().orElseThrow().landingLocation().getBlock(), org.bukkit.Material.BARREL.createBlockData()));
		assertInstanceOf(DropOutcome.Landed.class, handle.outcome().toCompletableFuture().getNow(null));
		assertEquals(1, world.tickets, "Free rewards must remain collectable without nearby players");
		var location = handle.context().orElseThrow().landingLocation();
		assertFalse(CrateManager.getCrate(location).isPaid());
		assertNull(com.airdropmc.Crate.readPaidPersistence((org.bukkit.block.Barrel) location.getBlock().getState()));
		assertTrue(world.getEntities().stream().noneMatch(e -> e instanceof org.bukkit.entity.Chicken
				|| e instanceof org.bukkit.entity.Slime));
		Airdrop.getConfiguration().getConfig().set("drop.limits.landed-lifetime-seconds", 86_400);
		server.getScheduler().performTicks(600);
		assertEquals(0, world.tickets);
		assertEquals(org.bukkit.Material.AIR, location.getBlock().getType());
		assertEquals(0, Airdrop.getDropAdmissionController().snapshot().landedClaims());
	}

	@Test
	void deniedPlayerNeverTouchesTerrain() {
		PlayerMock sender = server.addPlayer("Denied");
		sender.addAttachment(plugin, "airdrop.send", true);
		DropHandle handle = coordinator.requestSendDrop(sender, target(1600), "starter", quiet());
		assertEquals(DropRejectionReason.INSUFFICIENT_PERMISSION, rejected(handle).rejection().reason());
		assertEquals(0, world.surfaceReads);
		assertTrue(world.loads.isEmpty());
	}

	@Test
	void pendingPlayerCannotSubmitAnotherTerrainLoadEvenWithCooldownBypass() {
		PlayerMock sender = server.addPlayer("Sender");
		sender.setOp(true);
		DropHandle first = coordinator.requestSendDrop(sender, target(1600), "starter", quiet());
		DropHandle second = coordinator.requestSendDrop(sender, target(3200), "starter", quiet());
		assertFalse(first.spawn().toCompletableFuture().isDone());
		assertEquals(DropRejectionReason.REQUEST_PENDING, rejected(second).rejection().reason());
		assertEquals(1, world.loads.size());
	}

	@Test
	void timeoutDoesNotReleaseTheUnderlyingLoadBudgetOrReviveOnLateCompletion() {
		DropHandle first = request(1600);
		clock.addAndGet(Duration.ofSeconds(10).toNanos());
		server.getScheduler().performTicks(200);
		assertEquals(DropRejectionReason.TARGET_LOAD_TIMEOUT, rejected(first).rejection().reason());
		DropHandle second = request(3200);
		clock.addAndGet(Duration.ofSeconds(10).toNanos());
		server.getScheduler().performTicks(200);
		assertInstanceOf(DropOutcome.Rejected.class, second.outcome().toCompletableFuture().getNow(null));
		DropHandle third = request(4800);
		assertEquals(DropRejectionReason.REMOTE_LOAD_CAPACITY, rejected(third).rejection().reason());
		assertEquals(2, world.loads.size());
		world.complete(0);
		server.getScheduler().performOneTick();
		assertEquals(0, world.surfaceReads);
		assertTrue(CrateManager.getCrateMap().isEmpty());
		assertEquals(0, world.tickets);
		assertEquals(0, Airdrop.getDropAdmissionController().snapshot().falling());
		assertFalse(request(6400).spawn().toCompletableFuture().isDone());
		assertEquals(3, world.loads.size());
	}

	@Test
	void borderAndCapacityRejectWithoutStartingMoreTerrainWork() {
		world.getWorldBorder().setSize(100);
		assertEquals(DropRejectionReason.OUTSIDE_WORLD_BORDER, rejected(request(1600)).rejection().reason());
		assertTrue(world.loads.isEmpty());
		world.getWorldBorder().setSize(60_000_000);
		Airdrop.getConfiguration().getConfig().set("drop.limits.max-falling", 1);
		request(1600);
		assertEquals(DropRejectionReason.FALLING_CAPACITY, rejected(request(3200)).rejection().reason());
		assertEquals(1, world.loads.size());
	}

	@Test
	void failedLoadConsumesAttemptThrottleEvenForAnExemptOperator() {
		PlayerMock sender = server.addPlayer("Exempt");
		sender.setOp(true);
		DropHandle first = coordinator.requestSendDrop(sender, target(1600), "starter", quiet());
		world.loads.getFirst().future.complete(null);
		assertEquals(DropRejectionReason.TARGET_NOT_GENERATED, rejected(first).rejection().reason());
		DropHandle retry = coordinator.requestSendDrop(sender, target(3200), "starter", quiet());
		assertEquals(DropRejectionReason.REMOTE_LOAD_THROTTLED, rejected(retry).rejection().reason());
		assertEquals(Duration.ofSeconds(5), rejected(retry).rejection().retryAfter().orElseThrow());
		assertEquals(PaymentStatus.NOT_APPLICABLE, rejected(retry).payment());
		assertEquals(1, world.loads.size());
		assertEquals(0, Airdrop.getDropAdmissionController().snapshot().pending());
	}

	@Test
	void exceptionAndShutdownCannotRevivePendingLoads() {
		DropHandle failure = request(1600);
		world.loads.getFirst().future.completeExceptionally(new IllegalStateException("disk unavailable"));
		assertEquals(DropRejectionReason.INVALID_TARGET, rejected(failure).rejection().reason());
		clock.addAndGet(Duration.ofSeconds(5).toNanos());
		DropHandle waiting = request(3200);
		coordinator.stop();
		assertEquals(DropRejectionReason.SHUTTING_DOWN, rejected(waiting).rejection().reason());
		world.complete(1);
		assertTrue(CrateManager.getCrateMap().isEmpty());
		assertEquals(0, world.surfaceReads);
		assertEquals(0, world.tickets);
		assertEquals(0, Airdrop.getDropAdmissionController().snapshot().falling());
		assertTrue(waiting.spawn().toCompletableFuture().isDone());
	}

	@Test
	void unavailableNeighborTerrainDoesNotAddAnEntityTickingTicket() {
		world.missingNeighbor = true;
		DropHandle handle = request(1600);
		world.complete(0);
		assertEquals(DropRejectionReason.TARGET_NOT_GENERATED, rejected(handle).rejection().reason());
		assertEquals(0, world.tickets);
		assertEquals(0, world.surfaceReads);
	}

	@Test
	void readinessTimeoutReleasesTicketAndReservation() {
		DropHandle handle = request(1600);
		world.complete(0);
		assertEquals(1, world.tickets);
		clock.addAndGet(Duration.ofSeconds(10).toNanos());
		server.getScheduler().performOneTick();
		assertEquals(DropRejectionReason.TARGET_LOAD_TIMEOUT, rejected(handle).rejection().reason());
		assertEquals(0, world.tickets);
		assertEquals(0, Airdrop.getDropAdmissionController().snapshot().falling());
	}

	@Test
	void sharedChunkTicketSurvivesOneFailureUntilTheOtherFreeCrateIsRemoved() {
		PlayerMock first = server.addPlayer("First");
		PlayerMock second = server.addPlayer("Second");
		first.setOp(true);
		second.setOp(true);
		DropHandle a = coordinator.requestSendDrop(first, target(1600), "starter", quiet());
		DropHandle b = coordinator.requestSendDrop(second, target(1601), "starter", quiet());
		world.completeTicking(0);
		world.completeTicking(1);
		server.getScheduler().performOneTick();
		assertEquals(1, world.tickets);
		var aId = ((DropSpawnResult.Spawned) a.spawn().toCompletableFuture().getNow(null)).airdrop().fallingEntityId();
		var aEntity = CrateManager.getCrateMap().keySet().stream().filter(e -> e.getUniqueId().equals(aId)).findFirst().orElseThrow();
		CrateManager.removeCrateAndDestroy(aEntity);
		assertInstanceOf(DropOutcome.Failed.class, a.outcome().toCompletableFuture().getNow(null));
		assertEquals(1, world.tickets);
		var remaining = CrateManager.getCrateMap().keySet().iterator().next();
		server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityChangeBlockEvent(remaining,
				b.context().orElseThrow().landingLocation().getBlock(), org.bukkit.Material.BARREL.createBlockData()));
		assertInstanceOf(DropOutcome.Landed.class, b.outcome().toCompletableFuture().getNow(null));
		assertEquals(1, world.tickets);
		CrateManager.removeCrateAndDestroy(b.context().orElseThrow().landingLocation());
		assertEquals(0, world.tickets);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void initiallyTickingDestinationKeepsFreeRewardsAfterFlyAwayUntilRemoval(boolean shutdown) {
		var chunk = org.mockito.Mockito.spy(world.getChunkAt(100, 100));
		org.mockito.Mockito.doReturn(Chunk.LoadLevel.ENTITY_TICKING).when(chunk).getLoadLevel();
		world.ready.add("100:100");
		world.chunks.put("100:100", chunk);
		DropHandle handle = request(1600);
		assertInstanceOf(DropSpawnResult.Spawned.class, handle.spawn().toCompletableFuture().getNow(null));
		assertTrue(world.loads.isEmpty(), "Already ticking terrain needs no remote preparation");
		assertEquals(1, world.tickets, "Delivery must outlive the players keeping its chunk loaded");
		var falling = CrateManager.getCrateMap().keySet().iterator().next();
		server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityChangeBlockEvent(falling,
				handle.context().orElseThrow().landingLocation().getBlock(), org.bukkit.Material.BARREL.createBlockData()));
		assertInstanceOf(DropOutcome.Landed.class, handle.outcome().toCompletableFuture().getNow(null));
		assertEquals(1, world.tickets, "Keep the local fly-away animation loaded");
		assertTrue(world.getEntities().stream().anyMatch(entity -> entity instanceof org.bukkit.entity.Chicken));
		server.getScheduler().performTicks(62);
		assertEquals(1, world.tickets, "Animation cleanup must not retire the free reward");
		if (shutdown) {
			coordinator.stop();
			CrateManager.prepareForShutdown(plugin);
		} else {
			CrateManager.removeCrateAndDestroy(handle.context().orElseThrow().landingLocation());
		}
		assertEquals(0, world.tickets);
		assertTrue(world.getEntities().stream().noneMatch(entity -> entity instanceof org.bukkit.entity.Chicken
				|| entity instanceof org.bukkit.entity.Slime));
	}

	@ParameterizedTest
	@ValueSource(strings = {"clear", "world", "empty", "listener"})
	void freeRetentionIsReleasedByExistingRetirementPaths(String retirement) {
		if (retirement.equals("listener")) {
			server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
				@org.bukkit.event.EventHandler
				public void onLanded(com.airdropmc.api.event.AirdropLandedEvent event) {
					CrateManager.clearAll();
				}
			}, plugin);
		}
		DropHandle handle = request(1600);
		world.completeTicking(0);
		server.getScheduler().performOneTick();
		land(handle);
		var location = handle.context().orElseThrow().landingLocation();
		if (!retirement.equals("listener")) {
			assertEquals(1, world.tickets);
		}
		switch (retirement) {
			case "clear" -> CrateManager.clearAll();
			case "world" -> assertTrue(CrateManager.prepareWorldForUnload(world, plugin));
			case "empty" -> {
				var barrel = (org.bukkit.block.Barrel) location.getBlock().getState();
				var player = server.addPlayer();
				player.openInventory(barrel.getInventory());
				player.closeInventory();
			}
		}
		assertEquals(0, world.tickets);
		assertEquals(org.bukkit.Material.AIR, location.getBlock().getType());
		assertEquals(0, Airdrop.getDropAdmissionController().snapshot().landedClaims());
		CrateManager.clearAll();
		assertEquals(0, world.tickets, "Retirement must release each claim only once");
	}

	@Test
	void landedFreeCratesShareTicketUntilBothAreRemoved() {
		DropHandle first = request(1600);
		world.completeTicking(0);
		server.getScheduler().performOneTick();
		DropHandle second = request(1601);
		land(first);
		land(second);
		assertEquals(1, world.tickets);
		CrateManager.removeCrateAndDestroy(first.context().orElseThrow().landingLocation());
		assertEquals(1, world.tickets);
		CrateManager.removeCrateAndDestroy(second.context().orElseThrow().landingLocation());
		assertEquals(0, world.tickets);
	}

	@ParameterizedTest
	@ValueSource(strings = {"break", "block-explosion", "entity-explosion"})
	void nativeRemovalReconciliationReleasesSharedTicketOnlyAfterLastFreeCrate(String eventType) {
		DropHandle first = request(1600);
		world.completeTicking(0);
		server.getScheduler().performOneTick();
		DropHandle second = request(1601);
		land(first);
		land(second);
		assertEquals(1, world.tickets);
		for (DropHandle handle : List.of(first, second)) {
			Location location = handle.context().orElseThrow().landingLocation();
			var crate = CrateManager.getCrate(location);
			// Overlapping native signals must reconcile the same expected owner only once.
			signalNativeRemoval(location.getBlock(), eventType);
			signalNativeRemoval(location.getBlock(), eventType);
			assertEquals(crate, CrateManager.getCrate(location));
			location.getBlock().setType(org.bukkit.Material.STONE);
			server.getScheduler().performOneTick();
			assertNull(CrateManager.getCrate(location));
			assertEquals(org.bukkit.Material.STONE, location.getBlock().getType(),
					"Native finalization must preserve replacement blocks");
			assertFalse(CrateManager.finalizeCrateBreak(location, crate));
			if (handle == first) {
				assertEquals(1, world.tickets);
				assertEquals(0, world.ticketRemovals);
				assertEquals(1, Airdrop.getDropAdmissionController().snapshot().landedClaims());
			} else {
				assertEquals(0, world.tickets);
				assertEquals(1, world.ticketRemovals);
				assertEquals(0, Airdrop.getDropAdmissionController().snapshot().landedClaims());
			}
		}
		CrateManager.clearAll();
		assertEquals(1, world.ticketRemovals);
	}

	private void signalNativeRemoval(Block block, String eventType) {
		switch (eventType) {
			case "break" -> server.getPluginManager().callEvent(
					new org.bukkit.event.block.BlockBreakEvent(block, server.addPlayer()));
			case "block-explosion" -> {
				var event = org.mockito.Mockito.mock(org.bukkit.event.block.BlockExplodeEvent.class);
				org.mockito.Mockito.when(event.blockList()).thenReturn(List.of(block));
				new com.airdropmc.listeners.CrateCleanupListener(plugin).onBlockExplode(event);
			}
			case "entity-explosion" -> {
				var event = org.mockito.Mockito.mock(org.bukkit.event.entity.EntityExplodeEvent.class);
				org.mockito.Mockito.when(event.blockList()).thenReturn(List.of(block));
				new com.airdropmc.listeners.CrateCleanupListener(plugin).onEntityExplode(event);
			}
		}
	}

	@Test
	void suspensionReleasesRetentionButPreservesItsAdmissionClaim() {
		DropHandle handle = request(1600);
		world.completeTicking(0);
		server.getScheduler().performOneTick();
		land(handle);
		var lease = CrateManager.getCrate(handle.context().orElseThrow().landingLocation()).suspendLandedBarrel();
		try {
			assertEquals(0, world.tickets);
			assertEquals(1, world.ticketRemovals);
			assertEquals(1, Airdrop.getDropAdmissionController().snapshot().landedClaims());
		} finally {
			lease.close();
		}
	}

	@Test
	void fullLandedCapacityDoesNotAcquireAnotherTicket() {
		Airdrop.getConfiguration().getConfig().set("drop.limits.max-landed", 1);
		DropHandle first = request(1600);
		world.completeTicking(0);
		server.getScheduler().performOneTick();
		land(first);
		assertEquals(1, world.tickets);
		DropHandle denied = request(1616);
		assertEquals(DropRejectionReason.LANDED_CAPACITY, rejected(denied).rejection().reason());
		assertEquals(1, world.tickets);
		assertEquals(1, world.loads.size());
		CrateManager.clearAll();
		assertEquals(0, world.tickets);
	}

	private void land(DropHandle handle) {
		var id = ((DropSpawnResult.Spawned) handle.spawn().toCompletableFuture().getNow(null)).airdrop().fallingEntityId();
		var falling = CrateManager.getCrateMap().keySet().stream()
				.filter(entity -> entity.getUniqueId().equals(id)).findFirst().orElseThrow();
		server.getPluginManager().callEvent(new org.bukkit.event.entity.EntityChangeBlockEvent(falling,
				handle.context().orElseThrow().landingLocation().getBlock(), org.bukkit.Material.BARREL.createBlockData()));
		assertInstanceOf(DropOutcome.Landed.class, handle.outcome().toCompletableFuture().getNow(null));
	}

	@Test
	void laterImmediateRequestSharesAnExistingRemoteTicket() {
		DropHandle first = request(1600);
		world.completeTicking(0);
		server.getScheduler().performOneTick();
		DropHandle later = request(1601);
		assertInstanceOf(DropSpawnResult.Spawned.class, later.spawn().toCompletableFuture().getNow(null));
		assertEquals(1, world.loads.size(), "Immediate delivery does not start another remote load");
		assertEquals(1, world.tickets);
		var firstId = ((DropSpawnResult.Spawned) first.spawn().toCompletableFuture().getNow(null)).airdrop().fallingEntityId();
		CrateManager.removeCrateAndDestroy(CrateManager.getCrateMap().keySet().stream()
				.filter(entity -> entity.getUniqueId().equals(firstId)).findFirst().orElseThrow());
		assertEquals(1, world.tickets, "Later request still owns the shared ticket");
		assertFalse(later.outcome().toCompletableFuture().isDone());
		coordinator.stop();
		assertEquals(0, world.tickets);
	}

	@Test
	void bindingConflictCannotReleaseTheFirstRequestsLocationOrTicket() {
		world.fixedSurface = 64;
		PlayerMock first = server.addPlayer("First");
		PlayerMock second = server.addPlayer("Second");
		first.setOp(true);
		second.setOp(true);
		DropHandle a = coordinator.requestSendDrop(first, target(1600), "starter", quiet());
		DropHandle b = coordinator.requestSendDrop(second, target(1600), "starter", quiet());
		world.completeTicking(0);
		world.completeTicking(1);
		server.getScheduler().performOneTick();
		assertInstanceOf(DropSpawnResult.Spawned.class, a.spawn().toCompletableFuture().getNow(null));
		assertEquals(DropRejectionReason.LOCATION_RESERVED, rejected(b).rejection().reason());
		assertEquals(1, world.tickets);
		assertEquals(1, Airdrop.getDropAdmissionController().snapshot().locations());
		assertEquals(1, Airdrop.getDropAdmissionController().snapshot().falling());
	}

	@Test
	void fallingWatchdogReleasesTheRetainedChunkWithoutProducingItems() {
		DropHandle handle = request(1600);
		world.completeTicking(0);
		server.getScheduler().performOneTick();
		server.getScheduler().performTicks(2400);
		assertInstanceOf(DropOutcome.Failed.class, handle.outcome().toCompletableFuture().getNow(null));
		assertTrue(CrateManager.getCrateMap().isEmpty());
		assertEquals(0, world.tickets);
		assertTrue(world.getEntities().isEmpty());
	}

	@Test
	void paidRemoteRequestCapturesPriceAndExemptionBeforeWaitingButProviderAfterWaiting() throws Exception {
		Airdrop.getConfiguration().getConfig().set("economy.enabled", true);
		var packages = new org.bukkit.configuration.file.YamlConfiguration();
		packages.set("packages.starter.price", 10);
		packages.set("packages.starter.items", List.of(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND)));
		PackageManager.publishPackages(PackageManager.materializePackages(packages));
		var oldProvider = org.mockito.Mockito.mock(com.airdropmc.economy.EconomyProvider.class);
		var selectedProvider = org.mockito.Mockito.mock(com.airdropmc.economy.EconomyProvider.class);
		org.mockito.Mockito.when(selectedProvider.canAfford(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(CompletableFuture.completedFuture(com.airdropmc.economy.EconomyResult.ok()));
		org.mockito.Mockito.when(selectedProvider.withdraw(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn(CompletableFuture.completedFuture(com.airdropmc.economy.EconomyResult.ok()));
		setProvider(oldProvider);
		PlayerMock sender = server.addPlayer("Paid");
		sender.addAttachment(plugin, "airdrop.send", true);
		sender.addAttachment(plugin, "airdrop.package.starter", true);
		DropHandle handle = coordinator.requestSendDrop(sender, target(1600), "starter", quiet());
		org.mockito.Mockito.verifyNoInteractions(oldProvider);
		// Reloaded package values and changed cost permissions must not rewrite this request.
		packages.set("packages.starter.price", 0);
		PackageManager.publishPackages(PackageManager.materializePackages(packages));
		sender.addAttachment(plugin, "airdrop.cost.bypass", true);
		setProvider(selectedProvider);
		world.completeTicking(0);
		server.getScheduler().performTicks(4);
		assertEquals(new java.math.BigDecimal("10.0"), handle.context().orElseThrow().airdropPackage().price());
		assertEquals(PaymentStatus.CHARGED,
				assertInstanceOf(DropSpawnResult.Spawned.class, handle.spawn().toCompletableFuture().getNow(null)).payment());
		org.mockito.Mockito.verify(selectedProvider).withdraw(
				new com.airdropmc.economy.EconomyPlayer(sender.getUniqueId(), sender.getName()), new java.math.BigDecimal("10.0"));
		org.mockito.Mockito.verifyNoInteractions(oldProvider);
		land(handle);
		assertTrue(CrateManager.getCrate(handle.context().orElseThrow().landingLocation()).isPaid());
		assertEquals(0, world.tickets, "Paid crates retain their existing unload/recovery policy");
	}

	@Test
	void lostEconomyProviderAfterPreparationDoesNotChargeOrSpawn() throws Exception {
		Airdrop.getConfiguration().getConfig().set("economy.enabled", true);
		var packages = new org.bukkit.configuration.file.YamlConfiguration();
		packages.set("packages.starter.price", 10);
		packages.set("packages.starter.items", List.of(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND)));
		PackageManager.publishPackages(PackageManager.materializePackages(packages));
		var provider = org.mockito.Mockito.mock(com.airdropmc.economy.EconomyProvider.class);
		setProvider(provider);
		PlayerMock sender = server.addPlayer("Paid");
		sender.addAttachment(plugin, "airdrop.send", true);
		sender.addAttachment(plugin, "airdrop.package.starter", true);
		DropHandle handle = coordinator.requestSendDrop(sender, target(1600), "starter", quiet());
		setProvider(null);
		world.completeTicking(0);
		server.getScheduler().performOneTick();
		assertEquals(DropRejectionReason.ECONOMY_PROVIDER_UNAVAILABLE, rejected(handle).rejection().reason());
		org.mockito.Mockito.verifyNoInteractions(provider);
		assertTrue(CrateManager.getCrateMap().isEmpty());
		assertEquals(0, world.tickets);
		assertEquals(0, Airdrop.getDropAdmissionController().snapshot().pending());
	}

	@Test
	void loadedButInactiveTerrainStillWaitsForEntityTicking() {
		var inactive = org.mockito.Mockito.spy(world.getChunkAt(100, 100));
		org.mockito.Mockito.doReturn(Chunk.LoadLevel.BORDER).when(inactive).getLoadLevel();
		world.chunks.put("100:100", inactive);
		world.ready.add("100:100");
		DropHandle handle = request(1600);
		assertFalse(handle.spawn().toCompletableFuture().isDone());
		assertEquals(1, world.loads.size());
		assertEquals(0, world.surfaceReads);
		world.completeTicking(0);
		server.getScheduler().performOneTick();
		assertInstanceOf(DropSpawnResult.Spawned.class, handle.spawn().toCompletableFuture().getNow(null));
	}

	private void setProvider(com.airdropmc.economy.EconomyProvider provider) throws Exception {
		var field = Airdrop.class.getDeclaredField("economyProvider");
		field.setAccessible(true);
		field.set(null, provider);
	}

	@Test
	void unloadedWorldStopsTheNeighborhoodSequenceBeforeAnotherBackendCall() {
		DropHandle handle = request(1600);
		assertTrue(server.unloadWorld(world, false));
		world.complete(0);
		assertEquals(DropRejectionReason.INVALID_TARGET, rejected(handle).rejection().reason());
		assertEquals(1, world.loads.size());
		assertEquals(0, world.surfaceReads);
		assertEquals(0, world.tickets);
		assertEquals(0, Airdrop.getDropAdmissionController().snapshot().falling());
	}

	@Test
	void elapsedDeadlineRejectsLateCompletionEvenBeforeTheTimerRuns() {
		DropHandle handle = request(1600);
		clock.addAndGet(Duration.ofSeconds(11).toNanos());
		world.complete(0);
		assertEquals(DropRejectionReason.TARGET_LOAD_TIMEOUT, rejected(handle).rejection().reason());
		assertTrue(handle.spawn().toCompletableFuture().isDone());
		assertTrue(handle.context().isEmpty());
		assertEquals(0, world.surfaceReads);
		assertEquals(0, world.tickets);
	}

	@Test
	void generationRejectsANeighborhoodCrossingTheBorderBeforeBackendWork() {
		Airdrop.getConfiguration().getConfig().set("drop.remote-loading.generate-new-chunks", true);
		world.getWorldBorder().setSize(100);
		DropHandle handle = coordinator.requestSystemDrop(new Location(world, 40, 200, 0), "starter", quiet());
		assertEquals(DropRejectionReason.OUTSIDE_WORLD_BORDER, rejected(handle).rejection().reason());
		assertTrue(world.loads.isEmpty());
		assertEquals(0, world.tickets);
		assertEquals(0, Airdrop.getDropAdmissionController().snapshot().falling());
	}

	@Test
	void generatedOnlyDeliveryNearTheBorderDoesNotRequireAnInteriorNeighborhood() {
		world.getWorldBorder().setSize(100);
		DropHandle handle = coordinator.requestSystemDrop(new Location(world, 40, 200, 0), "starter", quiet());
		assertFalse(handle.spawn().toCompletableFuture().isDone());
		world.completeTicking(0);
		server.getScheduler().performOneTick();
		assertInstanceOf(DropSpawnResult.Spawned.class, handle.spawn().toCompletableFuture().getNow(null));
	}

	private DropHandle request(int x) {
		return coordinator.requestSystemDrop(target(x), "starter", quiet());
	}

	private Location target(int x) {
		return new Location(world, x, 200, 1600);
	}

	private DropOutcome.Rejected rejected(DropHandle handle) {
		return assertInstanceOf(DropOutcome.Rejected.class, handle.outcome().toCompletableFuture().getNow(null));
	}

	private DropRequestOptions quiet() {
		return DropRequestOptions.defaults().withDropHeight(20).withChickenCount(1)
				.withFlareEffects(false).withLandingEffects(false).withContinuousEffects(false).withSmokeEnabled(false);
	}

	private static final class ColdWorld extends WorldMock {
		private record Load(int x, int z, CompletableFuture<Chunk> future) {}
		final List<Load> loads = new ArrayList<>();
		final Set<String> ready = new HashSet<>();
		final Map<String, org.mockbukkit.mockbukkit.world.ChunkMock> chunks = new HashMap<>();
		boolean missingNeighbor;
		Integer fixedSurface;
		boolean completingNeighborhood;
		int surfaceReads;
		int tickets;
		int ticketRemovals;

		@Override
		public boolean isChunkLoaded(int x, int z) {
			return ready.contains(x + ":" + z);
		}

		@Override
		public CompletableFuture<Chunk> getChunkAtAsync(int x, int z, boolean generate, boolean urgent) {
			assertFalse(generate);
			assertFalse(urgent);
			if (completingNeighborhood) {
				if (missingNeighbor) return CompletableFuture.completedFuture(null);
				ready.add(x + ":" + z);
				var chunk = chunks.get(x + ":" + z);
				if (chunk == null) {
					chunk = org.mockito.Mockito.spy(super.getChunkAt(x, z));
					org.mockito.Mockito.doReturn(Chunk.LoadLevel.BORDER).when(chunk).getLoadLevel();
					chunks.put(x + ":" + z, chunk);
				}
				return CompletableFuture.completedFuture(chunk);
			}
			CompletableFuture<Chunk> future = new CompletableFuture<>();
			loads.add(new Load(x, z, future));
			return future;
		}

		@Override
		public Block getHighestBlockAt(int x, int z) {
			surfaceReads++;
			if (fixedSurface == null) return super.getHighestBlockAt(x, z);
			Block surface = org.mockito.Mockito.mock(Block.class);
			org.mockito.Mockito.when(surface.getLocation()).thenAnswer(ignored -> new Location(this, x, fixedSurface, z));
			return surface;
		}

		@Override
		public boolean addPluginChunkTicket(int x, int z, Plugin owner) {
			tickets++;
			return true;
		}

		@Override
		public boolean removePluginChunkTicket(int x, int z, Plugin owner) {
			tickets--;
			ticketRemovals++;
			return true;
		}

		@Override
		public org.mockbukkit.mockbukkit.world.ChunkMock getChunkAt(int x, int z) {
			return chunks.getOrDefault(x + ":" + z, super.getChunkAt(x, z));
		}

		Chunk complete(int index) {
			Load load = loads.get(index);
			ready.add(load.x + ":" + load.z);
			var chunk = org.mockito.Mockito.spy(super.getChunkAt(load.x, load.z));
			org.mockito.Mockito.when(chunk.getWorld()).thenReturn(this);
			org.mockito.Mockito.when(chunk.getX()).thenReturn(load.x);
			org.mockito.Mockito.when(chunk.getZ()).thenReturn(load.z);
			org.mockito.Mockito.doReturn(Chunk.LoadLevel.BORDER).when(chunk).getLoadLevel();
			chunks.put(load.x + ":" + load.z, chunk);
			completingNeighborhood = true;
			load.future.complete(chunk);
			completingNeighborhood = false;
			return chunks.get(load.x + 2 + ":" + (load.z + 2));
		}

		void completeTicking(int index) {
			Chunk chunk = complete(index);
			org.mockito.Mockito.doReturn(Chunk.LoadLevel.ENTITY_TICKING).when(chunk).getLoadLevel();
		}

		@Override
		public boolean isChunkGenerated(int x, int z) { throw new AssertionError("No synchronous terrain probes"); }
	}
}
