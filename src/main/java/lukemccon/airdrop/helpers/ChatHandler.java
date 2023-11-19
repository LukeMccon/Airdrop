package lukemccon.airdrop.helpers;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatHandler {

	ChatHandler() {

	}

	private static String ChatPrefix = ChatColor.BLUE + "[" + ChatColor.WHITE + "Airdrop" + ChatColor.BLUE + "]";

	/**
	 * Sends error message to a CommandSender
	 * @param sender who to send the message to
	 * @param message the message to send
	 */
	public static void sendErrorMessage(CommandSender sender, String message) {

		String formattedMessage = ChatHandler.ChatPrefix + ChatColor.RED + " "+ message;

		if (sender instanceof Player) {
			sender.sendMessage(formattedMessage);
		} else {
			Bukkit.getServer().getConsoleSender().sendMessage(formattedMessage);
		}


	}

	/**
	 * Sends message to a CommandSender
	 * @param sender who to send the message to
	 * @param message the message to send
	 */
	public static void sendMessage(CommandSender sender, String message) {
		String formattedMessage = ChatHandler.ChatPrefix + ChatColor.BLUE + " " + message;

		if (sender instanceof Player) {
			sender.sendMessage(formattedMessage);
		} else {
			Bukkit.getServer().getConsoleSender().sendMessage(formattedMessage);
		}

	}

	/**
	 * Logs info message to the console
	 * @param message to log
	 */
	public static void logMessage(String message) {
		Bukkit.getLogger().info(message);
	}

}
