package com.airdropmc.helpers;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;

/** Shared authorization and payment policy for targeted sends only. */
public final class SendPermissions {

	private static final String SEND = "airdrop.send";
	private static final String COST_BYPASS = "airdrop.cost.bypass";

	private SendPermissions() {
	}

	public static boolean canSend(CommandSender sender) {
		return hasSendPermission(sender) && (sender instanceof Player || isCostExempt(sender));
	}

	public static boolean hasSendPermission(CommandSender sender) {
		return sender.hasPermission(SEND) && (!isConsole(sender) || !hasAttachmentDenial(sender, SEND));
	}

	public static boolean isCostExempt(CommandSender sender) {
		return isConsole(sender) ? !hasAttachmentDenial(sender, COST_BYPASS) : sender.hasPermission(COST_BYPASS);
	}

	private static boolean isConsole(CommandSender sender) {
		return sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender;
	}

	private static boolean hasAttachmentDenial(CommandSender sender, String permission) {
		// Bukkit defaults have no attachment; inherited attachment children retain their attachment.
		// Inspect the effective entry because Paper's console has-all-permissions can mask a denial.
		return sender.getEffectivePermissions().stream().anyMatch(info -> permission.equals(info.getPermission())
				&& info.getAttachment() != null && !info.getValue());
	}
}
