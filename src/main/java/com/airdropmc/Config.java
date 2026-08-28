package com.airdropmc;

import com.airdropmc.config.AbstractConfig;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Holds the published main configuration candidate.
 */
public class Config extends AbstractConfig {

	public Config(FileConfiguration config) {
		super(config);
	}
}
