package com.airdropmc;

import com.airdropmc.config.AbstractConfig;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Handles the main config.yml configuration file.
 */
public class Config extends AbstractConfig {

    private static final String CONFIG_FILENAME = "config.yml";

    public Config(Airdrop plugin) {
        super(plugin, CONFIG_FILENAME);
    }

    @Override
    protected void onConfigLoaded() {
		// Load defaults from the jar resource.
		try (InputStream defaultStream = getPlugin().getResource(getFilename())) {
			if (defaultStream == null) {
				return;
			}
			try (InputStreamReader reader = new InputStreamReader(defaultStream, StandardCharsets.UTF_8)) {
				YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(reader);
				getConfig().setDefaults(defaultConfig);
			}
		} catch (IOException exception) {
			getPlugin().getLogger().warning("Failed to load default config resource: " + exception.getMessage());
		}
    }

    @Override
    protected void onCreateDefaultConfig() {
        getPlugin().saveResource(getFilename(), false);
    }
}
