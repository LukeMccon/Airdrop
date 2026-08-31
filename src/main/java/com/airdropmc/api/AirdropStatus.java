package com.airdropmc.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Immutable operational status snapshot.
 *
 * <p>This is deliberately a final class rather than a record so later API
 * minors can add diagnostic fields without changing a record's structural
 * contract.</p>
 */
public final class AirdropStatus {
	private static final int MAX_PROVIDER_CODE_POINTS = 80;
	private static final int MAX_DIAGNOSTIC_CODE_POINTS = 160;
	private static final Set<String> DIAGNOSTIC_CATEGORIES = Set.of(
			"STARTUP", "CONFIGURATION", "PACKAGE_REGISTRY");

	private final ReadinessState readiness;
	private final EconomyState economy;
	private final String economyProviderName;
	private final long packageRevision;
	private final int packageCount;
	private final int pendingCount;
	private final int fallingCount;
	private final int landedCount;
	private final OptionalInt maxFalling;
	private final OptionalInt maxLanded;
	private final String lastDiagnosticCategory;
	private final String lastDiagnostic;
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
		this(readiness, economy, economyProviderName, 0L, packageCount,
				fallingCount, landedCount, degradedReasons);
	}

	/**
	 * Creates a detached status snapshot including package publication identity.
	 *
	 * @param readiness service lifecycle state
	 * @param economy economy integration state
	 * @param economyProviderName provider name when economy is active
	 * @param packageRevision latest successful package publication revision
	 * @param packageCount published package count
	 * @param fallingCount active falling-drop count
	 * @param landedCount active landed-drop count
	 * @param degradedReasons stable degraded-state identifiers
	 */
	public AirdropStatus(
			ReadinessState readiness,
			EconomyState economy,
			String economyProviderName,
			long packageRevision,
			int packageCount,
			int fallingCount,
			int landedCount,
			List<String> degradedReasons) {
		this.readiness = Objects.requireNonNull(readiness, "readiness");
		this.economy = Objects.requireNonNull(economy, "economy");
		this.economyProviderName = normalizeProvider(economy, economyProviderName);
		if (packageRevision < 0L) {
			throw new IllegalArgumentException("packageRevision must be non-negative");
		}
		this.packageRevision = packageRevision;
		this.packageCount = requireNonNegative(packageCount, "packageCount");
		this.pendingCount = 0;
		this.fallingCount = requireNonNegative(fallingCount, "fallingCount");
		this.landedCount = requireNonNegative(landedCount, "landedCount");
		this.maxFalling = OptionalInt.empty();
		this.maxLanded = OptionalInt.empty();
		this.lastDiagnosticCategory = null;
		this.lastDiagnostic = null;
		this.degradedReasons = List.copyOf(Objects.requireNonNull(
				degradedReasons, "degradedReasons"));
		if (this.degradedReasons.stream().anyMatch(reason -> reason == null || reason.isBlank())) {
			throw new IllegalArgumentException("degradedReasons must contain non-blank values");
		}
	}

	/**
	 * Creates a complete operational snapshot. Limits remain absent until a
	 * configuration publication succeeds. Diagnostic category names are the
	 * stable values {@code STARTUP}, {@code CONFIGURATION}, and
	 * {@code PACKAGE_REGISTRY}.
	 *
	 * @param readiness service lifecycle state
	 * @param economy economy integration state
	 * @param economyProviderName bounded provider label when economy is active
	 * @param packageRevision latest successful package publication revision
	 * @param packageCount published package count
	 * @param pendingCount incomplete request count
	 * @param fallingCount active falling-drop count
	 * @param landedCount active landed-drop count
	 * @param maxFalling configured falling limit after publication
	 * @param maxLanded configured landed limit after publication
	 * @param lastDiagnosticCategory stable category of the last diagnostic
	 * @param lastDiagnostic bounded sanitized last diagnostic message
	 * @param degradedReasons stable degraded-state identifiers
	 */
	public AirdropStatus(
			ReadinessState readiness,
			EconomyState economy,
			String economyProviderName,
			long packageRevision,
			int packageCount,
			int pendingCount,
			int fallingCount,
			int landedCount,
			OptionalInt maxFalling,
			OptionalInt maxLanded,
			Optional<String> lastDiagnosticCategory,
			Optional<String> lastDiagnostic,
			List<String> degradedReasons) {
		this.readiness = Objects.requireNonNull(readiness, "readiness");
		this.economy = Objects.requireNonNull(economy, "economy");
		this.economyProviderName = normalizeProvider(economy, economyProviderName);
		if (packageRevision < 0L) {
			throw new IllegalArgumentException("packageRevision must be non-negative");
		}
		this.packageRevision = packageRevision;
		this.packageCount = requireNonNegative(packageCount, "packageCount");
		this.pendingCount = requireNonNegative(pendingCount, "pendingCount");
		this.fallingCount = requireNonNegative(fallingCount, "fallingCount");
		this.landedCount = requireNonNegative(landedCount, "landedCount");
		this.maxFalling = requirePositive(
				Objects.requireNonNull(maxFalling, "maxFalling"), "maxFalling");
		this.maxLanded = requirePositive(
				Objects.requireNonNull(maxLanded, "maxLanded"), "maxLanded");
		Optional<String> category = Objects.requireNonNull(
				lastDiagnosticCategory, "lastDiagnosticCategory");
		Optional<String> diagnostic = Objects.requireNonNull(lastDiagnostic, "lastDiagnostic");
		if (category.isPresent() != diagnostic.isPresent()) {
			throw new IllegalArgumentException(
					"lastDiagnosticCategory and lastDiagnostic must be present together");
		}
		this.lastDiagnosticCategory = category
				.map(value -> requireCategory(value, "lastDiagnosticCategory"))
				.orElse(null);
		this.lastDiagnostic = diagnostic
				.map(value -> requireSingleLine(
						value, "lastDiagnostic", MAX_DIAGNOSTIC_CODE_POINTS))
				.orElse(null);
		this.degradedReasons = validatedReasons(degradedReasons);
	}

	/**
	 * Returns the latest successful package publication revision.
	 *
	 * @return monotonic in-memory package registry revision
	 */
	public long packageRevision() {
		return packageRevision;
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
	 * Returns the number of requests which have not reached a terminal outcome.
	 *
	 * @return incomplete request count
	 */
	public int pendingCount() {
		return pendingCount;
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

	/** @return configured falling limit, absent before configuration publication */
	public OptionalInt maxFalling() {
		return maxFalling;
	}

	/** @return configured landed limit, absent before configuration publication */
	public OptionalInt maxLanded() {
		return maxLanded;
	}

	/**
	 * Returns the stable source category of the last operator diagnostic.
	 *
	 * @return {@code STARTUP}, {@code CONFIGURATION}, or {@code PACKAGE_REGISTRY}
	 */
	public Optional<String> lastDiagnosticCategory() {
		return Optional.ofNullable(lastDiagnosticCategory);
	}

	/** @return bounded sanitized last operator diagnostic */
	public Optional<String> lastDiagnostic() {
		return Optional.ofNullable(lastDiagnostic);
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
		return requireSingleLine(required, "economyProviderName", MAX_PROVIDER_CODE_POINTS);
	}

	private static int requireNonNegative(int value, String name) {
		if (value < 0) {
			throw new IllegalArgumentException(name + " must be non-negative");
		}
		return value;
	}

	private static OptionalInt requirePositive(OptionalInt value, String name) {
		if (value.isPresent() && value.getAsInt() < 1) {
			throw new IllegalArgumentException(name + " must be positive when published");
		}
		return value;
	}

	private static String requireCategory(String value, String name) {
		String required = requireSingleLine(value, name, 32);
		if (!DIAGNOSTIC_CATEGORIES.contains(required)) {
			throw new IllegalArgumentException(name + " is not a stable diagnostic category");
		}
		return required;
	}

	private static String requireSingleLine(String value, String name, int maximumCodePoints) {
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		if (value.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
				|| Character.getType(codePoint) == Character.LINE_SEPARATOR
				|| Character.getType(codePoint) == Character.PARAGRAPH_SEPARATOR)) {
			throw new IllegalArgumentException(name + " must be single-line and control-free");
		}
		if (value.codePointCount(0, value.length()) > maximumCodePoints) {
			throw new IllegalArgumentException(name + " exceeds " + maximumCodePoints + " code points");
		}
		return value;
	}

	private static List<String> validatedReasons(List<String> reasons) {
		List<String> copy = List.copyOf(Objects.requireNonNull(reasons, "degradedReasons"));
		if (copy.stream().anyMatch(reason -> reason == null || reason.isBlank())) {
			throw new IllegalArgumentException("degradedReasons must contain non-blank values");
		}
		return copy;
	}
}
