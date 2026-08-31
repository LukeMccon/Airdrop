package com.airdropmc.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable operational status snapshot.
 *
 * <p>This is deliberately a final class rather than a record so later API
 * minors can add diagnostic fields without changing a record's structural
 * contract.</p>
 */
public final class AirdropStatus {

	private final ReadinessState readiness;
	private final EconomyState economy;
	private final String economyProviderName;
	private final int packageCount;
	private final int fallingCount;
	private final int landedCount;
	private final List<String> degradedReasons;

	/**
	 * Creates a detached status snapshot.
	 *
	 * @param readiness service lifecycle state
	 * @param economy economy integration state
	 * @param economyProviderName provider name when economy is active
	 * @param packageCount published package count
	 * @param fallingCount active falling-drop count
	 * @param landedCount active landed-drop count
	 * @param degradedReasons stable degraded-state identifiers
	 */
	public AirdropStatus(
			ReadinessState readiness,
			EconomyState economy,
			String economyProviderName,
			int packageCount,
			int fallingCount,
			int landedCount,
			List<String> degradedReasons) {
		this.readiness = Objects.requireNonNull(readiness, "readiness");
		this.economy = Objects.requireNonNull(economy, "economy");
		this.economyProviderName = normalizeProvider(economy, economyProviderName);
		this.packageCount = requireNonNegative(packageCount, "packageCount");
		this.fallingCount = requireNonNegative(fallingCount, "fallingCount");
		this.landedCount = requireNonNegative(landedCount, "landedCount");
		this.degradedReasons = List.copyOf(Objects.requireNonNull(
				degradedReasons, "degradedReasons"));
		if (this.degradedReasons.stream().anyMatch(reason -> reason == null || reason.isBlank())) {
			throw new IllegalArgumentException("degradedReasons must contain non-blank values");
		}
	}

	/**
	 * Returns the service lifecycle state.
	 *
	 * @return service lifecycle state
	 */
	public ReadinessState readiness() {
		return readiness;
	}

	/**
	 * Returns the economy integration state.
	 *
	 * @return economy integration state
	 */
	public EconomyState economy() {
		return economy;
	}

	/**
	 * Returns the active provider display name.
	 *
	 * @return active provider display name, when available
	 */
	public Optional<String> economyProviderName() {
		return Optional.ofNullable(economyProviderName);
	}

	/**
	 * Returns the number of published packages.
	 *
	 * @return number of published packages
	 */
	public int packageCount() {
		return packageCount;
	}

	/**
	 * Returns the number of actively falling drops.
	 *
	 * @return number of actively falling drops
	 */
	public int fallingCount() {
		return fallingCount;
	}

	/**
	 * Returns the number of actively tracked landed drops.
	 *
	 * @return number of actively tracked landed drops
	 */
	public int landedCount() {
		return landedCount;
	}

	/**
	 * Returns stable degraded-state identifiers.
	 *
	 * @return immutable stable degraded-state identifiers
	 */
	public List<String> degradedReasons() {
		return degradedReasons;
	}

	private static String normalizeProvider(EconomyState economy, String providerName) {
		if (economy != EconomyState.ACTIVE) {
			if (providerName != null && !providerName.isBlank()) {
				throw new IllegalArgumentException(
						"economyProviderName is only valid for active economy");
			}
			return null;
		}
		String required = Objects.requireNonNull(providerName,
				"economyProviderName is required for active economy");
		if (required.isBlank()) {
			throw new IllegalArgumentException(
					"economyProviderName must not be blank for active economy");
		}
		return required;
	}

	private static int requireNonNegative(int value, String name) {
		if (value < 0) {
			throw new IllegalArgumentException(name + " must be non-negative");
		}
		return value;
	}
}
