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
	private final CommandTreeProbe treeProbe;

	public ConsoleSendCommand(Plugin plugin) {
		this.plugin = plugin;
		this.treeProbe = new CommandTreeProbe(plugin);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof ConsoleCommandSender) || args.length < 1) {
			return false;
		}
		if (args[0].equals("tree") && args.length == 2) {
			treeProbe.inspect(args[1]);
			return true;
		}
		var console = Bukkit.getConsoleSender();
		PermissionAttachment denial = switch (args[0]) {
			case "deny-cost" -> console.addAttachment(plugin, "airdrop.cost.bypass", false);
			case "deny-send" -> console.addAttachment(plugin, "airdrop.send", false);
			case "inspect" -> null;
			default -> throw new IllegalArgumentException("Unknown console fixture action");
		};
		try {
			var dispatcher = CommandTreeProbe.dispatcher();
			var source = CommandTreeProbe.source(console);
			var completions = dispatcher.getCompletionSuggestions(dispatcher.parse("airdrop se", source)).join();
			console.sendMessage("AIRDR_CONSOLE native=" + console.getClass().getName()
					+ " send=" + console.hasPermission("airdrop.send")
					+ " cost=" + console.hasPermission("airdrop.cost.bypass")
					+ " costSet=" + console.isPermissionSet("airdrop.cost.bypass")
					+ " complete=" + completions.getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).toList());
			if (args.length > 1 && args[1].equals("airdrop")) {
				// VanillaCommandWrapper.execute also defers nested dispatch. Invoke the real
				// installed Brigadier dispatcher synchronously, before removing the denial.
				CommandTreeProbe.dispatcher().execute(String.join(" ", Arrays.copyOfRange(args, 1, args.length)),
						CommandTreeProbe.source(console));
			}
		} catch (ReflectiveOperationException | com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
			throw new IllegalStateException("Native console dispatch failed", failure);
		} finally {
			if (denial != null) {
				console.removeAttachment(denial);
			}
		}
		return true;
	}
}
