package com.airdropmc.commands;

import com.airdropmc.AirdropCommandNames;
import org.bukkit.Location;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
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
import com.airdropmc.helpers.SendPermissions;
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

/** Targeted sends share player admission and charge only the initiating sender. */
public final class TargetedDropCommand {

	private TargetedDropCommand() {
	}

	public static boolean isTargeted(String command) {
		return AirdropCommandNames.SEND.equals(command);
	}

	public static void sendUsage(CommandSender sender) {
		ChatHandler.sendError(sender, MessageKey.COMMANDS_SEND_USAGE);
	}

	public static boolean canSend(CommandSender sender) {
		return SendPermissions.canSend(sender);
	}

	public static boolean isCoordinate(String value) {
		try {
			double coordinate = Double.parseDouble(value);
			return Double.isFinite(coordinate) && Math.abs(coordinate) < 30_000_000;
		} catch (NumberFormatException invalid) {
			return false;
		}
	}

	public static void onCommand(CommandSender sender, String[] args) {
		if (!SendPermissions.hasSendPermission(sender)) {
			ChatHandler.sendError(sender, MessageKey.ERROR_TARGETED_PERMISSION,
					Map.of("permission", "airdrop.send"));
			return;
		}
		boolean costExempt = SendPermissions.isCostExempt(sender);
		if (!(sender instanceof Player) && !costExempt) {
			ChatHandler.sendError(sender, MessageKey.ERROR_SEND_CONSOLE_COST);
			return;
		}
		if (args.length < 3 || args.length > 5) {
			sendUsage(sender);
			return;
		}
		String packageName = args[1];
		Player recipient = args.length == 3 ? Bukkit.getPlayerExact(args[2]) : null;
		Location destination;
		String destinationName;
		if (args.length == 3) {
			if (recipient == null || !recipient.isOnline()) {
				ChatHandler.sendError(sender, MessageKey.ERROR_TARGETED_PLAYER, Map.of("player", args[2]));
				return;
			}
			destination = recipient.getLocation();
			destinationName = recipient.getName();
		} else {
			if (!isCoordinate(args[2]) || !isCoordinate(args[3])) {
				ChatHandler.sendError(sender, MessageKey.ERROR_SEND_COORDINATES);
				return;
			}
			if (args.length == 4 && !(sender instanceof Player)) {
				ChatHandler.sendError(sender, MessageKey.ERROR_SEND_CONSOLE_WORLD);
				return;
			}
			World world = args.length == 5 ? Bukkit.getWorld(args[4]) : ((Player) sender).getWorld();
			if (world == null) {
				ChatHandler.sendError(sender, MessageKey.ERROR_SEND_WORLD, Map.of("world", args[4]));
				return;
			}
			destination = new Location(world, Double.parseDouble(args[2]), world.getMaxHeight() - 1.0,
					Double.parseDouble(args[3]));
			destinationName = args[2] + ", " + args[3] + " in " + world.getName();
		}
		boolean requireRecipientPermission = ConfigKeys.requiresGiftRecipientPermission();
		if (PackageManager.has(packageName)) {
			if (sender instanceof Player player && !PermissionsHelper.hasPermission(player, packageName)) {
				ChatHandler.sendError(sender, MessageKey.ERROR_INSUFFICIENT_PERMISSIONS,
						Map.of("permission", PackageNamePolicy.permissionNode(packageName)));
				return;
			}
			if (recipient != null && requireRecipientPermission && !PermissionsHelper.hasPermission(recipient, packageName)) {
				ChatHandler.sendError(sender, MessageKey.ERROR_GIFT_RECIPIENT_PERMISSION,
						Map.of("player", destinationName, "name", packageName));
				return;
			}
		}
		DropHandle handle;
		try {
			handle = recipient != null
					? DropController.requestSendDrop(sender, recipient, packageName,
							DropRequestOptions.defaults(), requireRecipientPermission)
					: DropController.requestSendDrop(sender, destination, packageName, DropRequestOptions.defaults());
		} catch (IllegalStateException unavailable) {
			ChatHandler.sendError(sender, MessageKey.ERROR_DROP_SHUTTING_DOWN);
			return;
		}

		Component senderLabel = sender instanceof Player player
				? Component.object(ObjectContents.playerHead(player.getUniqueId()))
						.append(Component.space()).append(Component.text(player.getName()))
				: Component.text(sender.getName());

		Map<String, String> details = new HashMap<>(Map.of(
				"action", ChatHandler.get(MessageKey.TARGETED_ACTION_SEND),
				"request_id", handle.requestId().toString(), "player", destinationName,
				"sender", sender.getName(), "name", packageName));
		if (!handle.outcome().toCompletableFuture().isDone() && canNotify(sender)) {
			if (handle.context().isEmpty()) {
				ChatHandler.send(sender, MessageKey.DROP_PREPARING);
			} else {
				var context = handle.context().orElseThrow();
				String amount = context.airdropPackage().price().toPlainString();
				details.put("amount", costExempt ? "0" : amount);
				details.put("cost", ChatHandler.get(costExempt ? MessageKey.TARGETED_COST_EXEMPT
						: context.airdropPackage().price().signum() == 0 ? MessageKey.TARGETED_COST_ZERO
								: MessageKey.TARGETED_COST_PAID, Map.of("amount", amount)));
				ChatHandler.send(sender, MessageKey.TARGETED_REQUESTED, details);
			}
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
			if (recipient != null && !recipient.equals(sender) && recipient.isOnline()) {
				ChatHandler.sendWithSender(recipient, MessageKey.TARGETED_RECIPIENT_INCOMING, details, senderLabel);
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
				if (recipient != null && !recipient.equals(sender) && recipient.isOnline()) {
					ChatHandler.sendWithSender(recipient, MessageKey.TARGETED_RECIPIENT_LANDED, details, senderLabel);
				}
			} else if (canNotify(sender)) {
				if (outcome instanceof DropOutcome.Rejected rejected) {
					ChatHandler.sendError(sender, MessageKey.TARGETED_REJECTED, details);
					if (rejected.rejection().reason() == DropRejectionReason.SKY_NOT_CLEAR) {
						ChatHandler.sendError(sender, MessageKey.ERROR_TARGETED_SKY, details);
					} else if (rejected.rejection().reason() == DropRejectionReason.INSUFFICIENT_PERMISSION
							&& !SendPermissions.hasSendPermission(sender)) {
						ChatHandler.sendError(sender, MessageKey.ERROR_TARGETED_PERMISSION,
								Map.of("permission", "airdrop.send"));
					} else if (rejected.rejection().reason() == DropRejectionReason.INSUFFICIENT_PERMISSION
							&& recipient != null && requireRecipientPermission
							&& (!(sender instanceof Player player) || PermissionsHelper.hasPermission(player, packageName))
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
