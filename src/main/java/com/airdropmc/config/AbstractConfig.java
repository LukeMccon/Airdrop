package com.airdropmc.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Base class for an already-loaded configuration candidate.
 */
@ApiStatus.Internal
public abstract class AbstractConfig {

	private final FileConfiguration config;

	protected AbstractConfig(FileConfiguration config) {
		this.config = Objects.requireNonNull(config, "config");
	}

	public FileConfiguration getConfig() {
		return config;
	}
}
