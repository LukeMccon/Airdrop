package com.airdropmc.api.event;

import com.airdropmc.Airdrop;
import com.airdropmc.api.AirdropApi;
import com.airdropmc.api.DeliveryStatus;
import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.DropRejectionReason;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.api.PaymentStatus;
import com.airdropmc.economy.EconomyPlayer;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.economy.EconomyResult;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.packages.PackageManager;
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

class AirdropEventCancellationTest {

	private ServerMock server;
	private WorldMock world;
	private Airdrop plugin;
	private AirdropApi api;
	private ControlledEconomyProvider economy;
	private Canceller canceller;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("event_cancel_world");
		plugin = preparedPlugin();
		server.getPluginManager().enablePlugin(plugin);
		awaitCondition(() -> Airdrop.isReady() || !plugin.isEnabled());
		assertTrue(plugin.isEnabled());
		api = server.getServicesManager().load(AirdropApi.class);
		economy = new ControlledEconomyProvider();
		setStatic("economyProvider", economy);
		canceller = new Canceller();
		server.getPluginManager().registerEvents(canceller, plugin);
	}

	@AfterEach
	void tearDown() {
		CrateManager.clearAll();
		PackageManager.clear();
		MockBukkit.unmock();
	}

	@Test
	void requestCancellationPrecedesAdmissionEconomyEntityAndCooldownMutation() {
		PlayerMock player = server.addPlayer("Cancelled");
		player.setOp(true);
		player.teleport(new Location(world, 4, 100, 4));
		canceller.cancelRequest = true;

		DropHandle handle = api.requestPlayerDrop(player, "paid", quietOptions());

		DropOutcome.Rejected outcome = assertInstanceOf(
				DropOutcome.Rejected.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.CANCELLED, outcome.rejection().reason());
		assertEquals(List.of("request", "outcome"), canceller.names);
		assertEquals(0, economy.affordabilityChecks);
		assertTrue(CrateManager.getCrateMap().isEmpty());
		assertEquals(emptyAdmission(), Airdrop.getDropAdmissionController().snapshot());
	}

	@Test
	void landingAttemptCancellationRemovesFallingStateBeforeOneTerminalOutcome() {
		canceller.cancelLanding = true;
		DropHandle handle = api.requestSystemDrop(
				new Location(world, 8, 100, 8), "free", quietOptions());
		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();
		Location landingLocation = handle.context().orElseThrow().landingLocation();

		server.getPluginManager().callEvent(new EntityChangeBlockEvent(
				falling, landingLocation.getBlock(), Material.BARREL.createBlockData()));

		DropOutcome.Failed outcome = assertInstanceOf(
				DropOutcome.Failed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DeliveryStatus.CANCELLED, outcome.delivery());
		assertEquals(PaymentStatus.NOT_APPLICABLE, outcome.payment());
		assertEquals(List.of("request", "spawned", "landing-attempt", "outcome"),
				canceller.names);
		assertEquals(Material.AIR, landingLocation.getBlock().getType());
		assertTrue(CrateManager.getCrateMap().isEmpty());
		assertEquals(emptyAdmission(), Airdrop.getDropAdmissionController().snapshot());
	}

	@Test
	void paidLandingCancellationWaitsForRefundAndIgnoresDuplicateLateCallbacks() {
		PlayerMock player = server.addPlayer("PaidCancelled");
		player.setOp(true);
		player.teleport(new Location(world, 12, 100, 12));
		DropHandle handle = api.requestPlayerDrop(player, "paid", quietOptions());
		completeCharge();
		canceller.cancelLanding = true;
		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();
		Location landingLocation = handle.context().orElseThrow().landingLocation();
		EntityChangeBlockEvent landing = new EntityChangeBlockEvent(
				falling, landingLocation.getBlock(), Material.BARREL.createBlockData());

		server.getPluginManager().callEvent(landing);

		assertEquals(1, economy.deposits);
		assertFalse(handle.outcome().toCompletableFuture().isDone());
		assertEquals(0, canceller.outcomeCount);
		server.getPluginManager().callEvent(landing);
		assertEquals(1, economy.deposits);

		economy.refund.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		DropOutcome.Failed outcome = assertInstanceOf(
				DropOutcome.Failed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DeliveryStatus.CANCELLED, outcome.delivery());
		assertEquals(PaymentStatus.REFUNDED, outcome.payment());
		assertEquals(1, canceller.outcomeCount);
		assertTrue(CrateManager.getCrateMap().isEmpty());
	}

	@Test
	void admissionAndPaymentRejectionsPublishRequestThenOutcomeOnly() {
		PlayerMock player = server.addPlayer("Rejected");
		player.setOp(true);
		player.teleport(new Location(world, 16, 100, 16));
		DropHandle pending = api.requestPlayerDrop(player, "paid", quietOptions());
		assertFalse(pending.outcome().toCompletableFuture().isDone());
		assertEquals(List.of("request"), canceller.names);
		canceller.names.clear();

		DropOutcome.Rejected duplicate = assertInstanceOf(
				DropOutcome.Rejected.class,
				api.requestPlayerDrop(player, "paid", quietOptions())
						.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.REQUEST_PENDING, duplicate.rejection().reason());
		assertEquals(List.of("request", "outcome"), canceller.names);

		canceller.names.clear();
		economy.affordability.complete(EconomyResult.rejected("insufficient"));
		server.getScheduler().performOneTick();
		DropOutcome.Rejected payment = assertInstanceOf(
				DropOutcome.Rejected.class, pending.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.INSUFFICIENT_FUNDS, payment.rejection().reason());
		assertEquals(List.of("outcome"), canceller.names);
	}

	private Airdrop preparedPlugin() throws Exception {
		Airdrop loaded = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(loaded.getDataFolder().toPath());
		Files.writeString(loaded.getDataFolder().toPath().resolve("config.yml"),
				"language: en\neconomy:\n  enabled: true\n",
				StandardCharsets.UTF_8);
		Files.writeString(loaded.getDataFolder().toPath().resolve("packages.yml"),
				"packages:\n"
						+ "  paid:\n    price: 10\n    items:\n"
						+ "      - ==: org.bukkit.inventory.ItemStack\n"
						+ "        schema_version: 1\n        id: minecraft:diamond\n        count: 1\n"
						+ "  free:\n    price: 0\n    items: []\n",
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

	private void completeCharge() {
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		economy.withdrawal.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
	}

	private DropAdmissionController.Snapshot emptyAdmission() {
		return new DropAdmissionController.Snapshot(0, 0, 0, 0, 0, true);
	}

	private void setStatic(String fieldName, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
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

	private static final class Canceller implements Listener {
		private final List<String> names = new ArrayList<>();
		private boolean cancelRequest;
		private boolean cancelLanding;
		private int outcomeCount;

		@EventHandler
		public void onRequest(AirdropRequestEvent event) {
			names.add("request");
			event.setCancelled(cancelRequest);
		}

		@EventHandler
		public void onSpawned(AirdropSpawnedEvent event) {
			names.add("spawned");
		}

		@EventHandler
		public void onLandingAttempt(AirdropLandingAttemptEvent event) {
			names.add("landing-attempt");
			event.setCancelled(cancelLanding);
		}

		@EventHandler
		public void onLanded(AirdropLandedEvent event) {
			names.add("landed");
		}

		@EventHandler
		public void onOutcome(AirdropOutcomeEvent event) {
			names.add("outcome");
			outcomeCount++;
		}
	}

	private static final class ControlledEconomyProvider implements EconomyProvider {

		private final CompletableFuture<EconomyResult> affordability = new CompletableFuture<>();
		private final CompletableFuture<EconomyResult> withdrawal = new CompletableFuture<>();
		private final CompletableFuture<EconomyResult> refund = new CompletableFuture<>();
		private int affordabilityChecks;
		private int deposits;

		@Override
		public boolean nativeAsync() {
			return true;
		}

		@Override
		public CompletionStage<EconomyResult> canAfford(EconomyPlayer player, BigDecimal amount) {
			affordabilityChecks++;
			return affordability;
		}

		@Override
		public CompletionStage<EconomyResult> withdraw(EconomyPlayer player, BigDecimal amount) {
			return withdrawal;
		}

		@Override
		public CompletionStage<EconomyResult> deposit(EconomyPlayer player, BigDecimal amount) {
			deposits++;
			return refund;
		}

		@Override
		public String getName() {
			return "Controlled";
		}
	}
}
