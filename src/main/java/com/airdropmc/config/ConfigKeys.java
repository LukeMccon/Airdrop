package com.airdropmc.config;

import com.airdropmc.Airdrop;
import com.airdropmc.Config;
import com.airdropmc.helpers.AirdropLogger;
import com.airdropmc.limits.DropLimitSettings;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.time.Duration;

/**
 * Stores all configuration keys and provides methods to access config values
 */
public final class ConfigKeys {
    private static final FileConfiguration FALLBACK_CONFIG = new YamlConfiguration();
    private static final int DEFAULT_PARACHUTE_CHICKEN_COUNT = 5;
    private static final int MIN_PARACHUTE_CHICKEN_COUNT = 1;
    private static final int MAX_PARACHUTE_CHICKEN_COUNT = 64;
    private static final double DEFAULT_DROP_FALLING_SPEED = 0.3;
    private static final double MIN_DROP_FALLING_SPEED = 0.01;
    private static final double MAX_DROP_FALLING_SPEED = 4.0;
    private static final int DEFAULT_DROP_HEIGHT = 100;
    private static final int MIN_DROP_HEIGHT = 1;
    private static final int MAX_DROP_HEIGHT = 320;
    private static final int DEFAULT_SMOKE_HEIGHT = 20;
    private static final int MIN_SMOKE_HEIGHT = 0;
    private static final int MAX_SMOKE_HEIGHT = 128;
	private static final int DEFAULT_REQUEST_COOLDOWN_SECONDS = 30;
	private static final int DEFAULT_MAX_FALLING = 3;
	private static final int DEFAULT_MAX_LANDED = 10;
	private static final int DEFAULT_LANDED_LIFETIME_SECONDS = 600;

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
	public static final String DROP_REQUEST_COOLDOWN_SECONDS = "drop.limits.request-cooldown-seconds";
	public static final String DROP_MAX_FALLING = "drop.limits.max-falling";
	public static final String DROP_MAX_LANDED = "drop.limits.max-landed";
	public static final String DROP_LANDED_LIFETIME_SECONDS = "drop.limits.landed-lifetime-seconds";

	// General paths
	public static final String LANGUAGE = "language";

    // Economy paths
    public static final String ECONOMY_ENABLED = "economy.enabled";
    public static final String LOGGING_DEBUG = "logging.debug";

    // Drop settings getters
    public static int getParachuteChickenCount() {
        return sanitizeParachuteChickenCount(
                getConfig().getInt(DROP_PARACHUTE_CHICKEN_COUNT, DEFAULT_PARACHUTE_CHICKEN_COUNT));
    }

    public static double getDropFallingSpeed() {
        FileConfiguration config = getConfig();
        double configuredSpeed;
        if (config.isSet(DROP_FALLING_SPEED)) {
            configuredSpeed = config.getDouble(DROP_FALLING_SPEED, DEFAULT_DROP_FALLING_SPEED);
        } else {
            configuredSpeed = config.getDouble(DROP_FALLING_SPEED_LEGACY, DEFAULT_DROP_FALLING_SPEED);
        }
        return sanitizeDropFallingSpeed(configuredSpeed);
    }

    public static int getDropHeight() {
        return sanitizeDropHeight(getConfig().getInt(DROP_HEIGHT, DEFAULT_DROP_HEIGHT));
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
        return sanitizeSmokeHeight(getConfig().getInt(DROP_SMOKE_HEIGHT, DEFAULT_SMOKE_HEIGHT));
    }

	public static DropLimitSettings getDropLimitSettings() {
		FileConfiguration config = getConfig();
		int cooldown = boundedInteger(config, DROP_REQUEST_COOLDOWN_SECONDS,
				1, 86_400, DEFAULT_REQUEST_COOLDOWN_SECONDS);
		int maxFalling = boundedInteger(config, DROP_MAX_FALLING, 1, 64, DEFAULT_MAX_FALLING);
		int maxLanded = boundedInteger(config, DROP_MAX_LANDED, 1, 256, DEFAULT_MAX_LANDED);
		int lifetime = boundedInteger(config, DROP_LANDED_LIFETIME_SECONDS,
				30, 86_400, DEFAULT_LANDED_LIFETIME_SECONDS);
		return new DropLimitSettings(
				Duration.ofSeconds(cooldown), maxFalling, maxLanded, Duration.ofSeconds(lifetime));
	}

	// General getters
	public static String getLanguage() {
		return getLanguage(getConfig());
	}

	static String getLanguage(FileConfiguration config) {
		String language = config.getString(LANGUAGE, "en");
		return language == null ? "en" : language;
	}

    // Economy getters
    public static boolean isEconomyEnabled() {
        return isEconomyEnabled(getConfig());
    }

	static boolean isEconomyEnabled(FileConfiguration config) {
		return config.getBoolean(ECONOMY_ENABLED, true);
	}

    public static boolean isDebugLoggingEnabled() {
        return getConfig().getBoolean(LOGGING_DEBUG, false);
    }

    static int sanitizeParachuteChickenCount(int chickenCount) {
        if (chickenCount < MIN_PARACHUTE_CHICKEN_COUNT || chickenCount > MAX_PARACHUTE_CHICKEN_COUNT) {
            return DEFAULT_PARACHUTE_CHICKEN_COUNT;
        }
        return chickenCount;
    }

    static double sanitizeDropFallingSpeed(double fallingSpeed) {
        if (!Double.isFinite(fallingSpeed)) {
            return DEFAULT_DROP_FALLING_SPEED;
        }
        if (fallingSpeed < MIN_DROP_FALLING_SPEED || fallingSpeed > MAX_DROP_FALLING_SPEED) {
            return DEFAULT_DROP_FALLING_SPEED;
        }
        return fallingSpeed;
    }

    static int sanitizeDropHeight(int dropHeight) {
        if (dropHeight < MIN_DROP_HEIGHT || dropHeight > MAX_DROP_HEIGHT) {
            return DEFAULT_DROP_HEIGHT;
        }
        return dropHeight;
    }

    static int sanitizeSmokeHeight(int smokeHeight) {
        if (smokeHeight < MIN_SMOKE_HEIGHT || smokeHeight > MAX_SMOKE_HEIGHT) {
            return DEFAULT_SMOKE_HEIGHT;
        }
        return smokeHeight;
	}

	private static int boundedInteger(FileConfiguration config, String key,
			int minimum, int maximum, int fallback) {
		Object configured = config.get(key);
		if (configured == null) {
			return fallback;
		}
		if (!(configured instanceof Number number)) {
			return invalidInteger(key, configured, fallback);
		}
		double numericValue = number.doubleValue();
		if (!Double.isFinite(numericValue) || numericValue != Math.rint(numericValue)
				|| numericValue < Integer.MIN_VALUE || numericValue > Integer.MAX_VALUE) {
			return invalidInteger(key, configured, fallback);
		}
		return bounded(key, (int) numericValue, minimum, maximum, fallback);
	}

	private static int invalidInteger(String key, Object value, int fallback) {
		AirdropLogger.warning("Invalid " + key + " value " + value + "; using " + fallback);
		return fallback;
	}

	private static int bounded(String key, int value, int minimum, int maximum, int fallback) {
		if (value >= minimum && value <= maximum) {
			return value;
		}
		AirdropLogger.warning("Invalid " + key + " value " + value + "; using " + fallback);
		return fallback;
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
