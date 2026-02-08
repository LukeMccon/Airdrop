package com.airdropmc.config;

import com.airdropmc.Airdrop;
import com.airdropmc.Config;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Stores all configuration keys and provides methods to access config values
 */
public final class ConfigKeys {
    private static final FileConfiguration FALLBACK_CONFIG = new YamlConfiguration();

    private ConfigKeys() {
        // Prevent instantiation
    }

    // Drop settings paths
    public static final String DROP_LANDING_PARTICLE_EFFECTS = "drop.particles.landing-effects";
    public static final String DROP_CONTINUOUS_PARTICLE_EFFECTS = "drop.particles.continuous-effects";
    public static final String DROP_FLARE_PARTICLE_EFFECTS = "drop.particles.flare-effects";
    public static final String DROP_SMOKE_ENABLED = "drop.particles.smoke.enabled";
    public static final String DROP_SMOKE_HEIGHT = "drop.particles.smoke.height";
    public static final String DROP_PARACHUTE_CHICKEN_COUNT = "drop.parachute.chicken-count";
    public static final String DROP_FALLING_SPEED = "drop.falling-speed";
    private static final String DROP_FALLING_SPEED_LEGACY = "drop.parachute.falling-speed";
    public static final String DROP_HEIGHT = "drop.height";

    // Economy paths
    public static final String ECONOMY_ENABLED = "economy.enabled";

    // Drop settings getters
    public static int getParachuteChickenCount() {
        return getConfig().getInt(DROP_PARACHUTE_CHICKEN_COUNT, 5);
    }

    public static double getDropFallingSpeed() {
        FileConfiguration config = getConfig();
        if (config.isSet(DROP_FALLING_SPEED)) {
            return config.getDouble(DROP_FALLING_SPEED, .3);
        }
        return config.getDouble(DROP_FALLING_SPEED_LEGACY, .3);
    }

    public static int getDropHeight() {
        return getConfig().getInt(DROP_HEIGHT, 20);
    }

    public static boolean shouldShowLandingParticleEffects() {
        return getConfig().getBoolean(DROP_LANDING_PARTICLE_EFFECTS, true);
    }

    public static boolean shouldShowContinuousParticleEffects() {
        return getConfig().getBoolean(DROP_CONTINUOUS_PARTICLE_EFFECTS, true);
    }

    public static boolean shouldShowFlareParticleEffects() {
        return getConfig().getBoolean(DROP_FLARE_PARTICLE_EFFECTS, true);
    }

    public static boolean isSmokeEnabled() {
        return getConfig().getBoolean(DROP_SMOKE_ENABLED, true);
    }

    public static int getSmokeHeight() {
        return getConfig().getInt(DROP_SMOKE_HEIGHT, 20);
    }

    // Economy getters
    public static boolean isEconomyEnabled() {
        return getConfig().getBoolean(ECONOMY_ENABLED, true);
    }

    // Helper method to get config
    private static FileConfiguration getConfig() {
        Config config = Airdrop.getConfiguration();
        if (config == null) {
            return FALLBACK_CONFIG;
        }
        FileConfiguration fileConfig = config.getConfig();
        return fileConfig == null ? FALLBACK_CONFIG : fileConfig;
    }
}
