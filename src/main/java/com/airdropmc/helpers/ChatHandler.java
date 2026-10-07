package com.airdropmc.helpers;

import com.airdropmc.lang.LanguageManager;
import com.airdropmc.lang.MessageKey;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.HashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class ChatHandler {

	ChatHandler() {

	}

	private static volatile LanguageManager lang;

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
			return formatFallback(key.getDefault(), Map.of());
		}
		return lang.get(key);
	}

	public static String get(MessageKey key, Map<String, String> placeholders) {
		if (lang == null) {
			return formatFallback(key.getDefault(), placeholders);
		}
		return lang.get(key, placeholders);
	}

	private static String formatFallback(String message, Map<String, String> placeholders) {
		String formatted = message;
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			if (entry.getValue() != null) {
				formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
			}
		}
		formatted = formatted
				.replace("{primary}", ChatTheme.primary().toString())
				.replace("{text}", ChatTheme.text().toString())
				.replace("{accent}", ChatTheme.accent().toString())
				.replace("{success}", ChatTheme.success().toString())
				.replace("{warning}", ChatTheme.warning().toString())
				.replace("{error}", ChatTheme.error().toString())
				.replace("{error-detail}", ChatTheme.errorDetail().toString());
		return formatMessage(formatted);
	}

	public static void send(CommandSender sender, MessageKey key) {
		sendMessage(sender, get(key));
	}

	public static void send(CommandSender sender, MessageKey key, Map<String, String> placeholders) {
		sendMessage(sender, get(key, placeholders));
	}

	/** Inserts a rich sender label without losing the configured locale or chat theme. */
	public static void sendWithSender(CommandSender recipient, MessageKey key,
			Map<String, String> placeholders, Component senderLabel) {
		Map<String, String> textPlaceholders = new HashMap<>(placeholders);
		textPlaceholders.remove("sender");
		String message = formatMessage(getChatPrefix() + ChatTheme.primary() + " " + get(key, textPlaceholders));
		Component component = LegacyComponentSerializer.legacySection().deserialize(message)
				.replaceText(builder -> builder.matchLiteral("{sender}").replacement(senderLabel));
		recipient.sendMessage(component);
	}

	public static void sendWithoutPrefix(CommandSender sender, MessageKey key, Map<String, String> placeholders) {
		String formattedMessage = formatMessage(get(key, placeholders));
		sender.sendMessage(formattedMessage);
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

		sender.sendMessage(formattedMessage);
	}

	/**
	 * Sends message to a CommandSender
	 * @param sender who to send the message to
	 * @param message the message to send
	 */
	public static void sendMessage(CommandSender sender, String message) {
		String formattedMessage = formatMessage(getChatPrefix() + ChatTheme.primary() + " " + message);

		sender.sendMessage(formattedMessage);

	}

	/**
	 * Logs info message to the console
	 * @param message to log
	 */
	public static void logMessage(String message) {
		AirdropLogger.info(message);
	}

}
