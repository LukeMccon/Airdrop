package com.airdropmc;

import com.airdropmc.config.AbstractConfig;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;

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
        // Load defaults from the jar resource
        InputStream defaultStream = getPlugin().getResource(getFilename());
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
            getConfig().setDefaults(defaultConfig);
        }
    }

    @Override
    protected void onCreateDefaultConfig() {
        getPlugin().saveResource(getFilename(), false);
    }
}
