package com.airdropmc.api.event;

import com.airdropmc.Airdrop;
import com.airdropmc.api.AirdropApi;
import com.airdropmc.api.DeliveryStatus;
import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.DropRejectionReason;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.events.PackageDropEvent;
import com.airdropmc.events.PackageLandEvent;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.packages.PackageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirdropEventOrderTest {

	private ServerMock server;
	private WorldMock world;
	private Airdrop plugin;
	private AirdropApi api;
	private EventRecorder recorder;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("event_order_world");
		plugin = preparedPlugin();
		server.getPluginManager().enablePlugin(plugin);
		awaitCondition(() -> Airdrop.isReady() || !plugin.isEnabled());
		assertTrue(plugin.isEnabled());
		api = server.getServicesManager().load(AirdropApi.class);
		recorder = new EventRecorder();
		server.getPluginManager().registerEvents(recorder, plugin);
	}

	@AfterEach
	void tearDown() {
		CrateManager.clearAll();
		PackageManager.clear();
		MockBukkit.unmock();
	}

	@Test
	void successfulRequestPublishesTheExactCorrelatedLifecycleOrder() {
		DropHandle handle = api.requestSystemDrop(
				new Location(world, 5, 100, 7), "starter", quietOptions());

		assertEquals(List.of("request", "spawned", "legacy-drop"), recorder.names);
		assertTrue(handle.spawn().toCompletableFuture().isDone());
		assertTrue(!handle.outcome().toCompletableFuture().isDone());

		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();
		server.getPluginManager().callEvent(new EntityChangeBlockEvent(
				falling,
				handle.context().orElseThrow().landingLocation().getBlock(),
				Material.BARREL.createBlockData()));

		assertEquals(List.of(
				"request", "spawned", "legacy-drop", "landing-attempt",
				"landed", "legacy-land", "outcome"), recorder.names);
		assertEquals(5, recorder.supportedRequestIds.size());
		assertTrue(recorder.supportedRequestIds.stream().allMatch(handle.requestId()::equals));
		assertTrue(recorder.primaryThreadOnly);
		assertInstanceOf(
				DropOutcome.Landed.class, handle.outcome().toCompletableFuture().join());
	}

	@Test
	void resolutionFailuresPublishOnlyTheTypedHandleOutcome() {
		DropHandle unknown = api.requestSystemDrop(
				new Location(world, 2, 100, 3), "missing", quietOptions());
		DropOutcome.Rejected unknownOutcome = assertInstanceOf(
				DropOutcome.Rejected.class, unknown.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.UNKNOWN_PACKAGE, unknownOutcome.rejection().reason());
		assertTrue(recorder.names.isEmpty());

		world.getBlockAt(8, 90, 8).setType(Material.STONE);
		DropHandle blocked = api.requestSystemDrop(
				new Location(world, 8, 80, 8), "starter", quietOptions());
		DropOutcome.Rejected blockedOutcome = assertInstanceOf(
				DropOutcome.Rejected.class, blocked.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.SKY_NOT_CLEAR, blockedOutcome.rejection().reason());
		assertTrue(recorder.names.isEmpty());
	}

	@Test
	void resolvedPermissionRejectionPublishesRequestThenOutcomeOnly() {
		PlayerMock denied = server.addPlayer("Denied");
		denied.teleport(new Location(world, 12, 100, 12));

		DropHandle handle = api.requestPlayerDrop(denied, "starter", quietOptions());

		DropOutcome.Rejected outcome = assertInstanceOf(
				DropOutcome.Rejected.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.INSUFFICIENT_PERMISSION, outcome.rejection().reason());
		assertEquals(List.of("request", "outcome"), recorder.names);
		assertEquals(List.of(handle.requestId(), handle.requestId()), recorder.supportedRequestIds);
		assertTrue(CrateManager.getCrateMap().isEmpty());
	}

	@Test
	void paperPreCancellationSkipsSupportedLandingAttemptAndAllLandedEvents() {
		DropHandle handle = api.requestSystemDrop(
				new Location(world, 16, 100, 16), "starter", quietOptions());
		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();
		EntityChangeBlockEvent landing = new EntityChangeBlockEvent(
				falling,
				handle.context().orElseThrow().landingLocation().getBlock(),
				Material.BARREL.createBlockData());
		landing.setCancelled(true);

		server.getPluginManager().callEvent(landing);

		assertEquals(List.of("request", "spawned", "legacy-drop", "outcome"), recorder.names);
		DropOutcome.Failed outcome = assertInstanceOf(
				DropOutcome.Failed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DeliveryStatus.CANCELLED, outcome.delivery());
	}

	@Test
	void landingFailurePublishesAttemptAndOneOutcomeButNoLandedEvent() {
		DropHandle handle = api.requestSystemDrop(
				new Location(world, 20, 100, 20), "starter", quietOptions());
		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();

		try {
			server.getPluginManager().callEvent(new EntityChangeBlockEvent(
					falling,
					world.getBlockAt(21, 1, 21),
					Material.BARREL.createBlockData()));
		} catch (RuntimeException expected) {
			// The legacy listener contract still propagates a failed landing.
		}

		assertEquals(List.of(
				"request", "spawned", "legacy-drop", "landing-attempt", "outcome"),
				recorder.names);
		DropOutcome.Failed outcome = assertInstanceOf(
				DropOutcome.Failed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DeliveryStatus.FAILED, outcome.delivery());
	}

	@Test
	void terminalEventsAreNeverFiredWhileCrateManagerMonitorIsHeld() {
		DropHandle handle = api.requestSystemDrop(
				new Location(world, 24, 100, 24), "starter", quietOptions());
		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();

		CrateManager.removeCrateAndDestroy(falling);

		assertTrue(handle.outcome().toCompletableFuture().isDone());
		assertTrue(!recorder.outcomeObservedInsideManagerMonitor);

		recorder.outcomeObservedInsideManagerMonitor = false;
		DropHandle cleared = api.requestSystemDrop(
				new Location(world, 26, 100, 26), "starter", quietOptions());
		CrateManager.clearAll();

		assertTrue(cleared.outcome().toCompletableFuture().isDone());
		assertTrue(!recorder.outcomeObservedInsideManagerMonitor);

		recorder.outcomeObservedInsideManagerMonitor = false;
		DropHandle chunkRemoved = api.requestSystemDrop(
				new Location(world, 28, 100, 28), "starter", quietOptions());
		CrateManager.removeFallingCratesInChunk(world.getChunkAt(1, 1));

		assertTrue(chunkRemoved.outcome().toCompletableFuture().isDone());
		assertTrue(!recorder.outcomeObservedInsideManagerMonitor);

		recorder.outcomeObservedInsideManagerMonitor = false;
		DropHandle chunkPrepared = api.requestSystemDrop(
				new Location(world, 36, 100, 36), "starter", quietOptions());
		CrateManager.prepareChunkForUnload(world.getChunkAt(2, 2));

		assertTrue(chunkPrepared.outcome().toCompletableFuture().isDone());
		assertTrue(!recorder.outcomeObservedInsideManagerMonitor);
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

	private static final class EventRecorder implements Listener {
		private final List<String> names = new ArrayList<>();
		private final List<UUID> supportedRequestIds = new ArrayList<>();
		private boolean primaryThreadOnly = true;
		private boolean outcomeObservedInsideManagerMonitor;

		private void record(String name, AbstractAirdropEvent event) {
			names.add(name);
			supportedRequestIds.add(event.requestId());
			primaryThreadOnly &= Bukkit.isPrimaryThread() && !event.isAsynchronous();
		}

		@EventHandler
		public void onRequest(AirdropRequestEvent event) {
			record("request", event);
		}

		@EventHandler
		public void onSpawned(AirdropSpawnedEvent event) {
			record("spawned", event);
		}

		@EventHandler
		public void onLegacyDrop(PackageDropEvent event) {
			names.add("legacy-drop");
		}

		@EventHandler
		public void onLandingAttempt(AirdropLandingAttemptEvent event) {
			record("landing-attempt", event);
		}

		@EventHandler
		public void onLanded(AirdropLandedEvent event) {
			record("landed", event);
		}

		@EventHandler
		public void onLegacyLand(PackageLandEvent event) {
			names.add("legacy-land");
		}

		@EventHandler
		public void onOutcome(AirdropOutcomeEvent event) {
			record("outcome", event);
			outcomeObservedInsideManagerMonitor |= Thread.holdsLock(CrateManager.class);
		}
	}
}
