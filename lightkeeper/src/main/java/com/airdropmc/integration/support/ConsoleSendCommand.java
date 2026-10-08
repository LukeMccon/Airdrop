package com.airdropmc.integration.support;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;

/** Runs against Paper's native console, with temporary denials restored in the same call. */
public final class ConsoleSendCommand implements CommandExecutor {

	private final Plugin plugin;

	public ConsoleSendCommand(Plugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof ConsoleCommandSender) || args.length < 1) {
			return false;
		}
		var console = Bukkit.getConsoleSender();
		PermissionAttachment denial = switch (args[0]) {
			case "deny-cost" -> console.addAttachment(plugin, "airdrop.cost.bypass", false);
			case "deny-send" -> console.addAttachment(plugin, "airdrop.send", false);
			case "inspect" -> null;
			default -> throw new IllegalArgumentException("Unknown console fixture action");
		};
		try {
			var airdrop = Bukkit.getPluginCommand("airdrop");
			console.sendMessage("AIRDR_CONSOLE native=" + console.getClass().getName()
					+ " send=" + console.hasPermission("airdrop.send")
					+ " cost=" + console.hasPermission("airdrop.cost.bypass")
					+ " costSet=" + console.isPermissionSet("airdrop.cost.bypass")
					+ " complete=" + airdrop.tabComplete(console, "airdrop", new String[]{"se"}));
			if (args.length > 1 && args[1].equals("airdrop")) {
				// Paper may defer nested dispatch; execute the registered command while the denial is attached.
				airdrop.execute(console, "airdrop", Arrays.copyOfRange(args, 2, args.length));
			}
		} finally {
			if (denial != null) {
				console.removeAttachment(denial);
			}
		}
		return true;
	}
}
