package me.lukemccon.airdrop.helpers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class ChatHandler {
	
	public static void sendErrorMessage(Player player, String message) {
		
		player.sendMessage(ChatColor.RED + message);
		
	}
	
	public static void sendErrorMessage(Player player) {
		
		sendErrorMessage(player, "This is a default error");
		
	}
	
	public static void sendMessage(Player player, String message) {
		
		player.sendMessage(message);
		
	}
	
	public static void logMessage(String message) {
		Bukkit.getLogger().info(message);
	}

}
