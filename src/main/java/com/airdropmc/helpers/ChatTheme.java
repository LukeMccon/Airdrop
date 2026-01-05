package com.airdropmc.helpers;

import com.airdropmc.Airdrop;
import com.airdropmc.Config;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Locale;

public final class ChatTheme {
	private static final String CONFIG_PREFIX = "ui.chat.colors";

	private ChatTheme() {
	}

	public static ChatColor primary() {
		return getColor("primary", ChatColor.BLUE);
	}

	public static ChatColor text() {
		return getColor("text", ChatColor.WHITE);
	}

	public static ChatColor accent() {
		return getColor("accent", ChatColor.AQUA);
	}

	public static ChatColor success() {
		return getColor("success", ChatColor.GREEN);
	}

	public static ChatColor warning() {
		return getColor("warning", ChatColor.YELLOW);
	}

	public static ChatColor error() {
		return getColor("error", ChatColor.RED);
	}

	public static ChatColor errorDetail() {
		return getColor("error-detail", ChatColor.DARK_RED);
	}

	private static ChatColor getColor(String key, ChatColor fallback) {
		Config config = Airdrop.getConfiguration();
		if (config == null) {
			return fallback;
		}
		FileConfiguration fileConfig = config.getConfig();
		if (fileConfig == null) {
			return fallback;
		}
		String value = fileConfig.getString(CONFIG_PREFIX + "." + key);
		if (value == null || value.isBlank()) {
			return fallback;
		}
		try {
			return ChatColor.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			return fallback;
		}
	}
}
