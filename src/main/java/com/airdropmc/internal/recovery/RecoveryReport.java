package com.airdropmc.internal.recovery;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded immutable summary of narrow paid-crate recovery outcomes. */
@ApiStatus.Internal
public record RecoveryReport(
		long recoveredCrates,
		long purgedCrates,
		List<String> diagnostics) {

	private static final int MAX_DIAGNOSTICS = 16;

	public RecoveryReport {
		if (recoveredCrates < 0L || purgedCrates < 0L) {
			throw new IllegalArgumentException("Recovery counts must be non-negative");
		}
		diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
		if (diagnostics.size() > MAX_DIAGNOSTICS
				|| diagnostics.stream().anyMatch(value -> value == null || value.isBlank())) {
			throw new IllegalArgumentException("Recovery diagnostics must be bounded and non-blank");
		}
	}

	/** @return an empty recovery report */
	public static RecoveryReport empty() {
		return new RecoveryReport(0L, 0L, List.of());
	}

	/** @return whether any narrow recovery candidate was purged fail-closed */
	public boolean degraded() {
		return purgedCrates > 0L;
	}

	/** @return a replacement report including one successful activation */
	public RecoveryReport withRecoveredCrate() {
		return new RecoveryReport(increment(recoveredCrates), purgedCrates, diagnostics);
	}

	/** @return a replacement report including one bounded purge diagnostic */
	public RecoveryReport withPurgedCrate(String diagnostic) {
		String required = Objects.requireNonNull(diagnostic, "diagnostic");
		if (required.isBlank()) {
			throw new IllegalArgumentException("diagnostic must not be blank");
		}
		List<String> replacement = new ArrayList<>(diagnostics);
		if (replacement.size() == MAX_DIAGNOSTICS) {
			replacement.removeFirst();
		}
		replacement.add(required);
		return new RecoveryReport(
				recoveredCrates, increment(purgedCrates), replacement);
	}

	private static long increment(long value) {
		return value == Long.MAX_VALUE ? value : value + 1L;
	}
}
