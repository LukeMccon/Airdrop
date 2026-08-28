package com.airdropmc.exceptions;

public class DropLimitException extends Exception {

	public enum Reason {
		REQUEST_PENDING,
		COOLDOWN,
		FALLING_CAPACITY,
		LANDED_CAPACITY,
		LOCATION_RESERVED,
		SHUTTING_DOWN
	}

	private final Reason reason;
	private final long retryAfterSeconds;

	public DropLimitException(Reason reason) {
		this(reason, 0);
	}

	public DropLimitException(Reason reason, long retryAfterSeconds) {
		super("Drop rejected: " + reason);
		this.reason = reason;
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public Reason getReason() {
		return reason;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}
