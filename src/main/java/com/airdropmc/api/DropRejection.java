package com.airdropmc.api;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Stable rejection details with optional cooldown retry guidance.
 *
 * @param reason machine-readable rejection reason
 * @param diagnostic non-blank diagnostic intended for logs and debugging
 * @param retryAfter positive retry delay for cooldown and remote-attempt rejections only
 */
public record DropRejection(
		DropRejectionReason reason,
		String diagnostic,
		Optional<Duration> retryAfter) {

	/** Validates stable rejection details and optional retry guidance. */
	public DropRejection {
		reason = Objects.requireNonNull(reason, "reason");
		diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
		retryAfter = Objects.requireNonNull(retryAfter, "retryAfter");
		if (diagnostic.isBlank()) {
			throw new IllegalArgumentException("diagnostic must not be blank");
		}
		if (retryAfter.isPresent()) {
			Duration retry = retryAfter.orElseThrow();
			if (reason != DropRejectionReason.COOLDOWN && reason != DropRejectionReason.REMOTE_LOAD_THROTTLED) {
				throw new IllegalArgumentException("retryAfter is only valid for cooldown and remote-attempt rejections");
			}
			if (retry.isZero() || retry.isNegative()) {
				throw new IllegalArgumentException("retryAfter must be positive");
			}
		}
		if ((reason == DropRejectionReason.COOLDOWN || reason == DropRejectionReason.REMOTE_LOAD_THROTTLED)
				&& retryAfter.isEmpty()) {
			throw new IllegalArgumentException("Throttled rejections require retryAfter");
		}
	}

	/**
	 * Creates a rejection without retry guidance.
	 *
	 * @param reason machine-readable rejection reason
	 * @param diagnostic non-blank diagnostic intended for logs and debugging
	 * @return rejection without a retry delay
	 */
	public static DropRejection of(DropRejectionReason reason, String diagnostic) {
		return new DropRejection(reason, diagnostic, Optional.empty());
	}

	/**
	 * Creates a cooldown rejection with positive retry guidance.
	 *
	 * @param retryAfter positive duration until another request may be attempted
	 * @return cooldown rejection with retry guidance
	 */
	public static DropRejection cooldown(Duration retryAfter) {
		return new DropRejection(
				DropRejectionReason.COOLDOWN,
				"Request cooldown has not expired",
				Optional.of(Objects.requireNonNull(retryAfter, "retryAfter")));
	}
}
