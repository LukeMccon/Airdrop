package com.airdropmc.api;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Stable rejection details with optional cooldown retry guidance.
 *
 * @param reason machine-readable rejection reason
 * @param diagnostic non-blank diagnostic intended for logs and debugging
 * @param retryAfter positive retry delay for cooldown rejections only
 */
public record DropRejection(
		DropRejectionReason reason,
		String diagnostic,
		Optional<Duration> retryAfter) {

	public DropRejection {
		reason = Objects.requireNonNull(reason, "reason");
		diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
		retryAfter = Objects.requireNonNull(retryAfter, "retryAfter");
		if (diagnostic.isBlank()) {
			throw new IllegalArgumentException("diagnostic must not be blank");
		}
		if (retryAfter.isPresent()) {
			Duration retry = retryAfter.orElseThrow();
			if (reason != DropRejectionReason.COOLDOWN) {
				throw new IllegalArgumentException("retryAfter is only valid for COOLDOWN");
			}
			if (retry.isZero() || retry.isNegative()) {
				throw new IllegalArgumentException("retryAfter must be positive");
			}
		}
		if (reason == DropRejectionReason.COOLDOWN && retryAfter.isEmpty()) {
			throw new IllegalArgumentException("COOLDOWN requires retryAfter");
		}
	}

	/** Creates a rejection without retry guidance. */
	public static DropRejection of(DropRejectionReason reason, String diagnostic) {
		return new DropRejection(reason, diagnostic, Optional.empty());
	}

	/** Creates a cooldown rejection with positive retry guidance. */
	public static DropRejection cooldown(Duration retryAfter) {
		return new DropRejection(
				DropRejectionReason.COOLDOWN,
				"Request cooldown has not expired",
				Optional.of(Objects.requireNonNull(retryAfter, "retryAfter")));
	}
}
