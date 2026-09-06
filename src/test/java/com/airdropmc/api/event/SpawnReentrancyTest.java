package com.airdropmc.api.event;

import com.airdropmc.Airdrop;
import com.airdropmc.api.AirdropApi;
import com.airdropmc.api.DeliveryStatus;
import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.api.DropSpawnResult;
import com.airdropmc.api.PaymentStatus;
import com.airdropmc.api.RetirementReason;
import com.airdropmc.economy.EconomyPlayer;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.economy.EconomyResult;
import com.airdropmc.events.PackageDropEvent;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.packages.PackageManager;
import org.bukkit.Location;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnReentrancyTest {

	private ServerMock server;
	private WorldMock world;
	private Airdrop plugin;
	private AirdropApi api;
	private ImmediateEconomyProvider economy;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock(new ServerMock() {
			@Override
			public boolean isStopping() {
				return false;
			}
		});
		world = server.addSimpleWorld("spawn_reentrancy_world");
		plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(plugin.getDataFolder().toPath());
		Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"),
				"language: en\neconomy:\n  enabled: true\n", StandardCharsets.UTF_8);
		Files.writeString(plugin.getDataFolder().toPath().resolve("packages.yml"),
				"packages:\n  paid:\n    price: 10\n    items: []\n"
						+ "  free:\n    price: 0\n    items: []\n", StandardCharsets.UTF_8);
		server.getPluginManager().enablePlugin(plugin);
		awaitCondition(() -> Airdrop.isReady() || !plugin.isEnabled());
		assertTrue(plugin.isEnabled());
		api = server.getServicesManager().load(AirdropApi.class);
		economy = new ImmediateEconomyProvider();
		Field economyField = Airdrop.class.getDeclaredField("economyProvider");
		economyField.setAccessible(true);
		economyField.set(null, economy);
	}

	@AfterEach
	void tearDown() {
		CrateManager.clearAll();
		PackageManager.clear();
		MockBukkit.unmock();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void disablingFromSpawnedEventDoesNotPublishLegacySpawnAfterRetirement(boolean paid) {
		ShutdownObserver observer = registerObserver(true);

		DropHandle handle = request(paid);
		awaitCondition(() -> handle.outcome().toCompletableFuture().isDone());

		assertShutdown(handle, observer, paid);
		assertEquals(List.of("spawned", "outcome", "retired"), observer.events);
	}

	@Test
	void disablingFromSpawnCompletionDoesNotPublishSpawnEventsAfterRetirement() {
		ShutdownObserver observer = registerObserver(false);
		DropHandle handle = request(true);
		assertFalse(handle.spawn().toCompletableFuture().isDone());
		CompletableFuture<Void> disableCompletion = handle.spawn().thenAccept(spawn -> {
			observer.events.add("spawn-stage");
			observer.falling = CrateManager.getCrateMap().keySet().iterator().next();
			server.getPluginManager().disablePlugin(plugin);
		}).toCompletableFuture();

		awaitCondition(disableCompletion::isDone);
		disableCompletion.join();

		assertShutdown(handle, observer, true);
		assertEquals(List.of("spawn-stage", "outcome", "retired"), observer.events);
	}

	private DropHandle request(boolean paid) {
		PlayerMock player = server.addPlayer("SpawnRecipient");
		player.setOp(true);
		player.teleport(new Location(world, 4, 100, 4));
		return api.requestPlayerDrop(player, paid ? "paid" : "free",
				DropRequestOptions.defaults().withChickenCount(1)
						.withFlareEffects(false).withLandingEffects(false)
						.withContinuousEffects(false).withSmokeEnabled(false));
	}

	private ShutdownObserver registerObserver(boolean disableFromSpawnedEvent) {
		ShutdownObserver observer = new ShutdownObserver(disableFromSpawnedEvent);
		PluginMock observerPlugin = MockBukkit.createMockPlugin("SpawnObserver");
		server.getPluginManager().registerEvents(observer, observerPlugin);
		server.getPluginManager().registerEvent(PackageDropEvent.class, observer, EventPriority.NORMAL,
				(listener, event) -> observer.onLegacySpawn((PackageDropEvent) event), observerPlugin);
		return observer;
	}

	private void assertShutdown(DropHandle handle, ShutdownObserver observer, boolean paid) {
		assertFalse(plugin.isEnabled());
		CompletableFuture<DropSpawnResult> spawned = handle.spawn().toCompletableFuture();
		CompletableFuture<DropOutcome> terminal = handle.outcome().toCompletableFuture();
		assertTrue(spawned.isDone());
		assertInstanceOf(DropSpawnResult.Spawned.class, spawned.join());
		assertTrue(terminal.isDone());
		DropOutcome.Failed outcome = assertInstanceOf(DropOutcome.Failed.class, terminal.join());
		assertEquals(DeliveryStatus.SHUTDOWN, outcome.delivery());
		assertEquals(paid ? PaymentStatus.CHARGED : PaymentStatus.NOT_APPLICABLE, outcome.payment());
		assertEquals(List.of(outcome), observer.outcomes);
		assertEquals(List.of(RetirementReason.HOT_DISABLE), observer.retirements);
		assertTrue(observer.falling.isDead());
		assertEquals(paid ? 1 : 0, economy.withdrawals);
		assertEquals(0, economy.refunds);
		server.getScheduler().performTicks(3);
		assertTrue(CrateManager.getCrateMap().isEmpty());
		assertTrue(api.activeDrops().isEmpty());
		assertEquals(0, api.status().pendingCount());
		assertEquals(List.of(outcome), observer.outcomes);
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
		assertTrue(condition.getAsBoolean(), "Timed out waiting for Airdrop");
	}

	private final class ShutdownObserver implements Listener {
		private final boolean disableFromSpawnedEvent;
		private final List<String> events = new ArrayList<>();
		private final List<DropOutcome> outcomes = new ArrayList<>();
		private final List<RetirementReason> retirements = new ArrayList<>();
		private FallingBlock falling;

		private ShutdownObserver(boolean disableFromSpawnedEvent) {
			this.disableFromSpawnedEvent = disableFromSpawnedEvent;
		}

		@EventHandler
		public void onSpawned(AirdropSpawnedEvent event) {
			events.add("spawned");
			if (disableFromSpawnedEvent) {
				falling = CrateManager.getCrateMap().keySet().iterator().next();
				server.getPluginManager().disablePlugin(plugin);
			}
		}

		public void onLegacySpawn(PackageDropEvent event) {
			events.add("legacy-spawn");
		}

		@EventHandler
		public void onOutcome(AirdropOutcomeEvent event) {
			events.add("outcome");
			outcomes.add(event.outcome());
		}

		@EventHandler
		public void onRetired(AirdropRetiredEvent event) {
			events.add("retired");
			retirements.add(event.reason());
		}
	}

	private static final class ImmediateEconomyProvider implements EconomyProvider {
		private int withdrawals;
		private int refunds;

		@Override
		public boolean nativeAsync() {
			return false;
		}

		@Override
		public CompletionStage<EconomyResult> canAfford(EconomyPlayer player, BigDecimal amount) {
			return CompletableFuture.completedFuture(EconomyResult.ok());
		}

		@Override
		public CompletionStage<EconomyResult> withdraw(EconomyPlayer player, BigDecimal amount) {
			withdrawals++;
			return CompletableFuture.completedFuture(EconomyResult.ok());
		}

		@Override
		public CompletionStage<EconomyResult> deposit(EconomyPlayer player, BigDecimal amount) {
			refunds++;
			return CompletableFuture.completedFuture(EconomyResult.ok());
		}

		@Override
		public String getName() {
			return "Immediate";
		}
	}
}
