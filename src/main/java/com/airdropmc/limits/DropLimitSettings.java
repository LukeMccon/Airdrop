package com.airdropmc.limits;

import java.time.Duration;
import java.util.Objects;

public record DropLimitSettings(
		Duration requestCooldown,
		int maxFalling,
		int maxLanded,
		Duration landedLifetime) {

	public DropLimitSettings {
		Objects.requireNonNull(requestCooldown, "requestCooldown");
		Objects.requireNonNull(landedLifetime, "landedLifetime");
		if (requestCooldown.isZero() || requestCooldown.isNegative()
				|| maxFalling < 1 || maxLanded < 1
				|| landedLifetime.isZero() || landedLifetime.isNegative()) {
			throw new IllegalArgumentException("Drop limits must be positive");
		}
	}

	public long landedLifetimeTicks() {
		return Math.multiplyExact(landedLifetime.getSeconds(), 20L);
	}
}
