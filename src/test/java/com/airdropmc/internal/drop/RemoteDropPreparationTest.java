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
	void retainedRemoteChunkWaitsForEntityTickingBeforeResolvingThenCleansUpOnLanding() {
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
		assertEquals(0, world.tickets);
		assertTrue(world.getEntities().stream().noneMatch(e -> e instanceof org.bukkit.entity.Chicken
				|| e instanceof org.bukkit.entity.Slime));
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
	void sharedChunkTicketSurvivesOneFailureUntilTheOtherDeliveryLands() {
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
		assertEquals(0, world.tickets);
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
