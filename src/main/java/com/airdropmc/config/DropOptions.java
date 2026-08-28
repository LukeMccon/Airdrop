package com.airdropmc.config;

/**
 * Configuration options for individual drop operations.
 * When a property is null, the default value from config.yml will be used.
 */
public class DropOptions {
    private Integer chickenCount;
    private Double fallingSpeed;
    private Integer dropHeight;
    private Boolean showLandingEffects;
    private Boolean showContinuousEffects;
    private Boolean showFlareEffects;
    private Boolean smokeEnabled;
    private Integer smokeHeight;

    public static DropOptions createDefault() {
        return new DropOptions();
    }

    // Fluent builder methods
    public DropOptions withChickenCount(int count) {
        this.chickenCount = count;
        return this;
    }

    public DropOptions withFallingSpeed(double speed) {
        this.fallingSpeed = speed;
        return this;
    }

    public DropOptions withDropHeight(int height) {
        this.dropHeight = height;
        return this;
    }

    public DropOptions withLandingEffects(boolean show) {
        this.showLandingEffects = show;
        return this;
    }

    public DropOptions withContinuousEffects(boolean show) {
        this.showContinuousEffects = show;
        return this;
    }

    public DropOptions withFlareEffects(boolean show) {
        this.showFlareEffects = show;
        return this;
    }

    public DropOptions withSmokeEnabled(boolean enabled) {
        this.smokeEnabled = enabled;
        return this;
    }

    public DropOptions withSmokeHeight(int height) {
        this.smokeHeight = height;
        return this;
    }

    // Getters that fall back to config values if not set
    public int getChickenCount() {
        if (chickenCount == null) {
            return ConfigKeys.getParachuteChickenCount();
        }
        return ConfigKeys.sanitizeParachuteChickenCount(chickenCount);
    }

    public double getFallingSpeed() {
        if (fallingSpeed == null) {
            return ConfigKeys.getDropFallingSpeed();
        }
        return ConfigKeys.sanitizeDropFallingSpeed(fallingSpeed);
    }

    public int getDropHeight() {
        if (dropHeight == null) {
            return ConfigKeys.getDropHeight();
        }
        return ConfigKeys.sanitizeDropHeight(dropHeight);
    }

    public boolean shouldShowLandingEffects() {
        return showLandingEffects != null ? showLandingEffects : ConfigKeys.shouldShowLandingParticleEffects();
    }

    public boolean shouldShowContinuousEffects() {
        return showContinuousEffects != null ? showContinuousEffects : ConfigKeys.shouldShowContinuousParticleEffects();
    }

    public boolean shouldShowFlareEffects() {
        return showFlareEffects != null ? showFlareEffects : ConfigKeys.shouldShowFlareParticleEffects();
    }

    public boolean isSmokeEnabled() {
        return smokeEnabled != null ? smokeEnabled : ConfigKeys.isSmokeEnabled();
    }

    public int getSmokeHeight() {
        if (smokeHeight == null) {
            return ConfigKeys.getSmokeHeight();
        }
        return ConfigKeys.sanitizeSmokeHeight(smokeHeight);
    }
}
