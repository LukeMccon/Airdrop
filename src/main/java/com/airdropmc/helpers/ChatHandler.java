package com.airdropmc.helpers;

import com.airdropmc.lang.LanguageManager;
import com.airdropmc.lang.MessageKey;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class ChatHandler {

	ChatHandler() {

	}

	private static LanguageManager lang;

	public static void init(LanguageManager langManager) {
		lang = langManager;
	}

	private static String getChatPrefix() {
		if (lang != null) {
			return lang.get(MessageKey.PREFIX);
		}
		return ChatTheme.primary() + "[" + ChatTheme.text() + "Airdrop" + ChatTheme.primary() + "]";
	}

	private static String formatMessage(String message) {
		if (message == null) {
			return "";
		}
		return ChatColor.translateAlternateColorCodes('&', message);
	}

	public static String get(MessageKey key) {
		if (lang == null) {
			return key.getDefault();
		}
		return lang.get(key);
	}

	public static String get(MessageKey key, Map<String, String> placeholders) {
		if (lang == null) {
			return key.getDefault();
		}
		return lang.get(key, placeholders);
	}

	public static void send(CommandSender sender, MessageKey key) {
		sendMessage(sender, get(key));
	}

	public static void send(CommandSender sender, MessageKey key, Map<String, String> placeholders) {
		sendMessage(sender, get(key, placeholders));
	}

	public static void sendWithoutPrefix(CommandSender sender, MessageKey key, Map<String, String> placeholders) {
		String formattedMessage = formatMessage(get(key, placeholders));
		if (sender instanceof Player) {
			sender.sendMessage(formattedMessage);
		} else {
			Bukkit.getServer().getConsoleSender().sendMessage(formattedMessage);
		}
	}

	public static void sendError(CommandSender sender, MessageKey key) {
		sendErrorMessage(sender, get(key));
	}

	public static void sendError(CommandSender sender, MessageKey key, Map<String, String> placeholders) {
		sendErrorMessage(sender, get(key, placeholders));
	}

	/**
	 * Sends error message to a CommandSender
	 * @param sender who to send the message to
	 * @param message the message to send
	 */
	public static void sendErrorMessage(CommandSender sender, String message) {

		String formattedMessage = formatMessage(getChatPrefix() + ChatTheme.error() + " " + message);

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
		String formattedMessage = formatMessage(getChatPrefix() + ChatTheme.primary() + " " + message);

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
		AirdropLogger.info(message);
	}

}
