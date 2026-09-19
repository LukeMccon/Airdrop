package com.airdropmc.integration.support;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.regex.Pattern;

/** AIRDR-87: use an ordinary close, not a synthetic InventoryCloseEvent. */
public final class MenuCloseCommand implements CommandExecutor {
	private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{1,80}");

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof ConsoleCommandSender) || args.length != 2 || !TOKEN.matcher(args[1]).matches()) {
			return false;
		}
		UUID playerId;
		try {
			playerId = UUID.fromString(args[0]);
		} catch (IllegalArgumentException failure) {
			sender.sendMessage("AIRDR_MENU_CLOSE token=" + args[1] + " success=false invalid-player");
			return true;
		}
		Player player = Bukkit.getPlayer(playerId);
		if (player == null || !player.isOnline()) {
			sender.sendMessage("AIRDR_MENU_CLOSE token=" + args[1] + " player=" + playerId + " success=false");
			return true;
		}
		player.closeInventory();
		sender.sendMessage("AIRDR_MENU_CLOSE token=" + args[1] + " player=" + playerId + " success=true");
		return true;
	}
}
