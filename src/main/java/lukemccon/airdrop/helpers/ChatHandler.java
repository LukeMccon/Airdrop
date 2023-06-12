package lukemccon.airdrop.helpers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class ChatHandler {

	private static String ChatPrefix = ChatColor.BLUE + "[" + ChatColor.WHITE + "Airdrop" + ChatColor.BLUE + "]";
	public static void sendErrorMessage(Player player, String message) {
		
		player.sendMessage( ChatHandler.ChatPrefix + ChatColor.RED + " "+ message);
		
	}
	public static void sendMessage(Player player, String message) {
		
		player.sendMessage(ChatHandler.ChatPrefix + ChatColor.BLUE + " " + message);
		
	}
	
	public static void logMessage(String message) {
		Bukkit.getLogger().info(message);
	}

}
