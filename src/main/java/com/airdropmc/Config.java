package com.airdropmc;

import com.airdropmc.config.AbstractConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.ApiStatus;

/**
 * Holds the published main configuration candidate.
 */
@ApiStatus.Internal
public class Config extends AbstractConfig {

	public Config(FileConfiguration config) {
		super(config);
	}
}
