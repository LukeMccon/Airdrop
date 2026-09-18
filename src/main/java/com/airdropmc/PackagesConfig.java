package com.airdropmc;

import com.airdropmc.config.AbstractConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.ApiStatus;

/**
 * Holds the published packages configuration candidate.
 */
@ApiStatus.Internal
public class PackagesConfig extends AbstractConfig {

	public PackagesConfig(FileConfiguration config) {
		super(config);
	}
}
