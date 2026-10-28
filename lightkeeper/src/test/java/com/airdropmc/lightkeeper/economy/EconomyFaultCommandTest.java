package com.airdropmc.lightkeeper.economy;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EconomyFaultCommandTest {
	@Test
	void consoleCommandsAcknowledgeExactTokensAndOnlyReleaseOnce() {
		try (var controls = new EconomyFaultControls()) {
			List<String> markers = new ArrayList<>();
			var command = new EconomyFaultCommand(controls, markers::add);
			var sender = sender(ConsoleCommandSender.class);
			UUID player = UUID.randomUUID();
			assertThat(command.execute(sender, new String[]{"fault", player.toString(), "WITHDRAW", "HOLD", "arm_1"})).isTrue();
			var response = controls.claim(player, EconomyOperationType.WITHDRAW);
			assertThat(command.execute(sender, new String[]{"release", player.toString(), "WITHDRAW", "release_1"})).isTrue();
			assertThat(response.confirmation()).isCompleted();
			assertThat(command.execute(sender, new String[]{"release", player.toString(), "WITHDRAW", "release_2"})).isFalse();
			assertThat(command.execute(sender, new String[]{"clear", player.toString(), "clear_1"})).isTrue();
			assertThat(markers).containsExactly(
					"AIRDR_ECONOMY_CONTROL token=arm_1 action=fault player=" + player,
					"AIRDR_ECONOMY_CONTROL token=release_1 action=release player=" + player,
					"AIRDR_ECONOMY_CONTROL token=clear_1 action=clear player=" + player);
		}
	}

	@Test
	void playersMalformedCommandsAndUnsafeTokensCannotArmControls() {
		try (var controls = new EconomyFaultControls()) {
			List<String> markers = new ArrayList<>();
			var command = new EconomyFaultCommand(controls, markers::add);
			String player = UUID.randomUUID().toString();
			assertThat(command.execute(sender(CommandSender.class), new String[]{"fault", player, "DEPOSIT", "REJECT", "token"})).isFalse();
			assertThat(command.execute(sender(ConsoleCommandSender.class), new String[]{"fault", player, "DEPOSIT", "REJECT", "bad token"})).isFalse();
			assertThat(command.execute(sender(ConsoleCommandSender.class), new String[]{"fault", player, "CAN_WITHDRAW", "HOLD", "token"})).isFalse();
			assertThat(markers).isEmpty();
			assertThat(controls.claim(UUID.fromString(player), EconomyOperationType.DEPOSIT).mode()).isEqualTo(EconomyFaultControls.Mode.NORMAL);
		}
	}

	private static <T extends CommandSender> T sender(Class<T> type) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> {
			if (method.getName().equals("sendMessage")) return null;
			throw new UnsupportedOperationException(method.toString());
		}));
	}
}
