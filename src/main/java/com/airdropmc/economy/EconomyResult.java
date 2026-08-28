package com.airdropmc.economy;

import java.util.Objects;

public record EconomyResult(Outcome outcome, String message) {

	public enum Outcome {
		SUCCESS,
		REJECTED,
		UNKNOWN
	}

	public EconomyResult {
		Objects.requireNonNull(outcome, "outcome");
		message = message == null ? "" : message;
	}

	public static EconomyResult ok() {
		return new EconomyResult(Outcome.SUCCESS, "");
	}

	public static EconomyResult rejected(String message) {
		return new EconomyResult(Outcome.REJECTED, message);
	}

	public static EconomyResult unknown(String message) {
		return new EconomyResult(Outcome.UNKNOWN, message);
	}

	@Deprecated
	public static EconomyResult fail(String message) {
		return rejected(message);
	}

	public boolean success() {
		return outcome == Outcome.SUCCESS;
	}
}
