package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.CapturedEventSnapshot;
import nl.pim16aap2.lightkeeper.framework.CommandResult;
import nl.pim16aap2.lightkeeper.framework.FreshServer;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PBool;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PEnum;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PNumber;
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

import static com.airdropmc.integration.AirdropIntegrationSupport.BARREL_POSITION;
import static com.airdropmc.integration.AirdropIntegrationSupport.DROP_EVENT;
import static com.airdropmc.integration.AirdropIntegrationSupport.LAND_EVENT;
import static org.assertj.core.api.Assertions.assertThat;

@FreshServer
@ExtendWith(LightkeeperExtension.class)
class PaidEconomyIT {
	private static final String BLOCK_CHANGE_EVENT = "org.bukkit.event.entity.EntityChangeBlockEvent";
	private static final String OPERATION_EVENT =
			"com.airdropmc.lightkeeper.economy.EconomyOperationEvent";
	private static final String STATE_EVENT =
			"com.airdropmc.lightkeeper.economy.EconomyStateEvent";
	private static final String OPERATION_TYPE =
			"com.airdropmc.lightkeeper.economy.EconomyOperationType";
	private static final String PREMIUM_PERMISSION = "airdrop.package.premium";
	private static final String PRICE = "10.25";

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void paidPackageUsesModernAsyncProviderAndWithdrawsExactPrice(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
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
			player.remove();
		}
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void cancelledPaidLandingRefundsExactlyOnceAndReleasesLocation(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
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
			AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:barrel");
			assertThat(drops.getCapturedEvents()).hasSize(2);
			assertThat(landings.getCapturedEvents()).hasSize(1);
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			if (retryingPlayer != null) {
				retryingPlayer.remove();
			}
			rejectedPlayer.remove();
		}
	}

	private void resetAccount(ILightkeeperFramework framework, UUID playerId, String balance) {
		CommandResult result = framework.server().executeCommand(CommandSource.CONSOLE,
				"lkeconomy reset %s %s".formatted(playerId, balance));
		assertThat(result.success()).as("reset fixture economy account").isTrue();
	}

	private List<CapturedEventSnapshot> operationsFor(List<CapturedEventSnapshot> operations, UUID playerId) {
		return operations.stream()
				.filter(event -> new PUuid(playerId).equals(event.value("getPlayerId")))
				.toList();
	}

	private void assertOperation(
			CapturedEventSnapshot event,
			String operation,
			UUID playerId,
			String resultingBalance
	) {
		assertThat(event.value("getOperation")).isEqualTo(new PEnum(OPERATION_TYPE, operation));
		assertThat(event.value("getCaller")).isEqualTo(new PString("Airdrop"));
		assertThat(event.value("getPlayerId")).isEqualTo(new PUuid(playerId));
		assertThat(event.value("getAmount")).isEqualTo(new PString(PRICE));
		assertThat(event.value("getBalance")).isEqualTo(new PString(resultingBalance));
		assertThat(event.value("isSuccessful")).isEqualTo(new PBool(true));
	}

	private void assertAccountState(
			ILightkeeperFramework framework,
			UUID playerId,
			String balance,
			int affordabilityChecks,
			int withdrawals,
			int deposits
	) {
		String correlationId = "paid_" + Long.toUnsignedString(System.nanoTime(), 36);
		try (var states = framework.events().capture(STATE_EVENT)) {
			CommandResult result = framework.server().executeCommand(CommandSource.CONSOLE,
					"lkeconomy report %s %s".formatted(playerId, correlationId));
			assertThat(result.success()).as("report fixture economy account").isTrue();
			framework.waitUntil(() -> states.getCapturedEvents().stream()
					.anyMatch(event -> new PString(correlationId).equals(event.value("getCorrelationId"))),
					Duration.ofSeconds(10));

			CapturedEventSnapshot state = states.getCapturedEvents().stream()
					.filter(event -> new PString(correlationId).equals(event.value("getCorrelationId")))
					.findFirst()
					.orElseThrow();
			assertThat(state.value("getPlayerId")).isEqualTo(new PUuid(playerId));
			assertThat(state.value("getBalance")).isEqualTo(new PString(balance));
			assertThat(state.value("getAffordabilityChecks"))
					.isEqualTo(new PNumber(affordabilityChecks));
			assertThat(state.value("getWithdrawals")).isEqualTo(new PNumber(withdrawals));
			assertThat(state.value("getDeposits")).isEqualTo(new PNumber(deposits));
		}
	}
}
