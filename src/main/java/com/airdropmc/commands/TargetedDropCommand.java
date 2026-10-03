package com.airdropmc.commands;

import com.airdropmc.AirdropCommandNames;
import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.api.DropSpawnResult;
import com.airdropmc.api.PaymentStatus;
import com.airdropmc.api.WorldPosition;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.controllers.DropController;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.packages.PackageManager;
import com.airdropmc.packages.PackageNamePolicy;
import com.airdropmc.api.DropRejectionReason;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/** Paid gifts and privileged free grants to an online player's captured location. */
public final class TargetedDropCommand {

	private TargetedDropCommand() {
	}

	public static boolean isTargeted(String command) {
		return AirdropCommandNames.GIFT.equals(command) || AirdropCommandNames.GRANT.equals(command);
	}

	public static void sendUsage(CommandSender sender, String action) {
		ChatHandler.sendError(sender, AirdropCommandNames.GIFT.equals(action)
				? MessageKey.COMMANDS_GIFT_USAGE : MessageKey.COMMANDS_GRANT_USAGE);
	}

	public static void onCommand(CommandSender sender, String[] args) {
		boolean gift = AirdropCommandNames.GIFT.equals(args[0]);
		if (gift && !(sender instanceof Player)) {
			ChatHandler.sendError(sender, MessageKey.COMMANDS_PLAYER_ONLY);
			return;
		}
		if (!sender.hasPermission("airdrop." + args[0])) {
			ChatHandler.sendError(sender, MessageKey.ERROR_TARGETED_PERMISSION,
					Map.of("permission", "airdrop." + args[0]));
			return;
		}
		Player recipient = Bukkit.getPlayerExact(args[1]);
		if (recipient == null || !recipient.isOnline()) {
			ChatHandler.sendError(sender, MessageKey.ERROR_TARGETED_PLAYER, Map.of("player", args[1]));
			return;
		}
		String packageName = args[2];
		boolean requireRecipientPermission = gift && ConfigKeys.requiresGiftRecipientPermission();
		if (gift && PackageManager.has(packageName)) {
			if (!PermissionsHelper.hasPermission((Player) sender, packageName)) {
				ChatHandler.sendError(sender, MessageKey.ERROR_INSUFFICIENT_PERMISSIONS,
						Map.of("permission", PackageNamePolicy.permissionNode(packageName)));
				return;
			}
			if (requireRecipientPermission && !PermissionsHelper.hasPermission(recipient, packageName)) {
				ChatHandler.sendError(sender, MessageKey.ERROR_GIFT_RECIPIENT_PERMISSION,
						Map.of("player", recipient.getName(), "name", packageName));
				return;
			}
		}
		DropHandle handle;
		try {
			handle = gift
					? DropController.requestGiftDrop((Player) sender, recipient, packageName,
							DropRequestOptions.defaults(), requireRecipientPermission)
					: DropController.requestSystemDrop(recipient.getLocation(), packageName,
							DropRequestOptions.defaults());
		} catch (IllegalStateException unavailable) {
			ChatHandler.sendError(sender, MessageKey.ERROR_DROP_SHUTTING_DOWN);
			return;
		}
		Map<String, String> details = new HashMap<>(Map.of(
				"action", ChatHandler.get(gift ? MessageKey.TARGETED_ACTION_GIFT : MessageKey.TARGETED_ACTION_GRANT),
				"request_id", handle.requestId().toString(), "player", recipient.getName(),
				"sender", sender.getName(), "name", packageName));
		if (!handle.outcome().toCompletableFuture().isDone() && canNotify(sender)) {
			ChatHandler.send(sender, MessageKey.TARGETED_REQUESTED, details);
		}
		handle.spawn().thenAccept(result -> {
			if (!(result instanceof DropSpawnResult.Spawned spawned)) {
				return;
			}
			details.put("name", spawned.resolvedContext().airdropPackage().name());
			details.put("amount", spawned.resolvedContext().airdropPackage().price().toPlainString());
			if (canNotify(sender)) {
				ChatHandler.send(sender, spawned.payment() == PaymentStatus.CHARGED
						? MessageKey.TARGETED_SPAWNED_CHARGED : MessageKey.TARGETED_SPAWNED, details);
			}
			if (!recipient.equals(sender) && recipient.isOnline()) {
				ChatHandler.send(recipient, MessageKey.TARGETED_RECIPIENT_INCOMING, details);
			}
		});
		handle.outcome().thenAccept(outcome -> {
			if (outcome instanceof DropOutcome.Landed landed) {
				WorldPosition position = landed.airdrop().position();
				World world = Bukkit.getWorld(position.worldId());
				details.put("world", world == null ? position.worldId().toString() : world.getName());
				details.put("x", Integer.toString((int) Math.floor(position.x())));
				details.put("y", Integer.toString((int) Math.floor(position.y())));
				details.put("z", Integer.toString((int) Math.floor(position.z())));
				if (canNotify(sender)) {
					ChatHandler.send(sender, MessageKey.TARGETED_LANDED, details);
				}
				if (!recipient.equals(sender) && recipient.isOnline()) {
					ChatHandler.send(recipient, MessageKey.TARGETED_RECIPIENT_LANDED, details);
				}
			} else if (canNotify(sender)) {
				if (outcome instanceof DropOutcome.Rejected rejected) {
					ChatHandler.sendError(sender, MessageKey.TARGETED_REJECTED, details);
					if (rejected.rejection().reason() == DropRejectionReason.SKY_NOT_CLEAR) {
						ChatHandler.sendError(sender, MessageKey.ERROR_TARGETED_SKY, details);
					} else if (gift && rejected.rejection().reason() == DropRejectionReason.INSUFFICIENT_PERMISSION
							&& !sender.hasPermission("airdrop.gift")) {
						ChatHandler.sendError(sender, MessageKey.ERROR_TARGETED_PERMISSION,
								Map.of("permission", "airdrop.gift"));
					} else if (gift && rejected.rejection().reason() == DropRejectionReason.INSUFFICIENT_PERMISSION
							&& requireRecipientPermission
							&& PermissionsHelper.hasPermission((Player) sender, packageName)
							&& !PermissionsHelper.hasPermission(recipient, packageName)) {
						ChatHandler.sendError(sender, MessageKey.ERROR_GIFT_RECIPIENT_PERMISSION, details);
					} else {
						DropCommand.sendRejection(sender, handle, rejected);
					}
				} else if (outcome instanceof DropOutcome.Failed failed) {
					ChatHandler.sendError(sender, failureKey(failed.payment()), details);
				}
			}
		});
	}

	private static MessageKey failureKey(PaymentStatus payment) {
		return switch (payment) {
			case REFUNDED -> MessageKey.TARGETED_REFUNDED;
			case REFUND_FAILED -> MessageKey.TARGETED_REFUND_FAILED;
			case CHARGED -> MessageKey.TARGETED_CHARGED_FAILED;
			case UNKNOWN -> MessageKey.TARGETED_PAYMENT_UNKNOWN;
			case NOT_APPLICABLE, REJECTED -> MessageKey.TARGETED_FAILED;
		};
	}

	private static boolean canNotify(CommandSender sender) {
		return !(sender instanceof Player player) || player.isOnline();
	}
}
