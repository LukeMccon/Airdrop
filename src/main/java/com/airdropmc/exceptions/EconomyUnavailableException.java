package com.airdropmc.exceptions;

import java.util.Objects;

/**
 * Indicates that a priced drop cannot start because economy support is not
 * available. This is a synchronous configuration/provider rejection, not an
 * asynchronous affordability result.
 */
public class EconomyUnavailableException extends Exception {

	/** Describes why economy support is unavailable. */
	public enum Reason {
		/** Economy support is disabled in Airdrop's configuration. */
		DISABLED,
		/** No compatible economy provider is currently registered. */
		NO_PROVIDER
	}

	private final Reason reason;

	/**
	 * Creates an economy-availability rejection.
	 *
	 * @param reason reason economy support is unavailable
	 * @throws NullPointerException if {@code reason} is {@code null}
	 */
	public EconomyUnavailableException(Reason reason) {
		super(messageFor(reason));
		this.reason = reason;
	}

	/** @return reason economy support is unavailable */
	public Reason getReason() {
		return reason;
	}

	private static String messageFor(Reason reason) {
		return switch (Objects.requireNonNull(reason, "reason")) {
			case DISABLED -> "Economy is disabled";
			case NO_PROVIDER -> "No economy provider is available";
		};
	}
}
