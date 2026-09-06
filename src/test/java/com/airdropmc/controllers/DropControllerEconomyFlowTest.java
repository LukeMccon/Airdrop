package com.airdropmc.controllers;

import com.airdropmc.Airdrop;
import com.airdropmc.Crate;
import com.airdropmc.api.DeliveryStatus;
import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.DropRejectionReason;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.api.DropSpawnResult;
import com.airdropmc.api.PaymentStatus;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.config.DropOptions;
import com.airdropmc.economy.EconomyPlayer;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.economy.EconomyResult;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLocationKey;
import com.airdropmc.packages.PackageManager;
import com.airdropmc.packages.Package;
import com.airdropmc.paid.PaidDropSession;
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
import org.mockito.MockedConstruction;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.LockSupport;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;

class DropControllerEconomyFlowTest {

	private ServerMock server;
	private WorldMock world;
	private Airdrop plugin;
	private ControlledEconomyProvider economy;
	private PlayerMock player;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("economy_world");
		plugin = preparedPlugin();
		server.getPluginManager().enablePlugin(plugin);
		awaitCondition(() -> Airdrop.isReady() || !plugin.isEnabled());
		assertTrue(plugin.isEnabled());
		economy = new ControlledEconomyProvider();
		setStatic("economyProvider", economy);
		player = server.addPlayer("Luke");
		player.setOp(true);
		player.teleport(new Location(world, 0, 120, 0));
	}

	@AfterEach
	void tearDown() {
		CrateManager.clearAll();
		PackageManager.clear();
		MockBukkit.unmock();
	}

	@Test
	void confirmedPaymentCreatesOneCorrelatedFallingCrate() {
		DropHandle handle = request("paid");
		completeCharge();

		DropSpawnResult.Spawned spawned = assertInstanceOf(
				DropSpawnResult.Spawned.class, handle.spawn().toCompletableFuture().join());
		assertEquals(PaymentStatus.CHARGED, spawned.payment());
		assertEquals(handle.requestId(), spawned.requestId());
		assertEquals(1, CrateManager.getCrateMap().size());
		assertTrue(CrateManager.getCrateMap().values().iterator().next().isPaid());
	}

	@Test
	void fallingCrateFailureRequestsOneRefundAndReportsRefunded() {
		DropHandle handle = request("paid");
		completeCharge();
		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();

		CrateManager.removeCrateAndDestroy(falling);
		CrateManager.removeCrateAndDestroy(falling);
		assertEquals(1, economy.deposits);
		economy.refund.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		DropOutcome.Failed outcome = assertInstanceOf(
				DropOutcome.Failed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DeliveryStatus.FAILED, outcome.delivery());
		assertEquals(PaymentStatus.REFUNDED, outcome.payment());
	}

	@Test
	void landedPaidCrateCleanupCannotReplayOutcomeOrStartRefund() {
		DropHandle handle = request("paid");
		completeCharge();
		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();
		EntityChangeBlockEvent landing = new EntityChangeBlockEvent(
				falling,
				handle.context().orElseThrow().landingLocation().getBlock(),
				Material.BARREL.createBlockData());
		server.getPluginManager().callEvent(landing);

		DropOutcome.Landed outcome = assertInstanceOf(
				DropOutcome.Landed.class, handle.outcome().toCompletableFuture().join());
		CrateManager.removeCrateAndDestroy(handle.context().orElseThrow().landingLocation());

		assertTrue(handle.outcome().toCompletableFuture().join() == outcome);
		assertEquals(0, economy.deposits);
	}

	@Test
	void insufficientFundsIsTypedAndReleasesAdmission() {
		DropHandle handle = request("paid");
		economy.affordability.complete(EconomyResult.rejected("insufficient"));
		server.getScheduler().performOneTick();

		DropOutcome.Rejected outcome = assertInstanceOf(
				DropOutcome.Rejected.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.INSUFFICIENT_FUNDS, outcome.rejection().reason());
		assertEquals(PaymentStatus.REJECTED, outcome.payment());
		assertEquals(0, economy.withdrawals);
		assertEquals(emptyAdmission(), Airdrop.getDropAdmissionController().snapshot());
	}

	@Test
	void ambiguousWithdrawalIsFailedUnknownAndNeverRefunded() {
		DropHandle handle = request("paid");
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		economy.withdrawal.completeExceptionally(new IllegalStateException("offline"));
		server.getScheduler().performOneTick();

		DropOutcome.Failed outcome = assertInstanceOf(
				DropOutcome.Failed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DeliveryStatus.FAILED, outcome.delivery());
		assertEquals(PaymentStatus.UNKNOWN, outcome.payment());
		assertEquals(0, economy.deposits);
		assertTrue(CrateManager.getCrateMap().isEmpty());
	}

	@Test
	void rejectedWithdrawalIsKnownUnchargedRejection() {
		DropHandle handle = request("paid");
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		economy.withdrawal.complete(EconomyResult.rejected("declined"));
		server.getScheduler().performOneTick();

		DropOutcome.Rejected outcome = assertInstanceOf(
				DropOutcome.Rejected.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.PAYMENT_REJECTED, outcome.rejection().reason());
		assertEquals(PaymentStatus.REJECTED, outcome.payment());
		assertEquals(emptyAdmission(), Airdrop.getDropAdmissionController().snapshot());
	}

	@Test
	void affordabilityTimeoutIsKnownUnchargedRejection() {
		DropHandle handle = request("paid");

		server.getScheduler().performTicks(PaidDropSession.PAYMENT_TIMEOUT_TICKS);

		DropOutcome.Rejected outcome = assertInstanceOf(
				DropOutcome.Rejected.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.AFFORDABILITY_UNKNOWN, outcome.rejection().reason());
		assertEquals(PaymentStatus.REJECTED, outcome.payment());
		assertEquals(0, economy.withdrawals);
	}

	@Test
	void withdrawalTimeoutIsFailedUnknownAndNeverRefunded() {
		DropHandle handle = request("paid");
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		server.getScheduler().performTicks(PaidDropSession.PAYMENT_TIMEOUT_TICKS);

		DropOutcome.Failed outcome = assertInstanceOf(
				DropOutcome.Failed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DeliveryStatus.FAILED, outcome.delivery());
		assertEquals(PaymentStatus.UNKNOWN, outcome.payment());
		assertEquals(0, economy.deposits);
	}

	@Test
	void pendingPaymentReservesPlayerAndRejectsDuplicateRequest() {
		DropHandle first = request("paid");
		DropHandle second = request("paid");

		assertFalse(first.outcome().toCompletableFuture().isDone());
		DropOutcome.Rejected rejection = assertInstanceOf(
				DropOutcome.Rejected.class, second.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.REQUEST_PENDING, rejection.rejection().reason());
		assertEquals(1, Airdrop.getDropAdmissionController().snapshot().pending());
	}

	@Test
	void disabledEconomyRejectsPaidButStillAllowsFreePackage() {
		Airdrop.getConfiguration().getConfig().set(ConfigKeys.ECONOMY_ENABLED, false);

		DropOutcome.Rejected paid = assertInstanceOf(
				DropOutcome.Rejected.class, request("paid").outcome().toCompletableFuture().join());
		DropSpawnResult.Spawned free = assertInstanceOf(
				DropSpawnResult.Spawned.class, request("free").spawn().toCompletableFuture().join());

		assertEquals(DropRejectionReason.ECONOMY_DISABLED, paid.rejection().reason());
		assertEquals(PaymentStatus.NOT_APPLICABLE, free.payment());
		assertEquals(0, economy.affordabilityChecks);
	}

	@Test
	void freePlayerRequestDoesNotRequireEconomyProvider() throws Exception {
		setStatic("economyProvider", null);

		DropSpawnResult.Spawned spawned = assertInstanceOf(
				DropSpawnResult.Spawned.class, request("free").spawn().toCompletableFuture().join());

		assertEquals(PaymentStatus.NOT_APPLICABLE, spawned.payment());
	}

	@Test
	void systemRequestForPricedPackageIsExplicitlyUnpaid() {
		DropHandle handle = DropController.requestSystemDrop(
				new Location(world, 20, 120, 20), "paid", options());

		DropSpawnResult.Spawned spawned = assertInstanceOf(
				DropSpawnResult.Spawned.class, handle.spawn().toCompletableFuture().join());
		assertEquals(PaymentStatus.NOT_APPLICABLE, spawned.payment());
		assertEquals(0, economy.affordabilityChecks);
	}

	@Test
	void missingProviderIsTypedBeforePayment() throws Exception {
		setStatic("economyProvider", null);

		DropOutcome.Rejected outcome = assertInstanceOf(
				DropOutcome.Rejected.class, request("paid").outcome().toCompletableFuture().join());

		assertEquals(DropRejectionReason.ECONOMY_PROVIDER_UNAVAILABLE,
				outcome.rejection().reason());
		assertEquals(PaymentStatus.REJECTED, outcome.payment());
	}

	@Test
	void capacityRejectionOccursBeforePayment() throws Exception {
		DropAdmissionController admission = Airdrop.getDropAdmissionController();
		for (int index = 0; index < ConfigKeys.getDropLimitSettings().maxFalling(); index++) {
			admission.acquireSystem(
					new DropLocationKey(world.getUID(), 100 + index, 65, 100 + index),
					ConfigKeys.getDropLimitSettings());
		}

		DropOutcome.Rejected outcome = assertInstanceOf(
				DropOutcome.Rejected.class, request("paid").outcome().toCompletableFuture().join());

		assertEquals(DropRejectionReason.FALLING_CAPACITY, outcome.rejection().reason());
		assertEquals(0, economy.affordabilityChecks);
	}

	@Test
	void affordabilityPendingAtShutdownIsKnownUnchargedRejection() {
		DropHandle handle = request("paid");

		plugin.onDisable();

		DropOutcome.Rejected outcome = assertInstanceOf(
				DropOutcome.Rejected.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.SHUTTING_DOWN, outcome.rejection().reason());
		assertEquals(PaymentStatus.REJECTED, outcome.payment());
	}

	@Test
	void withdrawalPendingAtShutdownIsFailedUnknown() {
		DropHandle handle = request("paid");
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		plugin.onDisable();

		DropOutcome.Failed outcome = assertInstanceOf(
				DropOutcome.Failed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DeliveryStatus.SHUTDOWN, outcome.delivery());
		assertEquals(PaymentStatus.UNKNOWN, outcome.payment());
		assertEquals(0, economy.deposits);

		economy.withdrawal.complete(EconomyResult.ok());
		assertTrue(handle.outcome().toCompletableFuture().join() == outcome);
		assertTrue(CrateManager.getCrateMap().isEmpty());
		assertEquals(0, economy.deposits);
	}

	@Test
	void queuedBackgroundAffordabilityCannotAdvanceAfterShutdown() throws Exception {
		DropHandle handle = request("paid");
		Thread callback = new Thread(
				() -> economy.affordability.complete(EconomyResult.ok()),
				"queued-affordability");
		callback.start();
		callback.join();

		plugin.onDisable();

		DropOutcome.Rejected outcome = assertInstanceOf(
				DropOutcome.Rejected.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DropRejectionReason.SHUTTING_DOWN, outcome.rejection().reason());
		assertEquals(PaymentStatus.REJECTED, outcome.payment());
		assertEquals(0, economy.withdrawals);
		assertTrue(CrateManager.getCrateMap().isEmpty());
	}

	@Test
	void confirmedChargeAtShutdownStaysChargedWithoutAutomaticRefund() {
		DropHandle handle = request("paid");
		completeCharge();

		plugin.onDisable();

		DropOutcome.Failed outcome = assertInstanceOf(
				DropOutcome.Failed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DeliveryStatus.SHUTDOWN, outcome.delivery());
		assertEquals(PaymentStatus.CHARGED, outcome.payment());
		assertEquals(0, economy.deposits);
	}

	@Test
	void freeFallingDropAtShutdownIsNotApplicable() {
		DropHandle handle = request("free");
		handle.spawn().toCompletableFuture().join();

		plugin.onDisable();

		DropOutcome.Failed outcome = assertInstanceOf(
				DropOutcome.Failed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DeliveryStatus.SHUTDOWN, outcome.delivery());
		assertEquals(PaymentStatus.NOT_APPLICABLE, outcome.payment());
	}

	@Test
	void refundPendingAtShutdownIsFailedUnknownAndNotRetried() {
		DropHandle handle = request("paid");
		completeCharge();
		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();
		CrateManager.removeCrateAndDestroy(falling);
		assertEquals(1, economy.deposits);

		plugin.onDisable();

		DropOutcome.Failed outcome = assertInstanceOf(
				DropOutcome.Failed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DeliveryStatus.FAILED, outcome.delivery());
		assertEquals(PaymentStatus.UNKNOWN, outcome.payment());
		assertEquals(1, economy.deposits);
	}

	@Test
	void paidSpawnFailureRunsExactlyOneRefundBeforeTerminalOutcome() {
		DropHandle handle;
		try (MockedConstruction<Crate> crates = mockConstruction(
				Crate.class,
				(mock, context) -> doThrow(new IllegalStateException("spawn failed"))
						.when(mock).dropCrate())) {
			handle = request("paid");
			completeCharge();
		}

		assertEquals(1, economy.deposits);
		economy.refund.complete(EconomyResult.rejected("refund rejected"));
		server.getScheduler().performOneTick();

		DropOutcome.Failed outcome = assertInstanceOf(
				DropOutcome.Failed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(PaymentStatus.REFUND_FAILED, outcome.payment());
		assertInstanceOf(
				DropSpawnResult.NotSpawned.class, handle.spawn().toCompletableFuture().join());
	}

	@Test
	void ambiguousRefundIsFailedUnknownAndNeverRetried() {
		DropHandle handle = request("paid");
		completeCharge();
		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();
		CrateManager.removeCrateAndDestroy(falling);

		economy.refund.completeExceptionally(new IllegalStateException("provider offline"));
		server.getScheduler().performOneTick();

		DropOutcome.Failed outcome = assertInstanceOf(
				DropOutcome.Failed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(DeliveryStatus.FAILED, outcome.delivery());
		assertEquals(PaymentStatus.UNKNOWN, outcome.payment());
		assertEquals(1, economy.deposits);
	}

	@Test
	void deprecatedResolvedPackageAdapterDoesNotRequireRegistryMembership() throws Exception {
		Package detached = new Package("detached", 0.0, List.of());
		PackageManager.clear();

		DropController.playerInitiatedDropPackage(
				detached,
				player,
				DropOptions.createDefault()
						.withDropHeight(20)
						.withChickenCount(1)
						.withFlareEffects(false));

		assertEquals(1, CrateManager.getCrateMap().size());
	}

	private DropHandle request(String packageName) {
		return DropController.requestPlayerDrop(player, packageName, options());
	}

	private void completeCharge() {
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		economy.withdrawal.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
	}

	private DropRequestOptions options() {
		return DropRequestOptions.defaults()
				.withDropHeight(20)
				.withChickenCount(1)
				.withFlareEffects(false)
				.withLandingEffects(false)
				.withContinuousEffects(false)
				.withSmokeEnabled(false);
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

	private static final class ControlledEconomyProvider implements EconomyProvider {

		private final CompletableFuture<EconomyResult> affordability = new CompletableFuture<>();
		private final CompletableFuture<EconomyResult> withdrawal = new CompletableFuture<>();
		private final CompletableFuture<EconomyResult> refund = new CompletableFuture<>();
		private int affordabilityChecks;
		private int withdrawals;
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
			withdrawals++;
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
