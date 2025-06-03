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

    // Drop settings paths
    public static final String DROP_LANDING_PARTICLE_EFFECTS = "drop.particles.landing-effects";
    public static final String DROP_CONTINUOUS_PARTICLE_EFFECTS = "drop.particles.continuous-effects";
    public static final String DROP_FLARE_PARTICLE_EFFECTS = "drop.particles.flare-effects";
    public static final String DROP_SMOKE_ENABLED = "drop.particles.smoke.enabled";
    public static final String DROP_SMOKE_HEIGHT = "drop.particles.smoke.height";
    public static final String DROP_PARACHUTE_CHICKEN_COUNT = "drop.parachute.chicken-count";
    public static final String DROP_FALLING_SPEED = "drop.parachute.falling-speed";
    public static final String DROP_HEIGHT = "drop.height";

    // Economy paths
    public static final String ECONOMY_ENABLED = "economy.enabled";

    // Drop settings getters
    public static int getParachuteChickenCount() {
        return getConfig().getInt(DROP_PARACHUTE_CHICKEN_COUNT, 5);
    }

    public static double getDropFallingSpeed() {
        return getConfig().getDouble(DROP_FALLING_SPEED, .3);
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
        return Airdrop.getConfiguration().getConfig();
    }
}
