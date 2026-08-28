package com.airdropmc.helpers;

import com.airdropmc.Airdrop;
import com.airdropmc.config.ConfigKeys;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized logging helpers for Airdrop.
 */
public final class AirdropLogger {

	private AirdropLogger() {
		// Utility class
	}

	private static Logger getLogger() {
		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin != null) {
			return plugin.getLogger();
		}
		return Logger.getLogger(Airdrop.PLUGIN_NAME);
	}

	public static void info(String message) {
		getLogger().info(message);
	}

	public static void warning(String message) {
		getLogger().warning(message);
	}

	public static void severe(String message) {
		getLogger().severe(message);
	}

	public static void log(Level level, String message, Throwable throwable) {
		getLogger().log(level, message, throwable);
	}

	public static void debug(String message) {
		if (ConfigKeys.isDebugLoggingEnabled()) {
			getLogger().info("[DEBUG] " + message);
		}
	}
}
