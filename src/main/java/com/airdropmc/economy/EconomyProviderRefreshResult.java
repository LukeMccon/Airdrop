package com.airdropmc.economy;

import java.util.Objects;

public record EconomyProviderRefreshResult(Outcome outcome, String providerName) {

	public enum Outcome {
		ACTIVE,
		DISABLED,
		UNAVAILABLE
	}

	public EconomyProviderRefreshResult {
		Objects.requireNonNull(outcome, "outcome");
		providerName = providerName == null ? "" : providerName;
	}

	public static EconomyProviderRefreshResult active(String providerName) {
		return new EconomyProviderRefreshResult(Outcome.ACTIVE, providerName);
	}

	public static EconomyProviderRefreshResult disabled() {
		return new EconomyProviderRefreshResult(Outcome.DISABLED, "");
	}

	public static EconomyProviderRefreshResult unavailable() {
		return new EconomyProviderRefreshResult(Outcome.UNAVAILABLE, "");
	}
}
