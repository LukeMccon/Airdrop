package com.airdropmc.config;

import com.airdropmc.Airdrop;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

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

	public boolean saveConfig() {
		return saveConfig(config);
	}

	public boolean saveConfig(FileConfiguration candidate) {
		if (candidate == null || configFile == null) {
			return false;
		}
		Path temporaryFile = null;
		try {
			Path targetFile = configFile.toPath().toAbsolutePath();
			Files.createDirectories(targetFile.getParent());
			temporaryFile = Files.createTempFile(targetFile.getParent(), configFile.getName() + ".", ".tmp");
			candidate.save(temporaryFile.toFile());
			try {
				Files.move(temporaryFile, targetFile, ATOMIC_MOVE, REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(temporaryFile, targetFile, REPLACE_EXISTING);
			}
			config = candidate;
			return true;
		} catch (IOException | RuntimeException ex) {
			if (temporaryFile != null) {
				try {
					Files.deleteIfExists(temporaryFile);
				} catch (IOException | RuntimeException cleanupException) {
					ex.addSuppressed(cleanupException);
				}
			}
			plugin.getLogger().log(Level.SEVERE, "Could not save config to " + configFile.getName(), ex);
			return false;
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
