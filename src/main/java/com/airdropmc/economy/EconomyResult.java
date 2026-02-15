package com.airdropmc.economy;

public record EconomyResult(boolean success, String message) {

	public static EconomyResult ok() {
		return new EconomyResult(true, "");
	}

	public static EconomyResult fail(String message) {
		return new EconomyResult(false, message);
	}
}
