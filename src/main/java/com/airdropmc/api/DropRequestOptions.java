package com.airdropmc.api;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Immutable per-request overrides. Empty values inherit the server's current
 * configuration when the request is resolved.
 */
public final class DropRequestOptions {

	private static final int MIN_CHICKEN_COUNT = 1;
	private static final int MAX_CHICKEN_COUNT = 64;
	private static final double MIN_FALLING_SPEED = 0.01;
	private static final double MAX_FALLING_SPEED = 4.0;
	private static final int MIN_DROP_HEIGHT = 1;
	private static final int MAX_DROP_HEIGHT = 320;
	private static final int MIN_SMOKE_HEIGHT = 0;
	private static final int MAX_SMOKE_HEIGHT = 128;
	private static final DropRequestOptions DEFAULTS = new DropRequestOptions(
			null, null, null, null, null, null, null, null);

	private final Integer chickenCount;
	private final Double fallingSpeed;
	private final Integer dropHeight;
	private final Boolean landingEffects;
	private final Boolean continuousEffects;
	private final Boolean flareEffects;
	private final Boolean smokeEnabled;
	private final Integer smokeHeight;

	private DropRequestOptions(
			Integer chickenCount,
			Double fallingSpeed,
			Integer dropHeight,
			Boolean landingEffects,
			Boolean continuousEffects,
			Boolean flareEffects,
			Boolean smokeEnabled,
			Integer smokeHeight) {
		this.chickenCount = chickenCount;
		this.fallingSpeed = fallingSpeed;
		this.dropHeight = dropHeight;
		this.landingEffects = landingEffects;
		this.continuousEffects = continuousEffects;
		this.flareEffects = flareEffects;
		this.smokeEnabled = smokeEnabled;
		this.smokeHeight = smokeHeight;
	}

	/**
	 * Returns a request-options value with no overrides.
	 *
	 * @return options with every override empty
	 */
	public static DropRequestOptions defaults() {
		return DEFAULTS;
	}

	/**
	 * Returns a copy with a parachute chicken override.
	 *
	 * @param value parachute chicken count override
	 * @return a new options value containing the override
	 */
	public DropRequestOptions withChickenCount(int value) {
		requireRange(value, MIN_CHICKEN_COUNT, MAX_CHICKEN_COUNT, "chickenCount");
		return copy(value, fallingSpeed, dropHeight, landingEffects, continuousEffects,
				flareEffects, smokeEnabled, smokeHeight);
	}

	/**
	 * Returns a copy with a falling-speed override.
	 *
	 * @param value downward falling-speed magnitude override
	 * @return a new options value containing the override
	 */
	public DropRequestOptions withFallingSpeed(double value) {
		if (!Double.isFinite(value) || value < MIN_FALLING_SPEED || value > MAX_FALLING_SPEED) {
			throw new IllegalArgumentException("fallingSpeed must be finite and between 0.01 and 4.0");
		}
		return copy(chickenCount, value, dropHeight, landingEffects, continuousEffects,
				flareEffects, smokeEnabled, smokeHeight);
	}

	/**
	 * Returns a copy with a spawn-height override.
	 *
	 * @param value spawn height override
	 * @return a new options value containing the override
	 */
	public DropRequestOptions withDropHeight(int value) {
		requireRange(value, MIN_DROP_HEIGHT, MAX_DROP_HEIGHT, "dropHeight");
		return copy(chickenCount, fallingSpeed, value, landingEffects, continuousEffects,
				flareEffects, smokeEnabled, smokeHeight);
	}

	/**
	 * Returns a copy with a landing-particle override.
	 *
	 * @param value whether one-shot landing particles are enabled
	 * @return a new options value containing the override
	 */
	public DropRequestOptions withLandingEffects(boolean value) {
		return copy(chickenCount, fallingSpeed, dropHeight, value, continuousEffects,
				flareEffects, smokeEnabled, smokeHeight);
	}

	/**
	 * Returns a copy with a continuous-glow override.
	 *
	 * @param value whether continuous landed glow is enabled
	 * @return a new options value containing the override
	 */
	public DropRequestOptions withContinuousEffects(boolean value) {
		return copy(chickenCount, fallingSpeed, dropHeight, landingEffects, value,
				flareEffects, smokeEnabled, smokeHeight);
	}

	/**
	 * Returns a copy with a falling-flare override.
	 *
	 * @param value whether the falling flare is enabled
	 * @return a new options value containing the override
	 */
	public DropRequestOptions withFlareEffects(boolean value) {
		return copy(chickenCount, fallingSpeed, dropHeight, landingEffects, continuousEffects,
				value, smokeEnabled, smokeHeight);
	}

	/**
	 * Returns a copy with a landed-smoke override.
	 *
	 * @param value whether landed smoke is enabled
	 * @return a new options value containing the override
	 */
	public DropRequestOptions withSmokeEnabled(boolean value) {
		return copy(chickenCount, fallingSpeed, dropHeight, landingEffects, continuousEffects,
				flareEffects, value, smokeHeight);
	}

	/**
	 * Returns a copy with a smoke-column height override.
	 *
	 * @param value landed smoke-column height override
	 * @return a new options value containing the override
	 */
	public DropRequestOptions withSmokeHeight(int value) {
		requireRange(value, MIN_SMOKE_HEIGHT, MAX_SMOKE_HEIGHT, "smokeHeight");
		return copy(chickenCount, fallingSpeed, dropHeight, landingEffects, continuousEffects,
				flareEffects, smokeEnabled, value);
	}

	/**
	 * Returns the parachute chicken override.
	 *
	 * @return optional parachute chicken count override
	 */
	public OptionalInt chickenCount() {
		return chickenCount == null ? OptionalInt.empty() : OptionalInt.of(chickenCount);
	}

	/**
	 * Returns the falling-speed override.
	 *
	 * @return optional falling-speed override
	 */
	public OptionalDouble fallingSpeed() {
		return fallingSpeed == null ? OptionalDouble.empty() : OptionalDouble.of(fallingSpeed);
	}

	/**
	 * Returns the spawn-height override.
	 *
	 * @return optional spawn-height override
	 */
	public OptionalInt dropHeight() {
		return dropHeight == null ? OptionalInt.empty() : OptionalInt.of(dropHeight);
	}

	/**
	 * Returns the landing-particle override.
	 *
	 * @return optional landing-particle override
	 */
	public Optional<Boolean> landingEffects() {
		return Optional.ofNullable(landingEffects);
	}

	/**
	 * Returns the continuous-glow override.
	 *
	 * @return optional continuous-glow override
	 */
	public Optional<Boolean> continuousEffects() {
		return Optional.ofNullable(continuousEffects);
	}

	/**
	 * Returns the falling-flare override.
	 *
	 * @return optional falling-flare override
	 */
	public Optional<Boolean> flareEffects() {
		return Optional.ofNullable(flareEffects);
	}

	/**
	 * Returns the landed-smoke override.
	 *
	 * @return optional landed-smoke override
	 */
	public Optional<Boolean> smokeEnabled() {
		return Optional.ofNullable(smokeEnabled);
	}

	/**
	 * Returns the smoke-column height override.
	 *
	 * @return optional smoke-column height override
	 */
	public OptionalInt smokeHeight() {
		return smokeHeight == null ? OptionalInt.empty() : OptionalInt.of(smokeHeight);
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof DropRequestOptions that)) {
			return false;
		}
		return Objects.equals(chickenCount, that.chickenCount)
				&& Objects.equals(fallingSpeed, that.fallingSpeed)
				&& Objects.equals(dropHeight, that.dropHeight)
				&& Objects.equals(landingEffects, that.landingEffects)
				&& Objects.equals(continuousEffects, that.continuousEffects)
				&& Objects.equals(flareEffects, that.flareEffects)
				&& Objects.equals(smokeEnabled, that.smokeEnabled)
				&& Objects.equals(smokeHeight, that.smokeHeight);
	}

	@Override
	public int hashCode() {
		return Objects.hash(chickenCount, fallingSpeed, dropHeight, landingEffects,
				continuousEffects, flareEffects, smokeEnabled, smokeHeight);
	}

	private DropRequestOptions copy(
			Integer replacementChickenCount,
			Double replacementFallingSpeed,
			Integer replacementDropHeight,
			Boolean replacementLandingEffects,
			Boolean replacementContinuousEffects,
			Boolean replacementFlareEffects,
			Boolean replacementSmokeEnabled,
			Integer replacementSmokeHeight) {
		return new DropRequestOptions(
				replacementChickenCount,
				replacementFallingSpeed,
				replacementDropHeight,
				replacementLandingEffects,
				replacementContinuousEffects,
				replacementFlareEffects,
				replacementSmokeEnabled,
				replacementSmokeHeight);
	}

	private static void requireRange(int value, int minimum, int maximum, String name) {
		if (value < minimum || value > maximum) {
			throw new IllegalArgumentException(
					name + " must be between " + minimum + " and " + maximum);
		}
	}
}
