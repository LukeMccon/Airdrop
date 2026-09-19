package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.CapturedEventSnapshot;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PNumber;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PString;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PUuid;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

final class EconomyIntegrationSupport {
	static final String OPERATION_EVENT = "com.airdropmc.lightkeeper.economy.EconomyOperationEvent";
	private static final String STATE_EVENT = "com.airdropmc.lightkeeper.economy.EconomyStateEvent";

	private EconomyIntegrationSupport() {}

	static void resetAccount(ILightkeeperFramework framework, UUID playerId, String balance) {
		assertThat(framework.server().executeCommand(CommandSource.CONSOLE,
				"lkeconomy reset %s %s".formatted(playerId, balance)).success()).isTrue();
		assertAccountState(framework, playerId, balance, 0, 0, 0);
	}

	static List<CapturedEventSnapshot> operationsFor(List<CapturedEventSnapshot> operations, UUID playerId) {
		return operations.stream().filter(event -> new PUuid(playerId).equals(event.value("getPlayerId"))).toList();
	}

	static void assertAccountState(ILightkeeperFramework framework, UUID playerId, String balance,
			int affordabilityChecks, int withdrawals, int deposits) {
		String token = "account_" + Long.toUnsignedString(System.nanoTime(), 36);
		try (var states = framework.events().capture(STATE_EVENT)) {
			assertThat(framework.server().executeCommand(CommandSource.CONSOLE,
					"lkeconomy report %s %s".formatted(playerId, token)).success()).isTrue();
			framework.waitUntil(() -> states.getCapturedEvents().stream()
					.anyMatch(event -> new PString(token).equals(event.value("getCorrelationId"))), Duration.ofSeconds(10));
			CapturedEventSnapshot state = states.getCapturedEvents().stream()
					.filter(event -> new PString(token).equals(event.value("getCorrelationId")))
					.findFirst().orElseThrow();
			assertThat(state.value("getPlayerId")).isEqualTo(new PUuid(playerId));
			assertThat(state.value("getBalance")).isEqualTo(new PString(balance));
			assertThat(state.value("getAffordabilityChecks")).isEqualTo(new PNumber(affordabilityChecks));
			assertThat(state.value("getWithdrawals")).isEqualTo(new PNumber(withdrawals));
			assertThat(state.value("getDeposits")).isEqualTo(new PNumber(deposits));
		}
	}

	static void disableProvider(ILightkeeperFramework framework) {
		assertThat(framework.server().executeCommand(CommandSource.CONSOLE, "lkeconomy disable").success()).isTrue();
		int offset = framework.server().output().size();
		assertThat(framework.server().executeCommand(CommandSource.CONSOLE, "airdrop reload").success()).isTrue();
		framework.waitUntil(() -> framework.server().output().stream().skip(offset)
				.anyMatch(line -> line.contains("No economy provider is available; paid drops are blocked")),
				Duration.ofSeconds(10));
	}

	static void fault(ILightkeeperFramework framework, UUID player, String operation, String mode) {
		control(framework, "fault %s %s %s".formatted(player, operation, mode), "fault", player);
	}

	static void release(ILightkeeperFramework framework, UUID player, String operation) {
		control(framework, "release %s %s".formatted(player, operation), "release", player);
	}

	static void clear(ILightkeeperFramework framework, UUID player) {
		control(framework, "clear " + player, "clear", player);
	}

	private static void control(ILightkeeperFramework framework, String arguments, String action, UUID player) {
		String token = "control_" + Long.toUnsignedString(System.nanoTime(), 36);
		int offset = framework.server().output().size();
		assertThat(framework.server().executeCommand(CommandSource.CONSOLE,
				"lkeconomy " + arguments + " " + token).success()).isTrue();
		String marker = "AIRDR_ECONOMY_CONTROL token=" + token + " action=" + action + " player=" + player;
		framework.waitUntil(() -> framework.server().output().stream().skip(offset).anyMatch(line -> line.endsWith(marker)),
				Duration.ofSeconds(10));
	}
}
