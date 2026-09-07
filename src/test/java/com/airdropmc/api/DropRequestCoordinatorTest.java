package com.airdropmc.api;

import com.airdropmc.Airdrop;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.internal.drop.DropRequestCoordinator;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLocationKey;
import com.airdropmc.packages.Package;
import com.airdropmc.packages.PackageManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DropRequestCoordinatorTest {

	private ServerMock server;
	private WorldMock world;
	private Airdrop plugin;
	private AirdropApi api;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("request_world");
		plugin = preparedPlugin();
		server.getPluginManager().enablePlugin(plugin);
		awaitCondition(() -> Airdrop.isReady() || !plugin.isEnabled());
		assertTrue(plugin.isEnabled());
		api = server.getServicesManager().load(AirdropApi.class);
	}

	@AfterEach
	void tearDown() {
		CrateManager.clearAll();
		PackageManager.clear();
		MockBukkit.unmock();
	}

	@Test
	void unknownPackageReturnsCorrelatedTypedRejectionWithoutContext() {
		Location requested = new Location(world, 2, 100, 3);

		DropHandle handle = api.requestSystemDrop(
				requested, "missing", DropRequestOptions.defaults());

		assertEquals("missing", handle.descriptor().requestedPackageName());
		assertEquals(handle.requestId(), handle.descriptor().requestId());
		assertTrue(handle.context().isEmpty());
		DropOutcome.Rejected outcome = assertInstanceOf(
				DropOutcome.Rejected.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.UNKNOWN_PACKAGE, outcome.rejection().reason());
		assertEquals(PaymentStatus.NOT_APPLICABLE, outcome.payment());
		assertInstanceOf(
				DropSpawnResult.NotSpawned.class,
				handle.spawn().toCompletableFuture().join());
		assertTrue(CrateManager.getCrateMap().isEmpty());
	}

	@Test
	void freeSystemRequestPublishesSnapshotThenLands() {
		Location requested = new Location(world, 5, 100, 7);
		DropRequestOptions options = DropRequestOptions.defaults()
				.withDropHeight(20)
				.withChickenCount(1)
				.withFlareEffects(false)
				.withLandingEffects(false)
				.withContinuousEffects(false)
				.withSmokeEnabled(false);

		DropHandle handle = api.requestSystemDrop(requested, "starter", options);

		DropSpawnResult.Spawned spawned = assertInstanceOf(
				DropSpawnResult.Spawned.class, handle.spawn().toCompletableFuture().join());
		assertEquals(PaymentStatus.NOT_APPLICABLE, spawned.payment());
		assertEquals(20, handle.context().orElseThrow().settings().dropHeight());
		assertFalse(handle.outcome().toCompletableFuture().isDone());

		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();
		AirdropView fallingView = api.findByFallingEntity(falling).orElseThrow();
		assertEquals(handle.requestId(), fallingView.requestId().orElseThrow());
		assertEquals(fallingView, api.findByRequestId(handle.requestId()).orElseThrow());
		assertEquals(fallingView, api.findByCrateId(fallingView.crateId()).orElseThrow());
		assertEquals(1, api.activeDrops().size());
		assertEquals(1, api.status().fallingCount());
		assertEquals(1, api.status().pendingCount());
		CompletableFuture.runAsync(() -> {
			assertEquals(fallingView, api.findByRequestId(handle.requestId()).orElseThrow());
			assertEquals(fallingView, api.findByCrateId(fallingView.crateId()).orElseThrow());
			assertEquals(List.of(fallingView), api.activeDrops().stream().toList());
		}).join();
		EntityChangeBlockEvent landing = new EntityChangeBlockEvent(
				falling,
				handle.context().orElseThrow().landingLocation().getBlock(),
				Material.BARREL.createBlockData());
		server.getPluginManager().callEvent(landing);

		DropOutcome.Landed outcome = assertInstanceOf(
				DropOutcome.Landed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DeliveryStatus.LANDED, outcome.delivery());
		assertEquals(handle.requestId(), outcome.requestId());
		assertTrue(api.findByFallingEntity(falling).isEmpty());
		AirdropView indexedLanded = api.findByLandedBlock(
				handle.context().orElseThrow().landingLocation().getBlock()).orElseThrow();
		assertEquals(outcome.airdrop().crateId(), indexedLanded.crateId());
		assertEquals(outcome.airdrop().requestId(), indexedLanded.requestId());
		assertEquals(1, api.activeDrops().size());
		assertEquals(0, api.status().fallingCount());
		assertEquals(1, api.status().landedCount());
		assertEquals(0, api.status().pendingCount());
	}

	@Test
	void fallingMovementRefreshesEveryLookupWithoutChangingRetainedSnapshots() {
		DropHandle handle = api.requestSystemDrop(
				new Location(world, 5, 100, 7), "starter", quietOptions());
		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();
		AirdropView original = api.findByRequestId(handle.requestId()).orElseThrow();
		List<AirdropView> retained = List.copyOf(api.activeDrops());
		WorldPosition spawnPosition = original.position();
		Location moved = falling.getLocation().add(1, -5, 2);
		((org.mockbukkit.mockbukkit.entity.EntityMock) falling).setLocation(moved);

		server.getScheduler().performTicks(2);

		AirdropView refreshed = api.findByRequestId(handle.requestId()).orElseThrow();
		assertEquals(WorldPosition.from(moved), refreshed.position());
		assertEquals(refreshed, api.findByCrateId(original.crateId()).orElseThrow());
		assertEquals(refreshed, api.findByFallingEntity(falling).orElseThrow());
		assertEquals(List.of(refreshed), List.copyOf(api.activeDrops()));
		assertEquals(spawnPosition, retained.getFirst().position());
		assertEquals(spawnPosition, original.position());
		CompletableFuture.runAsync(() -> {
			assertEquals(refreshed, api.findByRequestId(handle.requestId()).orElseThrow());
			assertEquals(refreshed, api.findByCrateId(original.crateId()).orElseThrow());
			assertEquals(List.of(refreshed), List.copyOf(api.activeDrops()));
		}).join();

		server.getPluginManager().callEvent(new EntityChangeBlockEvent(
				falling, handle.context().orElseThrow().landingLocation().getBlock(),
				Material.BARREL.createBlockData()));
		server.getScheduler().performTicks(2);
		assertInstanceOf(LandedAirdropView.class,
				api.findByRequestId(handle.requestId()).orElseThrow());
		assertTrue(api.findByFallingEntity(falling).isEmpty());
	}

	@Test
	void cancelledPaperLandingIsDistinctFromFailure() {
		DropHandle handle = api.requestSystemDrop(
				new Location(world, 9, 100, 11),
				"starter",
				DropRequestOptions.defaults()
						.withDropHeight(20)
						.withChickenCount(1)
						.withFlareEffects(false));
		handle.spawn().toCompletableFuture().join();
		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();
		EntityChangeBlockEvent landing = new EntityChangeBlockEvent(
				falling,
				world.getBlockAt(9, 1, 11),
				Material.BARREL.createBlockData());
		landing.setCancelled(true);

		server.getPluginManager().callEvent(landing);

		DropOutcome.Failed outcome = assertInstanceOf(
				DropOutcome.Failed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DeliveryStatus.CANCELLED, outcome.delivery());
		assertEquals(PaymentStatus.NOT_APPLICABLE, outcome.payment());
	}

	@Test
	void nullAndOffThreadBukkitArgumentsFailImmediately() throws Exception {
		assertThrows(NullPointerException.class,
				() -> api.requestSystemDrop(null, "starter", DropRequestOptions.defaults()));
		Throwable[] failure = new Throwable[1];
		Thread thread = new Thread(() -> {
			try {
				api.requestSystemDrop(
						new Location(world, 0, 100, 0), "starter", DropRequestOptions.defaults());
			} catch (Throwable caught) {
				failure[0] = caught;
			}
		}, "off-thread-request");
		thread.start();
		thread.join();

		assertInstanceOf(IllegalStateException.class, failure[0]);
	}

	@Test
	void resolvedContextAndCrateRetainDetachedRequestSnapshot() {
		Location requested = new Location(world, 22, 100, 24);
		DropHandle handle = api.requestSystemDrop(
				requested,
				"starter",
				DropRequestOptions.defaults()
						.withDropHeight(17)
						.withChickenCount(1)
						.withFlareEffects(false));
		requested.setX(999);
		requested.setY(999);

		ResolvedDropContext context = handle.context().orElseThrow();
		var crate = CrateManager.getCrateMap().values().iterator().next();
		assertEquals(22.0, handle.descriptor().requestedPosition().x());
		assertEquals(17, context.settings().dropHeight());
		assertEquals(handle.requestId(), crate.getRequestId());
		assertTrue(crate.getResolvedContext() == context);
	}

	@Test
	void permissionAndSkyFailuresAreTypedBeforeAdmissionSideEffects() {
		PlayerMock denied = server.addPlayer("Denied");
		denied.teleport(new Location(world, 30, 100, 30));

		DropOutcome.Rejected permission = assertInstanceOf(
				DropOutcome.Rejected.class,
				api.requestPlayerDrop(denied, "starter", DropRequestOptions.defaults())
						.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.INSUFFICIENT_PERMISSION,
				permission.rejection().reason());
		assertTrue(permission.context().isPresent());

		PlayerMock blocked = server.addPlayer("Blocked");
		blocked.setOp(true);
		world.getBlockAt(32, 90, 32).setType(Material.STONE);
		blocked.teleport(new Location(world, 32, 80, 32));
		DropOutcome.Rejected sky = assertInstanceOf(
				DropOutcome.Rejected.class,
				api.requestPlayerDrop(blocked, "starter", DropRequestOptions.defaults())
						.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.SKY_NOT_CLEAR, sky.rejection().reason());
		assertTrue(sky.context().isEmpty());
		assertEquals(0, Airdrop.getDropAdmissionController().snapshot().pending());
	}

	@Test
	void cooldownLocationAndLandedCapacityHaveDistinctTypedReasons() throws Exception {
		PlayerMock cooldownPlayer = server.addPlayer("Cooldown");
		cooldownPlayer.addAttachment(plugin, "airdrop.package.starter", true);
		cooldownPlayer.teleport(new Location(world, 40, 100, 40));
		DropHandle first = api.requestPlayerDrop(
				cooldownPlayer, "starter", quietOptions());
		first.spawn().toCompletableFuture().join();
		FallingBlock firstBlock = CrateManager.getCrateMap().keySet().iterator().next();
		CrateManager.removeCrateAndDestroy(firstBlock);
		cooldownPlayer.teleport(new Location(world, 41, 100, 41));

		DropOutcome.Rejected cooldown = assertInstanceOf(
				DropOutcome.Rejected.class,
				api.requestPlayerDrop(cooldownPlayer, "starter", quietOptions())
						.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.COOLDOWN, cooldown.rejection().reason());
		assertTrue(cooldown.rejection().retryAfter().orElseThrow().isPositive());

		DropAdmissionController admission = Airdrop.getDropAdmissionController();
		Location reservedRequest = new Location(world, 50, 100, 50);
		world.getBlockAt(50, 64, 50).setType(Material.STONE);
		Location reservedLanding = new Location(world, 50.5, 65, 50.5);
		admission.acquireSystem(
				DropLocationKey.from(reservedLanding), ConfigKeys.getDropLimitSettings());
		DropHandle locationHandle = api.requestSystemDrop(
				reservedRequest, "starter", quietOptions());
		assertTrue(locationHandle.outcome().toCompletableFuture().isDone(),
				() -> "reserved=" + DropLocationKey.from(reservedLanding)
						+ ", actual=" + DropLocationKey.from(
								locationHandle.context().orElseThrow().landingLocation())
						+ ", admission=" + admission.snapshot());
		DropOutcome.Rejected location = assertInstanceOf(
				DropOutcome.Rejected.class,
				locationHandle.outcome().toCompletableFuture().getNow(null));
		assertEquals(DropRejectionReason.LOCATION_RESERVED, location.rejection().reason());

		admission.clear();
		for (int index = 0; index < ConfigKeys.getDropLimitSettings().maxLanded(); index++) {
			admission.restoreLanded(new DropLocationKey(
					world.getUID(), 100 + index, 65, 100 + index));
		}
		DropHandle landedHandle = api.requestSystemDrop(
				new Location(world, 60, 100, 60), "starter", quietOptions());
		assertTrue(landedHandle.outcome().toCompletableFuture().isDone());
		DropOutcome.Rejected landed = assertInstanceOf(
				DropOutcome.Rejected.class,
				landedHandle.outcome().toCompletableFuture().getNow(null));
		assertEquals(DropRejectionReason.LANDED_CAPACITY, landed.rejection().reason());
	}

	@Test
	void invalidResolutionAndUnavailableLifecycleStatesAreTyped() {
		Package broken = mock(Package.class);
		when(broken.getName()).thenReturn("broken");
		when(broken.getPrice()).thenReturn(0.0);
		when(broken.getItems()).thenThrow(new IllegalStateException("items unavailable"));
		PackageManager.publishPackages(Map.of("broken", broken));

		DropOutcome.Rejected invalid = assertInstanceOf(
				DropOutcome.Rejected.class,
				api.requestSystemDrop(
						new Location(world, 70, 100, 70), "broken", quietOptions())
						.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.INVALID_TARGET, invalid.rejection().reason());

		DropRequestCoordinator unavailableCoordinator = new DropRequestCoordinator(plugin);
		DropOutcome.Rejected unavailable = assertInstanceOf(
				DropOutcome.Rejected.class,
				unavailableCoordinator.requestSystemDrop(
						new Location(world, 71, 100, 71), "broken", quietOptions())
						.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.SERVICE_UNAVAILABLE,
				unavailable.rejection().reason());

		plugin.onDisable();
		DropOutcome.Rejected stopping = assertInstanceOf(
				DropOutcome.Rejected.class,
				api.requestSystemDrop(
						new Location(world, 72, 100, 72), "broken", quietOptions())
						.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.SHUTTING_DOWN, stopping.rejection().reason());
	}

	@Test
	void landingFailureIsNotMisreportedAsCancellation() {
		DropHandle handle = api.requestSystemDrop(
				new Location(world, 80, 100, 80), "starter", quietOptions());
		handle.spawn().toCompletableFuture().join();
		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();
		EntityChangeBlockEvent wrongLanding = new EntityChangeBlockEvent(
				falling,
				world.getBlockAt(81, 1, 81),
				Material.BARREL.createBlockData());

		assertThrows(IllegalStateException.class,
				() -> server.getPluginManager().callEvent(wrongLanding));

		DropOutcome.Failed outcome = assertInstanceOf(
				DropOutcome.Failed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DeliveryStatus.FAILED, outcome.delivery());
	}

	private Airdrop preparedPlugin() throws Exception {
		Airdrop loaded = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(loaded.getDataFolder().toPath());
		Files.writeString(loaded.getDataFolder().toPath().resolve("config.yml"),
				"language: en\neconomy:\n  enabled: false\n",
				StandardCharsets.UTF_8);
		Files.writeString(loaded.getDataFolder().toPath().resolve("packages.yml"),
				"packages:\n  starter:\n    price: 0\n    items: []\n",
				StandardCharsets.UTF_8);
		return loaded;
	}

	private DropRequestOptions quietOptions() {
		return DropRequestOptions.defaults()
				.withDropHeight(20)
				.withChickenCount(1)
				.withFlareEffects(false)
				.withLandingEffects(false)
				.withContinuousEffects(false)
				.withSmokeEnabled(false);
	}

	private void awaitCondition(java.util.function.BooleanSupplier condition) {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			server.getScheduler().performOneTick();
			LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
		}
		assertTrue(condition.getAsBoolean(), "Timed out waiting for Airdrop startup");
	}
}
