package com.airdropmc.config;

import com.airdropmc.Airdrop;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Stores all configuration keys and provides methods to access config values
 */
public final class ConfigKeys {
    private ConfigKeys() {
        // Prevent instantiation
    }

    // Settings paths
    public static final String SETTINGS_LANDING_PARTICLE_EFFECTS = "settings.particles.landing-effects";
    public static final String SETTINGS_CONTINUOUS_PARTICLE_EFFECTS = "settings.particles.continuous-effects";

    // Economy paths
    public static final String ECONOMY_ENABLED = "economy.enabled";

    // Settings getters
    public static boolean shouldShowLandingParticleEffects() {
        return getConfig().getBoolean(SETTINGS_LANDING_PARTICLE_EFFECTS, true);
    }

    public static boolean shouldShowContinuousParticleEffects() {
        return getConfig().getBoolean(SETTINGS_CONTINUOUS_PARTICLE_EFFECTS, true);
    }

    // Economy getters
    public static boolean isEconomyEnabled() {
        return getConfig().getBoolean(ECONOMY_ENABLED, true);
    }

    // Helper method to get config
    private static FileConfiguration getConfig() {
        return Airdrop.getConfiguration().getConfig();
    }
}
