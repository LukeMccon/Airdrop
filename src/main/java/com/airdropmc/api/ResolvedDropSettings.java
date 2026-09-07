package com.airdropmc.api;

import java.time.Duration;
import java.util.Objects;

/**
 * Complete primitive settings captured once for one drop request.
 *
 * <p>This value is detached from live configuration. A later reload cannot
 * alter a drop that already owns this snapshot.</p>
 *
 * @param chickenCount number of parachute chickens
 * @param fallingSpeed downward blocks-per-tick velocity magnitude
 * @param dropHeight spawn height above the landing target
 * @param landingEffects whether one-shot landing particles are enabled
 * @param continuousEffects whether continuous landed glow is enabled
 * @param flareEffects whether the falling flare is enabled
 * @param smokeEnabled whether landed smoke is enabled
 * @param smokeHeight landed smoke column height
 * @param requestCooldown player request cooldown
 * @param maxFalling maximum simultaneous falling drops
 * @param maxLanded maximum simultaneous landed drops
 * @param landedLifetime maximum tracked landed lifetime
 */
public record ResolvedDropSettings(
		int chickenCount,
		double fallingSpeed,
		int dropHeight,
		boolean landingEffects,
		boolean continuousEffects,
		boolean flareEffects,
		boolean smokeEnabled,
		int smokeHeight,
		Duration requestCooldown,
		int maxFalling,
		int maxLanded,
		Duration landedLifetime) {

	private static final int MIN_CHICKEN_COUNT = 1;
	private static final int MAX_CHICKEN_COUNT = 64;
	private static final double MIN_FALLING_SPEED = 0.01;
	private static final double MAX_FALLING_SPEED = 4.0;
	private static final int MIN_DROP_HEIGHT = 1;
	private static final int MAX_DROP_HEIGHT = 320;
	private static final int MIN_SMOKE_HEIGHT = 0;
	private static final int MAX_SMOKE_HEIGHT = 128;

	/** Validates a complete resolved settings snapshot. */
	public ResolvedDropSettings {
		requestCooldown = Objects.requireNonNull(requestCooldown, "requestCooldown");
		landedLifetime = Objects.requireNonNull(landedLifetime, "landedLifetime");
		requireRange(chickenCount, MIN_CHICKEN_COUNT, MAX_CHICKEN_COUNT, "chickenCount");
		if (!Double.isFinite(fallingSpeed)
				|| fallingSpeed < MIN_FALLING_SPEED || fallingSpeed > MAX_FALLING_SPEED) {
			throw new IllegalArgumentException("fallingSpeed must be finite and between 0.01 and 4.0");
		}
		requireRange(dropHeight, MIN_DROP_HEIGHT, MAX_DROP_HEIGHT, "dropHeight");
		requireRange(smokeHeight, MIN_SMOKE_HEIGHT, MAX_SMOKE_HEIGHT, "smokeHeight");
		if (requestCooldown.isZero() || requestCooldown.isNegative()) {
			throw new IllegalArgumentException("requestCooldown must be positive");
		}
		if (maxFalling < 1) {
			throw new IllegalArgumentException("maxFalling must be positive");
		}
		if (maxLanded < 1) {
			throw new IllegalArgumentException("maxLanded must be positive");
		}
		if (landedLifetime.isZero() || landedLifetime.isNegative()) {
			throw new IllegalArgumentException("landedLifetime must be positive");
		}
	}

	private static void requireRange(int value, int minimum, int maximum, String name) {
		if (value < minimum || value > maximum) {
			throw new IllegalArgumentException(
					name + " must be between " + minimum + " and " + maximum);
		}
	}
}
