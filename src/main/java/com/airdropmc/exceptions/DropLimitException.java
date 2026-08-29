package com.airdropmc.exceptions;

/**
 * Indicates that drop admission rejected a request. Integrations should inspect
 * {@link #getReason()} instead of parsing the diagnostic message.
 */
public class DropLimitException extends Exception {

	/** Describes the admission rule that rejected the drop. */
	public enum Reason {
		/** The player already has a drop request in progress. */
		REQUEST_PENDING,
		/** The player's request cooldown has not expired. */
		COOLDOWN,
		/** The server has reached its configured falling-crate limit. */
		FALLING_CAPACITY,
		/** The server has reached its configured landed-crate limit. */
		LANDED_CAPACITY,
		/** Another active drop has reserved the requested landing location. */
		LOCATION_RESERVED,
		/** The plugin has stopped accepting new drops during shutdown. */
		SHUTTING_DOWN
	}

	private final Reason reason;
	private final long retryAfterSeconds;

	/**
	 * Creates a rejection without a retry delay.
	 *
	 * @param reason admission rule that rejected the drop
	 */
	public DropLimitException(Reason reason) {
		this(reason, 0);
	}

	/**
	 * Creates a rejection with optional cooldown retry guidance.
	 *
	 * @param reason admission rule that rejected the drop
	 * @param retryAfterSeconds whole seconds until retry; meaningful only for
	 *        {@link Reason#COOLDOWN}
	 */
	public DropLimitException(Reason reason, long retryAfterSeconds) {
		super("Drop rejected: " + reason);
		this.reason = reason;
		this.retryAfterSeconds = retryAfterSeconds;
	}

	/** @return admission rule that rejected the drop */
	public Reason getReason() {
		return reason;
	}

	/**
	 * @return configured whole seconds until retry; meaningful only for
	 *         {@link Reason#COOLDOWN}
	 */
	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}
