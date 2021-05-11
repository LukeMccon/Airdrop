package me.gregoryw.airdrop.helpers;

import org.bukkit.entity.Player;

public class ChatHandler {
	
	public static void sendErrorMessage(Player player, String message) {
		
		player.sendMessage("This is a default error");
		
	}
	
	public static void sendErrorMessage(Player player) {
		
		sendErrorMessage(player, "This is a default error");
		
	}
	
	public static void sendMessage(Player player, String message) {
		
		player.sendMessage(message);
		
	}

}
