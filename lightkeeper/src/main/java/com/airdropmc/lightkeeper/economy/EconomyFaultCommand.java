package com.airdropmc.lightkeeper.economy;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Console-only controls; each accepted action is acknowledged by a caller-supplied token. */
final class EconomyFaultCommand {
	private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{1,64}");
	private final EconomyFaultControls controls;
	private final Consumer<String> log;

	EconomyFaultCommand(EconomyFaultControls controls, Consumer<String> log) {
		this.controls = controls;
		this.log = log;
	}

	// fault <uuid> <WITHDRAW|DEPOSIT> <HOLD|REJECT|EXCEPTION> <token>
	// release <uuid> <WITHDRAW|DEPOSIT> <token> ; clear <uuid> <token>
	boolean execute(CommandSender sender, String[] arguments) {
		if (!(sender instanceof ConsoleCommandSender) || arguments.length < 3) {
			return false;
		}
		try {
			String action = arguments[0].toLowerCase(Locale.ROOT);
			UUID playerId = UUID.fromString(arguments[1]);
			String token = arguments[arguments.length - 1];
			if (!TOKEN.matcher(token).matches()) {
				throw new IllegalArgumentException("invalid correlation token");
			}
			switch (action) {
				case "fault" -> {
					if (arguments.length != 5) return false;
					controls.arm(playerId, operation(arguments[2]),
							EconomyFaultControls.Mode.valueOf(arguments[3].toUpperCase(Locale.ROOT)));
				}
				case "release" -> {
					if (arguments.length != 4) return false;
					if (!controls.release(playerId, operation(arguments[2]))) {
						throw new IllegalArgumentException("no held operation for account");
					}
				}
				case "clear" -> {
					if (arguments.length != 3) return false;
					controls.clear(playerId);
				}
				default -> { return false; }
			}
			log.accept("AIRDR_ECONOMY_CONTROL token=" + token + " action=" + action + " player=" + playerId);
			return true;
		} catch (IllegalArgumentException failure) {
			sender.sendMessage("Invalid LightKeeper economy fault command: " + failure.getMessage());
			return false;
		}
	}

	private static EconomyOperationType operation(String value) {
		EconomyOperationType operation = EconomyOperationType.valueOf(value.toUpperCase(Locale.ROOT));
		if (operation == EconomyOperationType.CAN_WITHDRAW) {
			throw new IllegalArgumentException("only withdrawal/refund faults are supported");
		}
		return operation;
	}
}
