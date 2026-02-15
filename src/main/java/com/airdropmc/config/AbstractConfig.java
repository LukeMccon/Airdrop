package com.airdropmc.config;

import com.airdropmc.Airdrop;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Base class for YAML configuration file handling.
 * Provides common functionality for loading, saving, and reloading config files.
 */
public abstract class AbstractConfig {

	private final Airdrop plugin;
	private final String filename;
	private FileConfiguration config;
	private File configFile;

	protected AbstractConfig(Airdrop plugin, String filename) {
		this.plugin = plugin;
		this.filename = filename;
		initializeConfig();
	}

	/**
	 * Called during construction to set up the config file.
	 * Subclasses can override to customize initialization.
	 */
	protected void initializeConfig() {
		saveDefaultConfig();
	}

	public void reloadConfig() {
		if (configFile == null) {
			configFile = new File(plugin.getDataFolder(), filename);
		}
		config = YamlConfiguration.loadConfiguration(configFile);
		onConfigLoaded();
	}

	/**
	 * Called after config is loaded. Subclasses can override to perform
	 * additional setup (e.g., loading defaults from jar).
	 */
	protected void onConfigLoaded() {
		// Default: do nothing
	}

	public FileConfiguration getConfig() {
		if (config == null) {
			reloadConfig();
		}
		return config;
	}

	public void saveConfig() {
		if (config == null || configFile == null) {
			return;
		}
		try {
			config.save(configFile);
		} catch (IOException ex) {
			plugin.getLogger().log(Level.SEVERE, "Could not save config to " + configFile, ex);
		}
	}

	public void saveDefaultConfig() {
		if (configFile == null) {
			configFile = new File(plugin.getDataFolder(), filename);
		}
		if (!configFile.exists()) {
			onCreateDefaultConfig();
		}
	}

	/**
	 * Called when the config file doesn't exist and needs to be created.
	 * Subclasses should override to provide default config creation.
	 */
	protected abstract void onCreateDefaultConfig();

	protected Airdrop getPlugin() {
		return plugin;
	}

	protected String getFilename() {
		return filename;
	}

	protected File getConfigFile() {
		return configFile;
	}

	protected void setConfig(FileConfiguration config) {
		this.config = config;
	}
}
