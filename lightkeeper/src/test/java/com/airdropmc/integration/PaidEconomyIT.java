package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.CapturedEventSnapshot;
import nl.pim16aap2.lightkeeper.framework.FreshServer;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PBool;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PEnum;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PRecord;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PRef;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PString;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PUuid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.airdropmc.integration.EconomyIntegrationSupport.*;
import static com.airdropmc.integration.AirdropIntegrationSupport.BARREL_POSITION;
import static com.airdropmc.integration.AirdropIntegrationSupport.DROP_EVENT;
import static com.airdropmc.integration.AirdropIntegrationSupport.LAND_EVENT;
import static org.assertj.core.api.Assertions.assertThat;

@FreshServer
@ExtendWith(LightkeeperExtension.class)
class PaidEconomyIT {
	private static final String BLOCK_CHANGE_EVENT = "org.bukkit.event.entity.EntityChangeBlockEvent";
	private static final String LANDING_ATTEMPT_EVENT = "com.airdropmc.api.event.AirdropLandingAttemptEvent";
	private static final List<String> LANDED_SEQUENCE = List.of("REQUEST", "SPAWNED", "LANDING_ATTEMPT", "LANDED", "OUTCOME");
	private static final List<String> VETO_SEQUENCE = List.of("REQUEST", "SPAWNED", "LANDING_ATTEMPT", "OUTCOME");
	private static final String OPERATION_TYPE =
			"com.airdropmc.lightkeeper.economy.EconomyOperationType";
	private static final String PREMIUM_PERMISSION = "airdrop.package.premium";
	private static final String PRICE = "10.25";

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void paidPackageUsesModernAsyncProviderAndWithdrawsExactPrice(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
		AirdropIntegrationSupport.enableEconomyProvider(framework);
		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		PlayerHandle player = AirdropIntegrationSupport.createPlayer(framework, world, PREMIUM_PERMISSION);

		try {
			resetAccount(framework, player.uniqueId(), "100.00");

			try (var operations = framework.events().capture(OPERATION_EVENT);
				 var drops = framework.events().capture(DROP_EVENT);
				 var landings = framework.events().capture(LAND_EVENT)) {
				player.executeCommand("airdrop premium");
				framework.waitUntil(() -> drops.getCapturedEvents().size() == 1, Duration.ofSeconds(20));
				AirdropIntegrationSupport.moveAway(player, world);
				framework.waitUntil(() -> landings.getCapturedEvents().size() == 1, Duration.ofSeconds(30));

				List<CapturedEventSnapshot> playerOperations =
						operationsFor(operations.getCapturedEvents(), player.uniqueId());
				assertThat(playerOperations).hasSize(2);
				assertOperation(playerOperations.get(0), "CAN_WITHDRAW", player.uniqueId(), "100.00");
				assertOperation(playerOperations.get(1), "WITHDRAW", player.uniqueId(), "89.75");
				assertAccountState(framework, player.uniqueId(), "89.75", 1, 1, 0);

				AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:barrel");
				AirdropIntegrationSupport.assertStarterContents(framework, world, BARREL_POSITION);
				assertThat(drops.getCapturedEvents()).hasSize(1);
				assertThat(landings.getCapturedEvents()).hasSize(1);
			}

			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			cleanup(framework, world, player);
		}
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void cancelledPaidLandingRefundsExactlyOnceAndReleasesLocation(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
		AirdropIntegrationSupport.enableEconomyProvider(framework);
		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		PlayerHandle rejectedPlayer = AirdropIntegrationSupport.createPlayer(
				framework, world, PREMIUM_PERMISSION);
		PlayerHandle retryingPlayer = null;

		try (var operations = framework.events().capture(OPERATION_EVENT);
			 var drops = framework.events().capture(DROP_EVENT);
			 var landings = framework.events().capture(LAND_EVENT)) {
			resetAccount(framework, rejectedPlayer.uniqueId(), "100.00");

			try (var blockChanges = framework.events().capture(BLOCK_CHANGE_EVENT)) {
				blockChanges.cancelNext(Integer.MAX_VALUE);
				rejectedPlayer.executeCommand("airdrop premium");
				framework.waitUntil(() -> drops.getCapturedEvents().size() == 1, Duration.ofSeconds(20));
				AirdropIntegrationSupport.moveAway(rejectedPlayer, world);

				PRecord crate = (PRecord) drops.getCapturedEvents().getFirst().value("getCrate");
				PRef fallingCrate = (PRef) crate.fields().get("getFallingCrate");
				framework.waitUntil(() -> blockChanges.getCapturedEvents().stream()
						.anyMatch(event -> fallingCrate.equals(event.value("getEntity"))),
						Duration.ofSeconds(30));
				framework.waitUntil(() -> operationsFor(
						operations.getCapturedEvents(), rejectedPlayer.uniqueId()).size() == 3,
						Duration.ofSeconds(20));

				List<CapturedEventSnapshot> rejectedOperations =
						operationsFor(operations.getCapturedEvents(), rejectedPlayer.uniqueId());
				assertThat(rejectedOperations).hasSize(3);
				assertOperation(rejectedOperations.get(0), "CAN_WITHDRAW", rejectedPlayer.uniqueId(), "100.00");
				assertOperation(rejectedOperations.get(1), "WITHDRAW", rejectedPlayer.uniqueId(), "89.75");
				assertOperation(rejectedOperations.get(2), "DEPOSIT", rejectedPlayer.uniqueId(), "100.00");
				assertAccountState(framework, rejectedPlayer.uniqueId(), "100.00", 1, 1, 1);
				assertThat(landings.getCapturedEvents()).isEmpty();
				AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:air");
				AirdropIntegrationSupport.awaitNoDropEntities(world);
			}

			retryingPlayer = AirdropIntegrationSupport.createPlayer(framework, world, PREMIUM_PERMISSION);
			resetAccount(framework, retryingPlayer.uniqueId(), "100.00");
			retryingPlayer.executeCommand("airdrop premium");
			framework.waitUntil(() -> drops.getCapturedEvents().size() == 2, Duration.ofSeconds(20));
			AirdropIntegrationSupport.moveAway(retryingPlayer, world);
			framework.waitUntil(() -> landings.getCapturedEvents().size() == 1, Duration.ofSeconds(30));

			assertAccountState(framework, retryingPlayer.uniqueId(), "89.75", 1, 1, 0);
			// The later delivery must not re-charge or refund the original account again.
			assertAccountState(framework, rejectedPlayer.uniqueId(), "100.00", 1, 1, 1);
			assertThat(operationsFor(operations.getCapturedEvents(), rejectedPlayer.uniqueId())).hasSize(3);
			AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:barrel");
			assertThat(drops.getCapturedEvents()).hasSize(2);
			assertThat(landings.getCapturedEvents()).hasSize(1);
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			try {
				if (retryingPlayer != null) retryingPlayer.remove();
			} finally {
				cleanup(framework, world, rejectedPlayer);
			}
		}
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void insufficientFundsDoesNotChargeAndSamePlayerCanImmediatelyRetry(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
		AirdropIntegrationSupport.enableEconomyProvider(framework);
		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		PlayerHandle player = AirdropIntegrationSupport.createPlayer(framework, world, PREMIUM_PERMISSION);
		try (var operations = framework.events().capture(OPERATION_EVENT);
			 var drops = framework.events().capture(DROP_EVENT);
			 var landings = framework.events().capture(LAND_EVENT)) {
			resetAccount(framework, player.uniqueId(), "5.00");
			int offset = framework.server().output().size();
			player.executeCommand("airdrop premium");
			var rejected = outcome(framework, offset, AirdropIntegrationSupport.REJECTED_MARKER_TYPES,
					"REJECTED", "REJECTED", "INSUFFICIENT_FUNDS");
			assertAccountState(framework, player.uniqueId(), "5.00", 1, 0, 0);
			var checks = operationsFor(operations.getCapturedEvents(), player.uniqueId());
			assertThat(checks).hasSize(1);
			assertOperation(checks.getFirst(), "CAN_WITHDRAW", player.uniqueId(), "5.00", false);
			assertThat(drops.getCapturedEvents()).isEmpty();
			assertThat(landings.getCapturedEvents()).isEmpty();
			AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:air");
			AirdropIntegrationSupport.awaitNoDropEntities(world);

			// Reset/fund the SAME player; no teleport or cooldown wait before retrying the same location.
			resetAccount(framework, player.uniqueId(), "100.00");
			int retryOffset = framework.server().output().size();
			player.executeCommand("airdrop premium");
			framework.waitUntil(() -> drops.getCapturedEvents().size() == 1, Duration.ofSeconds(20));
			AirdropIntegrationSupport.moveAway(player, world);
			var retry = outcome(framework, retryOffset, LANDED_SEQUENCE, "LANDED", "CHARGED", "NONE");
			assertThat(retry.requestId()).isNotEqualTo(rejected.requestId());
			assertAccountState(framework, player.uniqueId(), "89.75", 1, 1, 0);
			var all = operationsFor(operations.getCapturedEvents(), player.uniqueId());
			assertThat(all).hasSize(3);
			assertOperation(all.get(1), "CAN_WITHDRAW", player.uniqueId(), "100.00");
			assertOperation(all.get(2), "WITHDRAW", player.uniqueId(), "89.75");
			assertThat(landings.getCapturedEvents()).hasSize(1);
			assertSingleOutcome(framework, offset, rejected.requestId());
			AirdropIntegrationSupport.assertStarterContents(framework, world, BARREL_POSITION);
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			cleanup(framework, world, player);
		}
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void permissionDenialNeverCallsEnabledFundedProvider(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
		AirdropIntegrationSupport.enableEconomyProvider(framework);
		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		PlayerHandle player = AirdropIntegrationSupport.createPlayer(framework, world);
		try (var operations = framework.events().capture(OPERATION_EVENT);
			 var drops = framework.events().capture(DROP_EVENT);
			 var landings = framework.events().capture(LAND_EVENT)) {
			player.permissions().revoke(PREMIUM_PERMISSION);
			resetAccount(framework, player.uniqueId(), "100.00");
			int offset = framework.server().output().size();
			player.executeCommand("airdrop premium");
			outcome(framework, offset, AirdropIntegrationSupport.REJECTED_MARKER_TYPES,
					"REJECTED", "REJECTED", "INSUFFICIENT_PERMISSION");
			assertAccountState(framework, player.uniqueId(), "100.00", 0, 0, 0);
			assertThat(operationsFor(operations.getCapturedEvents(), player.uniqueId())).isEmpty();
			assertThat(drops.getCapturedEvents()).isEmpty();
			assertThat(landings.getCapturedEvents()).isEmpty();
			AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:air");
			AirdropIntegrationSupport.awaitNoDropEntities(world);
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			cleanup(framework, world, player);
		}
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void withdrawalTimeoutAndLateConfirmationDoNotDeliverRefundOrChargeTwice(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
		AirdropIntegrationSupport.enableEconomyProvider(framework);
		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		PlayerHandle player = AirdropIntegrationSupport.createPlayer(framework, world,
				PREMIUM_PERMISSION, AirdropIntegrationSupport.PACKAGE_PERMISSION);
		try (var operations = framework.events().capture(OPERATION_EVENT);
			 var drops = framework.events().capture(DROP_EVENT);
			 var landings = framework.events().capture(LAND_EVENT)) {
			resetAccount(framework, player.uniqueId(), "100.00");
			fault(framework, player.uniqueId(), "WITHDRAW", "HOLD");
			int offset = framework.server().output().size();
			player.executeCommand("airdrop premium");
			var timedOut = outcome(framework, offset, AirdropIntegrationSupport.REJECTED_MARKER_TYPES,
					"FAILED", "UNKNOWN", "FAILED");
			awaitReconciliation(framework, offset, timedOut.requestId(), player.uniqueId(), "WITHDRAWAL", "UNKNOWN",
					"Economy withdrawal operation timed out");
			// UNKNOWN is confirmation status, not proof that the debit was rolled back.
			assertAccountState(framework, player.uniqueId(), "89.75", 1, 1, 0);
			assertThat(drops.getCapturedEvents()).isEmpty();
			assertThat(landings.getCapturedEvents()).isEmpty();
			AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:air");
			AirdropIntegrationSupport.awaitNoDropEntities(world);
			release(framework, player.uniqueId(), "WITHDRAW");
			awaitReconciliation(framework, offset, timedOut.requestId(), player.uniqueId(), "WITHDRAWAL", "SUCCESS",
					"Ignoring late successful result");
			assertSingleOutcome(framework, offset, timedOut.requestId());
			assertThat(drops.getCapturedEvents()).isEmpty();
			assertThat(landings.getCapturedEvents()).isEmpty();
			assertAccountState(framework, player.uniqueId(), "89.75", 1, 1, 0);

			// A free request by the same player proves the timed-out request released admission.
			int retryOffset = framework.server().output().size();
			player.executeCommand("airdrop starter");
			framework.waitUntil(() -> drops.getCapturedEvents().size() == 1, Duration.ofSeconds(20));
			AirdropIntegrationSupport.moveAway(player, world);
			outcome(framework, retryOffset, LANDED_SEQUENCE, "LANDED", "NOT_APPLICABLE", "NONE");
			assertAccountState(framework, player.uniqueId(), "89.75", 1, 1, 0);
			var observed = operationsFor(operations.getCapturedEvents(), player.uniqueId());
			assertThat(observed).hasSize(2);
			assertOperation(observed.get(0), "CAN_WITHDRAW", player.uniqueId(), "100.00");
			assertOperation(observed.get(1), "WITHDRAW", player.uniqueId(), "89.75");
			assertSingleOutcome(framework, offset, timedOut.requestId());
			assertThat(drops.getCapturedEvents()).hasSize(1);
			assertThat(landings.getCapturedEvents()).hasSize(1);
			AirdropIntegrationSupport.assertStarterContents(framework, world, BARREL_POSITION);
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			cleanup(framework, world, player);
		}
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void landingVetoWithRejectedRefundHasRefundFailedOutcome(ILightkeeperFramework framework) {
		refundFault(framework, "REJECT", "REFUND_FAILED", "89.75", "REJECTED", "Fixture rejected refund");
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void landingVetoWithExceptionalRefundHasUnknownOutcome(ILightkeeperFramework framework) {
		refundFault(framework, "EXCEPTION", "UNKNOWN", "89.75", "UNKNOWN", "Fixture exceptional refund");
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void landingVetoWithTimedOutRefundDoesNotCreditOrCompleteAgainAfterLateConfirmation(ILightkeeperFramework framework) {
		refundFault(framework, "HOLD", "UNKNOWN", "100.00", "UNKNOWN", "Economy refund operation timed out");
	}

	private void refundFault(ILightkeeperFramework framework, String mode, String payment,
			String balance, String providerResult, String detail) {
		AirdropIntegrationSupport.awaitReady(framework);
		AirdropIntegrationSupport.enableEconomyProvider(framework);
		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		PlayerHandle player = AirdropIntegrationSupport.createPlayer(framework, world, PREMIUM_PERMISSION);
		PlayerHandle retry = null;
		try (var operations = framework.events().capture(OPERATION_EVENT);
			 var drops = framework.events().capture(DROP_EVENT);
			 var landings = framework.events().capture(LAND_EVENT)) {
			resetAccount(framework, player.uniqueId(), "100.00");
			fault(framework, player.uniqueId(), "DEPOSIT", mode);
			int offset = framework.server().output().size();
			AirdropIntegrationSupport.ConsumerMarker failed;
			try (var attempts = framework.events().capture(LANDING_ATTEMPT_EVENT)) {
				attempts.cancelNext(1);
				player.executeCommand("airdrop premium");
				framework.waitUntil(() -> drops.getCapturedEvents().size() == 1, Duration.ofSeconds(20));
				AirdropIntegrationSupport.moveAway(player, world);
				failed = outcome(framework, offset, VETO_SEQUENCE, "CANCELLED", payment, "CANCELLED");
				assertThat(attempts.getCapturedEvents()).hasSize(1);
				assertThat(landings.getCapturedEvents()).isEmpty();
				AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:air");
				AirdropIntegrationSupport.awaitNoDropEntities(world);
			}
			awaitReconciliation(framework, offset, failed.requestId(), player.uniqueId(), "REFUND", providerResult, detail);
			assertAccountState(framework, player.uniqueId(), balance, 1, 1, 1);
			var observed = operationsFor(operations.getCapturedEvents(), player.uniqueId());
			assertThat(observed).hasSize(3);
			assertOperation(observed.get(0), "CAN_WITHDRAW", player.uniqueId(), "100.00");
			assertOperation(observed.get(1), "WITHDRAW", player.uniqueId(), "89.75");
			assertOperation(observed.get(2), "DEPOSIT", player.uniqueId(), balance, mode.equals("HOLD"));
			if (mode.equals("HOLD")) {
				// Credit has already applied; only the successful response is withheld.
				release(framework, player.uniqueId(), "DEPOSIT");
				awaitReconciliation(framework, offset, failed.requestId(), player.uniqueId(), "REFUND", "SUCCESS",
						"Ignoring late successful result");
				assertAccountState(framework, player.uniqueId(), balance, 1, 1, 1);
				assertSingleOutcome(framework, offset, failed.requestId());
				assertThat(landings.getCapturedEvents()).isEmpty();
			}

			// Spawn consumed the first player's cooldown; location reuse must use a different player.
			retry = AirdropIntegrationSupport.createPlayer(framework, world, PREMIUM_PERMISSION);
			resetAccount(framework, retry.uniqueId(), "100.00");
			int retryOffset = framework.server().output().size();
			retry.executeCommand("airdrop premium");
			framework.waitUntil(() -> drops.getCapturedEvents().size() == 2, Duration.ofSeconds(20));
			AirdropIntegrationSupport.moveAway(retry, world);
			outcome(framework, retryOffset, LANDED_SEQUENCE, "LANDED", "CHARGED", "NONE");
			assertAccountState(framework, retry.uniqueId(), "89.75", 1, 1, 0);
			assertAccountState(framework, player.uniqueId(), balance, 1, 1, 1);
			assertThat(operationsFor(operations.getCapturedEvents(), player.uniqueId())).hasSize(3);
			assertSingleOutcome(framework, offset, failed.requestId());
			assertThat(drops.getCapturedEvents()).hasSize(2);
			assertThat(landings.getCapturedEvents()).hasSize(1);
			AirdropIntegrationSupport.assertStarterContents(framework, world, BARREL_POSITION);
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			try {
				if (retry != null) retry.remove();
			} finally {
				cleanup(framework, world, player);
			}
		}
	}

	private static void cleanup(ILightkeeperFramework framework, WorldHandle world, PlayerHandle player) {
		try { clear(framework, player.uniqueId()); }
		finally {
			// All cancellation captures have closed before physical cleanup.
			try { AirdropIntegrationSupport.cleanupCrate(framework, world); }
			finally {
				try { player.remove(); }
				finally { disableProvider(framework); }
			}
		}
	}

	private static AirdropIntegrationSupport.ConsumerMarker outcome(ILightkeeperFramework framework,
			int offset, List<String> sequence, String delivery, String payment, String reason) {
		var markers = AirdropIntegrationSupport.awaitConsumerMarkers(framework, offset, sequence);
		AirdropIntegrationSupport.assertCorrelatedPrimaryThreadSequence(markers);
		var result = markers.getLast();
		assertThat(result.required("delivery")).isEqualTo(delivery);
		assertThat(result.required("payment")).isEqualTo(payment);
		assertThat(result.required("reason")).isEqualTo(reason);
		return result;
	}

	private static void assertSingleOutcome(ILightkeeperFramework framework, int offset, UUID request) {
		assertThat(AirdropIntegrationSupport.consumerMarkers(framework, offset).stream()
				.filter(marker -> marker.type().equals("OUTCOME") && marker.requestId().equals(request)).toList())
				.hasSize(1);
	}

	private static void awaitReconciliation(ILightkeeperFramework framework, int offset, UUID request,
			UUID player, String operation, String result, String detail) {
		String expected = "Paid drop requires reconciliation: request=" + request + " player=" + player
				+ " amount=" + PRICE + " provider=LightKeeper Economy operation=" + operation + " result=" + result
				+ " detail=" + detail + "; no automatic retry or refund";
		framework.waitUntil(() -> framework.server().output().stream().skip(offset)
				.anyMatch(line -> line.endsWith(expected)), Duration.ofSeconds(15));
		assertThat(framework.server().output().stream().skip(offset).filter(line -> line.endsWith(expected)).toList())
				.as("one exact reconciliation diagnostic for this request/account/operation").hasSize(1);
	}

	private void assertOperation(
			CapturedEventSnapshot event,
			String operation,
			UUID playerId,
			String resultingBalance
	) {
		assertOperation(event, operation, playerId, resultingBalance, true);
	}

	private void assertOperation(CapturedEventSnapshot event, String operation, UUID playerId,
			String resultingBalance, boolean successful) {
		assertThat(event.value("getOperation")).isEqualTo(new PEnum(OPERATION_TYPE, operation));
		assertThat(event.value("getCaller")).isEqualTo(new PString("Airdrop"));
		assertThat(event.value("getPlayerId")).isEqualTo(new PUuid(playerId));
		assertThat(event.value("getAmount")).isEqualTo(new PString(PRICE));
		assertThat(event.value("getBalance")).isEqualTo(new PString(resultingBalance));
		assertThat(event.value("isSuccessful")).isEqualTo(new PBool(successful));
	}

}
