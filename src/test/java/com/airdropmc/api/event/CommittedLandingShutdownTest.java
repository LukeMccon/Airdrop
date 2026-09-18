package com.airdropmc.api.event;

import com.airdropmc.Airdrop;
import com.airdropmc.api.AirdropApi;
import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.api.PaymentStatus;
import com.airdropmc.economy.EconomyPlayer;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.economy.EconomyResult;
import com.airdropmc.events.PackageLandEvent;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.packages.PackageManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

class CommittedLandingShutdownTest {

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
		world = server.addSimpleWorld("committed_landing_world");
		plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(plugin.getDataFolder().toPath());
		Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"),
				"language: en\neconomy:\n  enabled: true\n", StandardCharsets.UTF_8);
		Files.writeString(plugin.getDataFolder().toPath().resolve("packages.yml"),
				"packages:\n  paid:\n    price: 10\n    items:\n"
						+ "      - ==: org.bukkit.inventory.ItemStack\n"
						+ "        schema_version: 1\n        id: minecraft:diamond\n        count: 1\n"
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
	void disablingFromLandedEventPreservesCommittedDeliveryAndEventOrder(boolean paid) {
		PlayerMock player = server.addPlayer("LandingRecipient");
		player.setOp(true);
		player.teleport(new Location(world, 4, 100, 4));
		DropHandle handle = api.requestPlayerDrop(player, paid ? "paid" : "free",
				DropRequestOptions.defaults().withChickenCount(1)
						.withFlareEffects(false).withLandingEffects(false)
						.withContinuousEffects(false).withSmokeEnabled(false));
		awaitCondition(() -> handle.spawn().toCompletableFuture().isDone());
		ShutdownObserver observer = new ShutdownObserver();
		PluginMock observerPlugin = MockBukkit.createMockPlugin("LandingObserver");
		server.getPluginManager().registerEvents(observer, observerPlugin);
		server.getPluginManager().registerEvent(PackageLandEvent.class, observer, EventPriority.NORMAL,
				(listener, event) -> observer.onLegacyLand((PackageLandEvent) event), observerPlugin);
		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();

		server.getPluginManager().callEvent(new EntityChangeBlockEvent(
				falling, handle.context().orElseThrow().landingLocation().getBlock(),
				Material.BARREL.createBlockData()));

		assertFalse(plugin.isEnabled());
		CompletableFuture<DropOutcome> terminal = handle.outcome().toCompletableFuture();
		assertTrue(terminal.isDone(), "Committed landing must complete after synchronous events");
		DropOutcome.Landed outcome = assertInstanceOf(
				DropOutcome.Landed.class, terminal.join());
		assertEquals(paid ? PaymentStatus.CHARGED : PaymentStatus.NOT_APPLICABLE, outcome.payment());
		assertEquals(List.of("landed", "legacy-land", "outcome"), observer.events);
		assertEquals(List.of(outcome), observer.outcomes);
		assertEquals(paid ? 1 : 0, economy.withdrawals);
		assertEquals(0, economy.refunds);
		assertTrue(api.activeDrops().isEmpty());
		assertEquals(0, api.status().pendingCount());
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
		private final List<String> events = new ArrayList<>();
		private final List<DropOutcome> outcomes = new ArrayList<>();

		@EventHandler
		public void onLanded(AirdropLandedEvent event) {
			events.add("landed");
			assertEquals(Material.BARREL, event.context().landingLocation().getBlock().getType());
			server.getPluginManager().disablePlugin(plugin);
		}

		public void onLegacyLand(PackageLandEvent event) {
			events.add("legacy-land");
		}

		@EventHandler
		public void onOutcome(AirdropOutcomeEvent event) {
			events.add("outcome");
			outcomes.add(event.outcome());
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
