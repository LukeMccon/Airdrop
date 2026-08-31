package com.airdropmc.commands;

import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.api.DropSpawnResult;
import com.airdropmc.api.PaymentStatus;
import com.airdropmc.controllers.DropController;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.packages.PackageNamePolicy;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/** Translates typed request results into localized command feedback. */
public final class DropCommand {

	private DropCommand() {
	}

	public static void onCommand(CommandSender sender, String[] args) {
		if (!(sender instanceof Player player)) {
			ChatHandler.sendError(sender, MessageKey.COMMANDS_PLAYER_ONLY);
			return;
		}
		String packageName = args[0];
		DropHandle handle;
		try {
			handle = DropController.requestPlayerDrop(
					player, packageName, DropRequestOptions.defaults());
		} catch (IllegalStateException unavailable) {
			ChatHandler.sendError(player, MessageKey.ERROR_DROP_SHUTTING_DOWN);
			return;
		}

		handle.spawn().thenAccept(result -> sendSpawnFeedback(player, result));
		handle.outcome().thenAccept(outcome -> sendOutcomeFeedback(player, outcome));
	}

	private static void sendSpawnFeedback(Player player, DropSpawnResult result) {
		if (result instanceof DropSpawnResult.Spawned spawned
				&& spawned.payment() == PaymentStatus.CHARGED) {
			ChatHandler.send(player, MessageKey.DROP_CHARGED, Map.of(
					"amount", spawned.resolvedContext().airdropPackage().price().toPlainString()));
		}
	}

	private static void sendOutcomeFeedback(Player player, DropOutcome outcome) {
		if (outcome instanceof DropOutcome.Rejected rejected) {
			sendRejection(player, rejected);
			return;
		}
		if (outcome instanceof DropOutcome.Failed failed) {
			if (failed.payment() == PaymentStatus.REFUNDED) {
				ChatHandler.sendError(player, MessageKey.DROP_REFUNDED);
			} else {
				ChatHandler.sendError(player, MessageKey.DROP_FAILED);
			}
		}
	}

	private static void sendRejection(Player player, DropOutcome.Rejected rejected) {
		switch (rejected.rejection().reason()) {
			case UNKNOWN_PACKAGE -> ChatHandler.sendError(
					player,
					MessageKey.ERROR_PACKAGE_NOT_FOUND,
					Map.of("name", rejected.descriptor().requestedPackageName()));
			case INSUFFICIENT_PERMISSION -> {
				String canonical = PackageNamePolicy.requireCanonical(
						rejected.descriptor().requestedPackageName());
				ChatHandler.sendError(player, MessageKey.ERROR_INSUFFICIENT_PERMISSIONS, Map.of(
						"permission", PackageNamePolicy.permissionNode(canonical),
						"package", canonical));
			}
			case SKY_NOT_CLEAR, INVALID_TARGET ->
					ChatHandler.sendError(player, MessageKey.ERROR_SKY_NOT_CLEAR);
			case REQUEST_PENDING ->
					ChatHandler.sendError(player, MessageKey.ERROR_DROP_REQUEST_PENDING);
			case COOLDOWN -> ChatHandler.sendError(player, MessageKey.ERROR_DROP_COOLDOWN,
					Map.of("seconds", Long.toString(rejected.rejection().retryAfter()
							.orElseThrow().toSeconds())));
			case FALLING_CAPACITY ->
					ChatHandler.sendError(player, MessageKey.ERROR_DROP_FALLING_LIMIT);
			case LANDED_CAPACITY ->
					ChatHandler.sendError(player, MessageKey.ERROR_DROP_LANDED_LIMIT);
			case LOCATION_RESERVED ->
					ChatHandler.sendError(player, MessageKey.ERROR_DROP_LOCATION_RESERVED);
			case ECONOMY_DISABLED, ECONOMY_PROVIDER_UNAVAILABLE ->
					ChatHandler.sendError(player, MessageKey.ERROR_ECONOMY_UNAVAILABLE);
			case INSUFFICIENT_FUNDS -> ChatHandler.sendError(
					player,
					MessageKey.ERROR_CANNOT_AFFORD,
					Map.of(
							"player", player.getName(),
							"price", rejected.resolvedContext().orElseThrow()
									.airdropPackage().price().toPlainString()));
			case PAYMENT_REJECTED, AFFORDABILITY_UNKNOWN, CANCELLED ->
					ChatHandler.sendError(player, MessageKey.DROP_FAILED);
			case SERVICE_UNAVAILABLE, SHUTTING_DOWN ->
					ChatHandler.sendError(player, MessageKey.ERROR_DROP_SHUTTING_DOWN);
		}
	}
}
