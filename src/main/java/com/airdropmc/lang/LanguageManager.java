package com.airdropmc.lang;

import com.airdropmc.Airdrop;
import com.airdropmc.helpers.ChatTheme;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class LanguageManager {
	private final Airdrop plugin;
	private FileConfiguration langConfig;
	private String currentLanguage;

	public LanguageManager(Airdrop plugin) {
		this.plugin = plugin;
	}

	public void loadLanguage(String langCode) {
		if (langCode == null || langCode.isBlank()) {
			langCode = "en";
		}
		currentLanguage = langCode;

		File langFolder = new File(plugin.getDataFolder(), "lang");
		if (!langFolder.exists()) {
			langFolder.mkdirs();
		}

		String fileName = langCode + ".yml";
		File langFile = new File(langFolder, fileName);
		if (!langFile.exists()) {
			try {
				plugin.saveResource("lang/" + fileName, false);
			} catch (IllegalArgumentException ignored) {
				// Missing language resource; defaults will be used.
			}
		}

		langConfig = YamlConfiguration.loadConfiguration(langFile);

		InputStream defaultStream = plugin.getResource("lang/" + fileName);
		if (defaultStream != null) {
			YamlConfiguration defaultConfig = YamlConfiguration
					.loadConfiguration(new InputStreamReader(defaultStream));
			langConfig.setDefaults(defaultConfig);

			// Sync missing keys from defaults to user's config
			boolean updated = false;
			for (String key : defaultConfig.getKeys(true)) {
				if (!langConfig.isSet(key)) {
					langConfig.set(key, defaultConfig.get(key));
					updated = true;
				}
			}
			if (updated) {
				try {
					langConfig.save(langFile);
				} catch (java.io.IOException e) {
					plugin.getLogger().warning("Failed to save updated language file: " + e.getMessage());
				}
			}
		}
	}

	public void reload() {
		if (currentLanguage == null || currentLanguage.isBlank()) {
			loadLanguage("en");
			return;
		}
		loadLanguage(currentLanguage);
	}

	public String get(MessageKey key) {
		return format(getRaw(key), null);
	}

	public String get(MessageKey key, Map<String, String> placeholders) {
		return format(getRaw(key), placeholders);
	}

	private String getRaw(MessageKey key) {
		if (langConfig == null) {
			return key.getDefault();
		}
		String message = langConfig.getString(key.getKey());
		if (message == null || message.isBlank()) {
			return key.getDefault();
		}
		return message;
	}

	private String format(String message, Map<String, String> placeholders) {
		if (message == null) {
			return "";
		}
		String formatted = replacePlaceholders(message, placeholders);
		formatted = replaceThemePlaceholders(formatted);
		return ChatColor.translateAlternateColorCodes('&', formatted);
	}

	private String replacePlaceholders(String message, Map<String, String> placeholders) {
		if (placeholders == null || placeholders.isEmpty()) {
			return message;
		}
		String formatted = message;
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			if (entry.getValue() == null) {
				continue;
			}
			formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return formatted;
	}

	private String replaceThemePlaceholders(String message) {
		Map<String, String> theme = new HashMap<>();
		theme.put("primary", ChatTheme.primary().toString());
		theme.put("text", ChatTheme.text().toString());
		theme.put("accent", ChatTheme.accent().toString());
		theme.put("success", ChatTheme.success().toString());
		theme.put("warning", ChatTheme.warning().toString());
		theme.put("error", ChatTheme.error().toString());
		theme.put("error-detail", ChatTheme.errorDetail().toString());

		String formatted = message;
		for (Map.Entry<String, String> entry : theme.entrySet()) {
			formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return formatted;
	}

	public String getCurrentLanguage() {
		return currentLanguage;
	}
}
