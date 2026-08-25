package com.airdropmc;

import com.airdropmc.config.AbstractConfig;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Holds the published packages configuration candidate.
 */
public class PackagesConfig extends AbstractConfig {

	public PackagesConfig(FileConfiguration config) {
		super(config);
	}
}
