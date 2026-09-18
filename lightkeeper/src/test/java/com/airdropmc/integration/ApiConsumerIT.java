package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.CapturedEventSnapshot;
import nl.pim16aap2.lightkeeper.framework.FreshServer;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PBool;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PEnum;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.airdropmc.integration.AirdropIntegrationSupport.BARREL_POSITION;
import static com.airdropmc.integration.AirdropIntegrationSupport.FREE_MARKER_TYPES;
import static com.airdropmc.integration.AirdropIntegrationSupport.PACKAGE_PERMISSION;
import static com.airdropmc.integration.EconomyIntegrationSupport.OPERATION_EVENT;
import static com.airdropmc.integration.EconomyIntegrationSupport.assertAccountState;
import static com.airdropmc.integration.EconomyIntegrationSupport.operationsFor;
import static com.airdropmc.integration.EconomyIntegrationSupport.resetAccount;
import static org.assertj.core.api.Assertions.assertThat;

@FreshServer
@ExtendWith(LightkeeperExtension.class)
class ApiConsumerIT {
	private static final String EXPECTED_SEQUENCE =
			"READY, REQUEST, SPAWNED, LANDING_ATTEMPT, LANDED, OUTCOME";

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void packagedConsumerObservesOneCorrelatedPrimaryThreadLifecycle(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		AirdropIntegrationSupport.keepLandingChunkLoaded(framework, world);
		PlayerHandle player = AirdropIntegrationSupport.createPlayer(
				framework, world, PACKAGE_PERMISSION);

		try {
			player.executeCommand("airdrop starter");
			AirdropIntegrationSupport.awaitConsumerMarkers(
					framework, 0, FREE_MARKER_TYPES.subList(0, 3));

			List<AirdropIntegrationSupport.ConsumerMarker> markers =
					AirdropIntegrationSupport.awaitConsumerMarkers(
							framework, 0, FREE_MARKER_TYPES);
			assertThat(markers).as(EXPECTED_SEQUENCE).hasSize(6);
			AirdropIntegrationSupport.assertCorrelatedPrimaryThreadSequence(markers);

			AirdropIntegrationSupport.ConsumerMarker outcome = markers.getLast();
			assertThat(outcome.required("delivery")).isEqualTo("LANDED");
			assertThat(outcome.required("payment")).isEqualTo("NOT_APPLICABLE");
			assertThat(outcome.required("reason")).isEqualTo("NONE");
			AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:barrel");
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			cleanupWorld(framework, world, player);
		}
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void packagedConsumerRequestsDropAndObservesCorrelatedHandleStages(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		AirdropIntegrationSupport.keepLandingChunkLoaded(framework, world);
		PlayerHandle player = AirdropIntegrationSupport.createPlayer(framework, world, PACKAGE_PERMISSION);
		try {
			var definition = ConsumerIntegrationSupport.snapshot(framework, "starter");
			assertThat(definition.name()).isEqualTo("starter");
			assertThat(new java.math.BigDecimal(definition.price())).isEqualByComparingTo("0");
			assertThat(definition.items()).containsExactly(
					"IRON_HELMET:1", "IRON_CHESTPLATE:1", "IRON_LEGGINGS:1", "IRON_BOOTS:1", "BREAD:2");
			var request = ConsumerIntegrationSupport.request(framework, player, "starter");
			var spawned = ConsumerIntegrationSupport.awaitHandleResult(framework, request, "HANDLE_SPAWN");
			assertSpawned(spawned, request, "NOT_APPLICABLE");
			AirdropIntegrationSupport.moveAway(player, world);
			var outcome = ConsumerIntegrationSupport.awaitHandleResult(framework, request, "HANDLE_OUTCOME");
			assertOutcome(outcome, "LANDED", "NOT_APPLICABLE", "NONE");
			assertThat(outcome.required("crateId")).isEqualTo(spawned.required("crateId"));
			assertThat(outcome.required("viewRequestId")).isEqualTo(request.requestId().toString());
			assertRequestEvidence(framework, request, spawned.required("crateId"), "LANDED", "NOT_APPLICABLE");
			AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:barrel");
			AirdropIntegrationSupport.assertStarterContents(framework, world, BARREL_POSITION);
			AirdropIntegrationSupport.awaitNoDropEntities(world);
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			cleanupWorld(framework, world, player);
		}
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void directPaidRequestPublicLandingVetoRefundsAndSecondPlayerReusesLocation(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		AirdropIntegrationSupport.keepLandingChunkLoaded(framework, world);
		PlayerHandle rejectedPlayer = AirdropIntegrationSupport.createPlayer(
				framework, world, "airdrop.package.premium");
		PlayerHandle retryingPlayer = null;
		try {
			AirdropIntegrationSupport.enableEconomyProvider(framework);
			resetAccount(framework, rejectedPlayer.uniqueId(), "100.00");
			try (var operations = framework.events().capture(OPERATION_EVENT);
				 var landed = framework.events().capture("com.airdropmc.api.event.AirdropLandedEvent")) {
				ConsumerIntegrationSupport.Request rejected;
				String rejectedCrateId;
				try (var attempts = framework.events().capture("com.airdropmc.api.event.AirdropLandingAttemptEvent")) {
					attempts.cancelNext(1);
					rejected = ConsumerIntegrationSupport.request(framework, rejectedPlayer, "premium");
					var spawned = ConsumerIntegrationSupport.awaitHandleResult(framework, rejected, "HANDLE_SPAWN");
					assertSpawned(spawned, rejected, "CHARGED");
					rejectedCrateId = spawned.required("crateId");
					AirdropIntegrationSupport.moveAway(rejectedPlayer, world);
					var outcome = ConsumerIntegrationSupport.awaitHandleResult(framework, rejected, "HANDLE_OUTCOME");
					assertOutcome(outcome, "CANCELLED", "REFUNDED", "CANCELLED");
					assertRequestEvidence(framework, rejected, rejectedCrateId, "CANCELLED", "REFUNDED");
					assertThat(attempts.getCapturedEvents()).singleElement().satisfies(event ->
							assertThat(event.value("isCancelled")).isEqualTo(new PBool(true)));
					assertThat(landed.getCapturedEvents()).isEmpty();
					assertAccountState(framework, rejectedPlayer.uniqueId(), "100.00", 1, 1, 1);
					assertPaidOperations(operations.getCapturedEvents(), rejectedPlayer.uniqueId(), true);
					AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:air");
					AirdropIntegrationSupport.awaitNoDropEntities(world);
					AirdropIntegrationSupport.awaitNoEntities(world, "minecraft:item");
				}

				// The cancelled post-spawn request retains its player's cooldown; reuse with a second player.
				retryingPlayer = AirdropIntegrationSupport.createPlayer(framework, world, "airdrop.package.premium");
				resetAccount(framework, retryingPlayer.uniqueId(), "100.00");
				var retry = ConsumerIntegrationSupport.request(framework, retryingPlayer, "premium");
				assertThat(retry.requestId()).isNotEqualTo(rejected.requestId());
				var retrySpawn = ConsumerIntegrationSupport.awaitHandleResult(framework, retry, "HANDLE_SPAWN");
				assertSpawned(retrySpawn, retry, "CHARGED");
				assertThat(retrySpawn.required("crateId")).isNotEqualTo(rejectedCrateId);
				AirdropIntegrationSupport.moveAway(retryingPlayer, world);
				var retryOutcome = ConsumerIntegrationSupport.awaitHandleResult(framework, retry, "HANDLE_OUTCOME");
				assertOutcome(retryOutcome, "LANDED", "CHARGED", "NONE");
				assertThat(retryOutcome.required("crateId")).isEqualTo(retrySpawn.required("crateId"));
				assertThat(retryOutcome.required("viewRequestId")).isEqualTo(retry.requestId().toString());
				assertRequestEvidence(framework, retry, retrySpawn.required("crateId"), "LANDED", "CHARGED");
				assertThat(landed.getCapturedEvents()).hasSize(1);
				AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:barrel");
				AirdropIntegrationSupport.assertStarterContents(framework, world, BARREL_POSITION);
				AirdropIntegrationSupport.awaitNoDropEntities(world);

				assertAccountState(framework, retryingPlayer.uniqueId(), "89.75", 1, 1, 0);
				assertPaidOperations(operations.getCapturedEvents(), retryingPlayer.uniqueId(), false);
				// Recheck the original account and terminal evidence after the independent retry completed.
				assertAccountState(framework, rejectedPlayer.uniqueId(), "100.00", 1, 1, 1);
				assertPaidOperations(operations.getCapturedEvents(), rejectedPlayer.uniqueId(), true);
				assertRequestEvidence(framework, rejected, rejectedCrateId, "CANCELLED", "REFUNDED");
				assertThat(operations.getCapturedEvents()).hasSize(5);
			}
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			// All event captures (especially the veto) close before physical cleanup.
			try {
				cleanupWorld(framework, world, rejectedPlayer, retryingPlayer);
			} finally {
				assertThat(framework.server().executeCommand(CommandSource.CONSOLE, "lkeconomy disable").success()).isTrue();
				assertThat(framework.server().executeCommand(CommandSource.CONSOLE, "airdrop reload").success()).isTrue();
			}
		}
	}

	private static void assertSpawned(ConsumerIntegrationSupport.Marker marker,
			ConsumerIntegrationSupport.Request request, String payment) {
		assertThat(marker.required("spawned")).isEqualTo("true");
		assertThat(marker.required("payment")).isEqualTo(payment);
		assertThat(marker.required("viewRequestId")).isEqualTo(request.requestId().toString());
		assertThat(UUID.fromString(marker.required("crateId"))).isNotNull();
	}

	private static void assertOutcome(ConsumerIntegrationSupport.Marker marker,
			String delivery, String payment, String reason) {
		assertThat(marker.required("delivery")).isEqualTo(delivery);
		assertThat(marker.required("payment")).isEqualTo(payment);
		assertThat(marker.required("reason")).isEqualTo(reason);
	}

	private static void assertRequestEvidence(ILightkeeperFramework framework,
			ConsumerIntegrationSupport.Request request, String crateId, String delivery, String payment) {
		boolean landed = delivery.equals("LANDED");
		List<String> expected = landed
				? List.of("REQUEST", "SPAWNED", "LANDING_ATTEMPT", "LANDED", "OUTCOME")
				: List.of("REQUEST", "SPAWNED", "LANDING_ATTEMPT", "OUTCOME");
		framework.waitUntil(() -> AirdropIntegrationSupport.consumerMarkers(framework, request.outputOffset()).stream()
				.anyMatch(marker -> marker.type().equals("OUTCOME") && marker.requestId().equals(request.requestId())),
				Duration.ofSeconds(10));
		var lifecycle = AirdropIntegrationSupport.consumerMarkers(framework, request.outputOffset()).stream()
				.filter(marker -> request.requestId().equals(marker.requestId())).toList();
		assertThat(lifecycle).extracting(AirdropIntegrationSupport.ConsumerMarker::type)
				.containsExactlyElementsOf(expected);
		AirdropIntegrationSupport.assertCorrelatedPrimaryThreadSequence(lifecycle);
		assertThat(lifecycle.getLast().required("delivery")).isEqualTo(delivery);
		assertThat(lifecycle.getLast().required("payment")).isEqualTo(payment);
		assertThat(lifecycle.getLast().required("reason")).isEqualTo(landed ? "NONE" : "CANCELLED");

		var markers = ConsumerIntegrationSupport.markers(framework, request.outputOffset());
		var handles = markers.stream().filter(marker -> request.token().equals(marker.values().get("token"))).toList();
		assertThat(handles).extracting(ConsumerIntegrationSupport.Marker::type)
				.containsExactly("HANDLE", "HANDLE_SPAWN", "HANDLE_OUTCOME");
		assertThat(handles).allSatisfy(marker -> {
			assertThat(marker.required("primaryThread")).as("actual, unrescheduled callback thread").isEqualTo("true");
			assertThat(marker.required("requestId")).isEqualTo(request.requestId().toString());
			assertThat(marker.required("status")).isEqualTo("OK");
		});
		var crateEvents = markers.stream()
				.filter(marker -> request.requestId().toString().equals(marker.values().get("requestId")))
				.filter(marker -> List.of("SPAWNED", "LANDING_ATTEMPT", "LANDED").contains(marker.type())).toList();
		assertThat(crateEvents).hasSize(landed ? 3 : 2).allSatisfy(marker ->
				assertThat(marker.required("crateId")).isEqualTo(crateId));
	}

	private static void assertPaidOperations(List<CapturedEventSnapshot> events, UUID playerId, boolean refunded) {
		List<CapturedEventSnapshot> operations = operationsFor(events, playerId);
		List<String> types = refunded ? List.of("CAN_WITHDRAW", "WITHDRAW", "DEPOSIT")
				: List.of("CAN_WITHDRAW", "WITHDRAW");
		List<String> balances = refunded ? List.of("100.00", "89.75", "100.00")
				: List.of("100.00", "89.75");
		assertThat(operations).hasSize(types.size());
		for (int index = 0; index < types.size(); index++) {
			var operation = operations.get(index);
			assertThat(operation.value("getOperation")).isEqualTo(new PEnum(
					"com.airdropmc.lightkeeper.economy.EconomyOperationType", types.get(index)));
			assertThat(operation.value("getCaller")).isEqualTo(new PString("Airdrop"));
			assertThat(operation.value("getAmount")).isEqualTo(new PString("10.25"));
			assertThat(operation.value("getBalance")).isEqualTo(new PString(balances.get(index)));
			assertThat(operation.value("isSuccessful")).isEqualTo(new PBool(true));
		}
	}

	private static void cleanupWorld(ILightkeeperFramework framework, WorldHandle world, PlayerHandle... players) {
		try {
			AirdropIntegrationSupport.cleanupCrate(framework, world);
		} finally {
			try {
				for (PlayerHandle player : players) {
					if (player != null) {
						player.remove();
					}
				}
			} finally {
				assertThat(framework.server().executeCommand(CommandSource.CONSOLE,
						"minecraft:execute in minecraft:%s run forceload remove 0 0".formatted(world.name())).success()).isTrue();
			}
		}
	}
}
