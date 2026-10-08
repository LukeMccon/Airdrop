package com.airdropmc.api;

import com.airdropmc.Airdrop;
import com.airdropmc.controllers.DropController;
import com.airdropmc.economy.EconomyPlayer;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.economy.EconomyResult;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.packages.PackageManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DropRequestCoordinatorGiftTest {

	private ServerMock server;
	private WorldMock world;
	private Airdrop plugin;
	private AirdropApi api;
	private ControlledEconomy economy;
	private PlayerMock sender;
	private PlayerMock recipient;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		world = com.airdropmc.testutil.TestWorlds.loadedWorld(server, "gift_world");
		plugin = preparedPlugin();
		server.getPluginManager().enablePlugin(plugin);
		awaitReady();
		api = server.getServicesManager().load(AirdropApi.class);
		economy = new ControlledEconomy();
		Field field = Airdrop.class.getDeclaredField("economyProvider");
		field.setAccessible(true);
		field.set(null, economy);
		sender = server.addPlayer("Sender");
		sender.addAttachment(plugin, "airdrop.send", true);
		sender.addAttachment(plugin, "airdrop.package.paid", true);
		sender.addAttachment(plugin, "airdrop.package.free", true);
		sender.teleport(new Location(world, 4.25, 100, 6.75));
		recipient = server.addPlayer("Recipient");
		recipient.teleport(new Location(world, 20.25, 100, 30.75));
		world.getBlockAt(20, 64, 30).setType(Material.STONE);
	}

	@AfterEach
	void tearDown() {
		CrateManager.clearAll();
		PackageManager.clear();
		MockBukkit.unmock();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void giftChargesSenderAndDeliversAtRecipientInBothPermissionModes(boolean requirePermission) {
		recipient.addAttachment(plugin, "airdrop.package.paid", true);
		Location requested = recipient.getLocation();

		DropHandle handle = gift("paid", requirePermission);
		assertEquals(DropSource.PLAYER, handle.descriptor().source());
		assertEquals(sender.getUniqueId(), handle.descriptor().playerId().orElseThrow());
		assertEquals(WorldPosition.from(requested), handle.descriptor().requestedPosition());
		assertEquals(List.of(senderPayment()), economy.affordabilityChecks);
		completeCharge();

		DropSpawnResult.Spawned spawned = assertInstanceOf(
				DropSpawnResult.Spawned.class, handle.spawn().toCompletableFuture().join());
		assertEquals(PaymentStatus.CHARGED, spawned.payment());
		assertEquals(List.of(senderPayment()), economy.withdrawals);
		assertEquals(new Location(world, 20.5, 84, 30.5), handle.context().orElseThrow().spawnLocation());
		assertEquals(new Location(world, 20.5, 65, 30.5), handle.context().orElseThrow().landingLocation());
		assertEquals(1, CrateManager.getCrateMap().size());
		land(handle);
		DropOutcome.Landed landed = assertInstanceOf(
				DropOutcome.Landed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(PaymentStatus.CHARGED, landed.payment());
		Barrel barrel = assertInstanceOf(Barrel.class,
				handle.context().orElseThrow().landingLocation().getBlock().getState());
		assertEquals(Material.DIAMOND, barrel.getInventory().getItem(0).getType());
		assertTrue(economy.deposits.isEmpty());
	}

	@Test
	void recipientDisconnectDuringRequestEventKeepsCapturedDestination() {
		server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
			@org.bukkit.event.EventHandler
			public void disconnect(com.airdropmc.api.event.AirdropRequestEvent event) {
				recipient.disconnect();
			}
		}, plugin);
		Location captured = recipient.getLocation();
		DropHandle handle = gift("free", false);
		assertInstanceOf(DropSpawnResult.Spawned.class, handle.spawn().toCompletableFuture().join());
		assertEquals(WorldPosition.from(captured), handle.descriptor().requestedPosition());
		assertFalse(recipient.isOnline());
	}

	@Test
	void defaultConsoleUsesSystemAdmissionAndDoesNotChargeRecipient() {
		var console = mock(org.bukkit.command.ConsoleCommandSender.class);
		when(console.hasPermission("airdrop.send")).thenReturn(true);
		DropHandle handle = DropController.requestSendDrop(console, recipient, "paid", options(), false);
		var spawned = assertInstanceOf(DropSpawnResult.Spawned.class, handle.spawn().toCompletableFuture().join());
		assertEquals(PaymentStatus.NOT_APPLICABLE, spawned.payment());
		assertEquals(DropSource.SYSTEM, handle.descriptor().source());
		assertTrue(handle.descriptor().playerId().isEmpty());
		assertTrue(economy.affordabilityChecks.isEmpty());
		assertTrue(economy.withdrawals.isEmpty());
		land(handle);
		assertInstanceOf(DropOutcome.Landed.class, handle.outcome().toCompletableFuture().join());
	}

	@ParameterizedTest
	@ValueSource(strings = {"airdrop.send", "airdrop.cost.bypass"})
	void explicitConsoleDenialsAlsoRejectDirectCoordinatorCalls(String permission) {
		var console = server.getConsoleSender();
		var attachment = console.addAttachment(plugin, permission, false);
		try {
			DropHandle handle = DropController.requestSendDrop(console, recipient, "paid", options(), false);
			assertEquals(DropRejectionReason.INSUFFICIENT_PERMISSION, rejection(handle).rejection().reason());
			assertTrue(economy.affordabilityChecks.isEmpty());
			assertTrue(economy.withdrawals.isEmpty());
			assertEquals(0, Airdrop.getDropAdmissionController().snapshot().pending());
		} finally {
			console.removeAttachment(attachment);
		}
	}

	@Test
	void adminSenderStillPaysForGift() {
		sender.addAttachment(plugin, "airdrop.admin", true);
		sender.addAttachment(plugin, "airdrop.package.paid", false);

		DropHandle handle = gift("paid", false);
		completeCharge();

		assertInstanceOf(DropSpawnResult.Spawned.class, handle.spawn().toCompletableFuture().join());
		assertEquals(List.of(senderPayment()), economy.withdrawals);
	}

	@Test
	void senderMustHaveGiftLeafEvenWhenAdmin() {
		sender.addAttachment(plugin, "airdrop.admin", true);
		sender.addAttachment(plugin, "airdrop.send", false);

		assertEquals(DropRejectionReason.INSUFFICIENT_PERMISSION,
				rejection(gift("paid", false)).rejection().reason());

		assertTrue(economy.affordabilityChecks.isEmpty());
		assertTrue(economy.withdrawals.isEmpty());
		assertEquals(0, Airdrop.getDropAdmissionController().snapshot().pending());
	}

	@Test
	void senderMustBeEligibleForPackage() {
		sender.addAttachment(plugin, "airdrop.package.paid", false);

		assertEquals(DropRejectionReason.INSUFFICIENT_PERMISSION,
				rejection(gift("paid", false)).rejection().reason());

		assertTrue(economy.affordabilityChecks.isEmpty());
		assertTrue(economy.withdrawals.isEmpty());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void recipientEligibilityIsRequiredOnlyWhenEnabled(boolean requirePermission) {
		DropHandle handle = gift("paid", requirePermission);

		if (requirePermission) {
			assertEquals(DropRejectionReason.INSUFFICIENT_PERMISSION, rejection(handle).rejection().reason());
			assertTrue(economy.affordabilityChecks.isEmpty());
			assertTrue(economy.withdrawals.isEmpty());
		} else {
			completeCharge();
			assertInstanceOf(DropSpawnResult.Spawned.class, handle.spawn().toCompletableFuture().join());
			assertEquals(List.of(senderPayment()), economy.withdrawals);
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void offlineRecipientIsRejectedBeforePayment(boolean costExempt) {
		sender.addAttachment(plugin, "airdrop.cost.bypass", costExempt);
		recipient.disconnect();

		DropHandle handle = gift("paid", false);
		assertEarlyRejection(handle, DropRejectionReason.INVALID_TARGET, costExempt);
		assertTrue(economy.affordabilityChecks.isEmpty());
		assertTrue(economy.withdrawals.isEmpty());
		assertEquals(0, Airdrop.getDropAdmissionController().snapshot().pending());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void blockedRecipientIsRejectedEvenWhenSenderHasClearSky(boolean costExempt) {
		sender.addAttachment(plugin, "airdrop.cost.bypass", costExempt);
		world.getBlockAt(20, 110, 30).setType(Material.OAK_LEAVES);

		assertEarlyRejection(gift("paid", false), DropRejectionReason.SKY_NOT_CLEAR, costExempt);
		assertTrue(economy.affordabilityChecks.isEmpty());
		assertTrue(economy.withdrawals.isEmpty());
		assertTrue(CrateManager.getCrateMap().isEmpty());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void unloadedRecipientWorldIsRejectedBeforePayment(boolean costExempt) {
		sender.addAttachment(plugin, "airdrop.cost.bypass", costExempt);
		WorldMock unloaded = com.airdropmc.testutil.TestWorlds.loadedWorld(server, "unloaded_gift_world");
		Location target = new Location(unloaded, 20, 100, 30);
		assertTrue(server.unloadWorld(unloaded, false));
		org.bukkit.entity.Player staleTarget = mock(org.bukkit.entity.Player.class);
		when(staleTarget.isOnline()).thenReturn(true);
		when(staleTarget.getLocation()).thenReturn(target);

		DropHandle handle = DropController.requestSendDrop(sender, staleTarget, "paid", options(), false);

		assertTrue(handle.outcome().toCompletableFuture().isDone());
		assertEarlyRejection(handle, DropRejectionReason.INVALID_TARGET, costExempt);
		assertTrue(economy.affordabilityChecks.isEmpty());
		assertTrue(economy.withdrawals.isEmpty());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void unknownPackagePreservesCapturedCostPolicy(boolean costExempt) {
		sender.addAttachment(plugin, "airdrop.cost.bypass", costExempt);
		assertEarlyRejection(gift("missing", false), DropRejectionReason.UNKNOWN_PACKAGE, costExempt);
		assertTrue(economy.affordabilityChecks.isEmpty());
		assertTrue(economy.withdrawals.isEmpty());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void sendPermissionDenialPreservesCapturedCostPolicy(boolean costExempt) {
		sender.addAttachment(plugin, "airdrop.cost.bypass", costExempt);
		sender.addAttachment(plugin, "airdrop.send", false);
		assertEarlyRejection(gift("paid", false), DropRejectionReason.INSUFFICIENT_PERMISSION, costExempt);
		assertTrue(economy.affordabilityChecks.isEmpty());
		assertTrue(economy.withdrawals.isEmpty());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void unavailableSelfOrdersPreserveCapturedCostPolicy(boolean costExempt) {
		sender.addAttachment(plugin, "airdrop.cost.bypass", costExempt);
		var coordinator = new com.airdropmc.internal.drop.DropRequestCoordinator(plugin);
		assertEarlyRejection(coordinator.requestPlayerDrop(sender, "paid", options()),
				DropRejectionReason.SERVICE_UNAVAILABLE, costExempt);
		coordinator.stop();
		assertEarlyRejection(coordinator.requestPlayerDrop(sender, "paid", options()),
				DropRejectionReason.SHUTTING_DOWN, costExempt);
		assertTrue(economy.affordabilityChecks.isEmpty());
		assertTrue(economy.withdrawals.isEmpty());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void selfAndGiftRequestsShareSendersPendingGuard(boolean giftFirst) {
		DropHandle pending = giftFirst ? gift("paid", false)
				: api.requestPlayerDrop(sender, "paid", options());
		DropHandle duplicate = giftFirst ? api.requestPlayerDrop(sender, "paid", options())
				: gift("paid", false);

		assertFalse(pending.spawn().toCompletableFuture().isDone());
		assertEquals(DropRejectionReason.REQUEST_PENDING, rejection(duplicate).rejection().reason());
		assertEquals(List.of(senderPayment()), economy.affordabilityChecks);
		assertTrue(economy.withdrawals.isEmpty());
	}

	@Test
	void pendingGiftDoesNotOwnRecipientsPendingGuard() {
		DropHandle gift = gift("paid", false);
		recipient.addAttachment(plugin, "airdrop.package.free", true);
		recipient.teleport(new Location(world, 40, 100, 45));

		DropHandle self = api.requestPlayerDrop(recipient, "free", options());

		assertInstanceOf(DropSpawnResult.Spawned.class, self.spawn().toCompletableFuture().join());
		assertFalse(gift.spawn().toCompletableFuture().isDone());
		assertEquals(List.of(senderPayment()), economy.affordabilityChecks);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void selfAndGiftRequestsShareSenderCooldownButRecipientKeepsOwnCooldown(boolean giftFirst) {
		DropHandle first = giftFirst ? gift("paid", false)
				: api.requestPlayerDrop(sender, "paid", options());
		completeCharge();
		land(first);
		sender.teleport(new Location(world, 50, 100, 55));
		recipient.teleport(new Location(world, 60, 100, 65));
		DropHandle second = giftFirst ? api.requestPlayerDrop(sender, "free", options())
				: gift("free", false);

		assertEquals(DropRejectionReason.COOLDOWN, rejection(second).rejection().reason());
		recipient.addAttachment(plugin, "airdrop.package.free", true);
		assertInstanceOf(DropSpawnResult.Spawned.class,
				api.requestPlayerDrop(recipient, "free", options()).spawn().toCompletableFuture().join());
		assertEquals(List.of(senderPayment()), economy.withdrawals);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void recipientMovementOrDisconnectWhilePaymentIsPendingKeepsValidatedSnapshot(boolean disconnect) {
		Location snapshot = recipient.getLocation();
		DropHandle handle = gift("paid", false);
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		recipient.teleport(new Location(world, 80, 120, 85));
		if (disconnect) {
			recipient.disconnect();
		}
		sender.teleport(new Location(world, 90, 120, 95));
		economy.withdrawal.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		assertInstanceOf(DropSpawnResult.Spawned.class, handle.spawn().toCompletableFuture().join());
		assertEquals(WorldPosition.from(snapshot), handle.descriptor().requestedPosition());
		assertEquals(new Location(world, 20.5, 65, 30.5), handle.context().orElseThrow().landingLocation());
		assertEquals(List.of(senderPayment()), economy.withdrawals);
		assertEquals(1, CrateManager.getCrateMap().size());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void failedOrCancelledLandingRefundsSenderExactlyOnce(boolean cancelled) {
		DropHandle handle = gift("paid", false);
		completeCharge();
		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();
		Location landing = handle.context().orElseThrow().landingLocation();
		EntityChangeBlockEvent event = new EntityChangeBlockEvent(falling,
				(cancelled ? landing : landing.clone().add(1, 0, 1)).getBlock(), Material.BARREL.createBlockData());
		event.setCancelled(cancelled);
		if (cancelled) {
			server.getPluginManager().callEvent(event);
		} else {
			assertThrows(IllegalStateException.class, () -> server.getPluginManager().callEvent(event));
		}
		CrateManager.removeCrateAndDestroy(falling);
		assertEquals(List.of(senderPayment()), economy.deposits);
		economy.refund.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		DropOutcome.Failed outcome = assertInstanceOf(
				DropOutcome.Failed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(cancelled ? DeliveryStatus.CANCELLED : DeliveryStatus.FAILED, outcome.delivery());
		assertEquals(PaymentStatus.REFUNDED, outcome.payment());
		assertEquals(List.of(senderPayment()), economy.deposits);
	}

	@Test
	void targetWorldUnloadedDuringWithdrawalRefundsSender() {
		DropHandle handle = gift("paid", false);
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		WorldMock other = com.airdropmc.testutil.TestWorlds.loadedWorld(server, "other_world");
		sender.teleport(new Location(other, 0, 100, 0));
		recipient.teleport(new Location(other, 10, 100, 10));
		assertTrue(server.unloadWorld(world, false));
		economy.withdrawal.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		assertEquals(List.of(senderPayment()), economy.withdrawals);
		assertEquals(List.of(senderPayment()), economy.deposits);
		assertTrue(CrateManager.getCrateMap().isEmpty());
		economy.refund.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		DropOutcome.Failed outcome = assertInstanceOf(
				DropOutcome.Failed.class, handle.outcome().toCompletableFuture().join());
		assertEquals(PaymentStatus.REFUNDED, outcome.payment());
		assertEquals(0, Airdrop.getDropAdmissionController().snapshot().pending());
	}

	@Test
	void costExemptionSkipsAllEconomyOperationsButPreservesPlayerAdmission() {
		sender.addAttachment(plugin, "airdrop.cost.bypass", true);
		DropHandle first = gift("paid", false);
		assertEquals(PaymentStatus.NOT_APPLICABLE, assertInstanceOf(
				DropSpawnResult.Spawned.class, first.spawn().toCompletableFuture().getNow(null)).payment());
		assertTrue(economy.affordabilityChecks.isEmpty());
		assertTrue(economy.withdrawals.isEmpty());
		land(first);
		recipient.teleport(new Location(world, 60, 100, 65));
		assertEquals(DropRejectionReason.COOLDOWN, rejection(gift("paid", false)).rejection().reason());
	}

	@Test
	void exemptionDoesNotBypassSenderOrRecipientPackageAccess() {
		sender.addAttachment(plugin, "airdrop.cost.bypass", true);
		assertEquals(DropRejectionReason.INSUFFICIENT_PERMISSION, rejection(gift("paid", true)).rejection().reason());
		sender.addAttachment(plugin, "airdrop.package.paid", false);
		assertEquals(DropRejectionReason.INSUFFICIENT_PERMISSION, rejection(gift("paid", false)).rejection().reason());
		assertTrue(economy.withdrawals.isEmpty());
	}

	@Test
	void exemptLandingFailureNeverRefundsUnchargedMoney() {
		sender.addAttachment(plugin, "airdrop.cost.bypass", true);
		DropHandle handle = gift("paid", false);
		assertInstanceOf(DropSpawnResult.Spawned.class, handle.spawn().toCompletableFuture().getNow(null));
		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();
		CrateManager.removeCrateAndDestroy(falling);
		assertEquals(PaymentStatus.NOT_APPLICABLE, assertInstanceOf(
				DropOutcome.Failed.class, handle.outcome().toCompletableFuture().join()).payment());
		assertTrue(economy.withdrawals.isEmpty());
		assertTrue(economy.deposits.isEmpty());
	}

	@Test
	void selfOrderAlsoUsesEffectiveCostExemption() {
		sender.addAttachment(plugin, "airdrop.cost.bypass", true);
		DropHandle handle = api.requestPlayerDrop(sender, "paid", options());
		assertEquals(PaymentStatus.NOT_APPLICABLE, assertInstanceOf(DropSpawnResult.Spawned.class,
				handle.spawn().toCompletableFuture().getNow(null)).payment());
		assertTrue(economy.withdrawals.isEmpty());
	}

	@Test
	void paidCoordinateSendCapturesLocationAndRefundsOriginalPayer() {
		Location target = new Location(world, 20.25, world.getMaxHeight() - 1, 30.75);
		DropHandle handle = DropController.requestSendDrop(sender, target, "paid", options());
		target.setX(300);
		sender.teleport(new Location(com.airdropmc.testutil.TestWorlds.loadedWorld(server, "moved"), 0, 100, 0));
		completeCharge();
		assertEquals(new Location(world, 20.5, 65, 30.5), handle.context().orElseThrow().landingLocation());
		assertEquals(sender.getUniqueId(), handle.descriptor().playerId().orElseThrow());
		assertEquals(List.of(senderPayment()), economy.withdrawals);
		CrateManager.removeCrateAndDestroy(CrateManager.getCrateMap().keySet().iterator().next());
		assertEquals(List.of(senderPayment()), economy.deposits);
		economy.refund.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		assertEquals(PaymentStatus.REFUNDED, assertInstanceOf(DropOutcome.Failed.class,
				handle.outcome().toCompletableFuture().join()).payment());
	}

	@Test
	void recipientCostExemptionDoesNotExemptSender() {
		recipient.addAttachment(plugin, "airdrop.cost.bypass", true);
		DropHandle handle = gift("paid", false);
		completeCharge();
		assertEquals(PaymentStatus.CHARGED, assertInstanceOf(DropSpawnResult.Spawned.class,
				handle.spawn().toCompletableFuture().join()).payment());
		assertEquals(List.of(senderPayment()), economy.withdrawals);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void acceptedPaymentPolicySurvivesPermissionChangesDuringRequestEvent(boolean exempt) {
		sender.addAttachment(plugin, "airdrop.cost.bypass", exempt);
		server.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
			@org.bukkit.event.EventHandler
			public void onRequest(com.airdropmc.api.event.AirdropRequestEvent event) {
				sender.addAttachment(plugin, "airdrop.cost.bypass", !exempt);
			}
		}, plugin);
		DropHandle handle = gift("paid", false);
		if (!exempt) { completeCharge(); }
		assertEquals(exempt ? PaymentStatus.NOT_APPLICABLE : PaymentStatus.CHARGED,
				assertInstanceOf(DropSpawnResult.Spawned.class, handle.spawn().toCompletableFuture().join()).payment());
		assertEquals(exempt ? List.of() : List.of(senderPayment()), economy.withdrawals);
	}

	private DropHandle gift(String packageName, boolean requireRecipientPermission) {
		return DropController.requestSendDrop(sender, recipient, packageName, options(), requireRecipientPermission);
	}

	private DropOutcome.Rejected rejection(DropHandle handle) {
		return assertInstanceOf(DropOutcome.Rejected.class, handle.outcome().toCompletableFuture().join());
	}

	private void assertEarlyRejection(DropHandle handle, DropRejectionReason reason, boolean costExempt) {
		DropOutcome.Rejected outcome = rejection(handle);
		assertEquals(reason, outcome.rejection().reason());
		PaymentStatus expected = costExempt ? PaymentStatus.NOT_APPLICABLE : PaymentStatus.REJECTED;
		assertEquals(expected, outcome.payment());
		assertEquals(expected, assertInstanceOf(DropSpawnResult.NotSpawned.class,
				handle.spawn().toCompletableFuture().join()).outcome().payment());
		assertTrue(handle.context().isEmpty());
		assertTrue(economy.deposits.isEmpty());
		assertTrue(CrateManager.getCrateMap().isEmpty());
	}

	private Payment senderPayment() {
		return new Payment(new EconomyPlayer(sender.getUniqueId(), sender.getName()), new BigDecimal("10.25"));
	}

	private void completeCharge() {
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		economy.withdrawal.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
	}

	private void land(DropHandle handle) {
		FallingBlock falling = CrateManager.getCrateMap().keySet().iterator().next();
		server.getPluginManager().callEvent(new EntityChangeBlockEvent(falling,
				handle.context().orElseThrow().landingLocation().getBlock(), Material.BARREL.createBlockData()));
	}

	private DropRequestOptions options() {
		return DropRequestOptions.defaults().withDropHeight(20).withChickenCount(1)
				.withFlareEffects(false).withLandingEffects(false).withContinuousEffects(false).withSmokeEnabled(false);
	}

	private Airdrop preparedPlugin() throws Exception {
		Airdrop loaded = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(loaded.getDataFolder().toPath());
		Files.writeString(loaded.getDataFolder().toPath().resolve("config.yml"),
				"language: en\neconomy:\n  enabled: true\n", StandardCharsets.UTF_8);
		Files.writeString(loaded.getDataFolder().toPath().resolve("packages.yml"),
				"packages:\n  paid:\n    price: 10.25\n    items:\n"
						+ "      - ==: org.bukkit.inventory.ItemStack\n"
						+ "        schema_version: 1\n        id: minecraft:diamond\n        count: 1\n"
						+ "  free:\n    price: 0\n    items: []\n", StandardCharsets.UTF_8);
		return loaded;
	}

	private void awaitReady() {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline && !Airdrop.isReady() && plugin.isEnabled()) {
			server.getScheduler().performOneTick();
			LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
		}
		assertTrue(Airdrop.isReady(), "Timed out waiting for Airdrop startup");
	}

	private record Payment(EconomyPlayer player, BigDecimal amount) {
	}

	private static final class ControlledEconomy implements EconomyProvider {
		private final CompletableFuture<EconomyResult> affordability = new CompletableFuture<>();
		private final CompletableFuture<EconomyResult> withdrawal = new CompletableFuture<>();
		private final CompletableFuture<EconomyResult> refund = new CompletableFuture<>();
		private final List<Payment> affordabilityChecks = new ArrayList<>();
		private final List<Payment> withdrawals = new ArrayList<>();
		private final List<Payment> deposits = new ArrayList<>();

		@Override
		public boolean nativeAsync() {
			return true;
		}

		@Override
		public CompletionStage<EconomyResult> canAfford(EconomyPlayer player, BigDecimal amount) {
			affordabilityChecks.add(new Payment(player, amount));
			return affordability;
		}

		@Override
		public CompletionStage<EconomyResult> withdraw(EconomyPlayer player, BigDecimal amount) {
			withdrawals.add(new Payment(player, amount));
			return withdrawal;
		}

		@Override
		public CompletionStage<EconomyResult> deposit(EconomyPlayer player, BigDecimal amount) {
			deposits.add(new Payment(player, amount));
			return refund;
		}

		@Override
		public String getName() {
			return "Controlled";
		}
	}
}
