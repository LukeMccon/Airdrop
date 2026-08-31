package com.airdropmc.exceptions;

/** Indicates that creating another package would exceed Airdrop's package limit. */
public final class PackageCapacityException extends RuntimeException {
	private final int requestedCount;
	private final int limit;

	/**
	 * Creates a package-capacity rejection.
	 *
	 * @param requestedCount number of packages the rejected configuration would contain
	 * @param limit maximum supported package count
	 */
	public PackageCapacityException(int requestedCount, int limit) {
		super("Cannot configure " + requestedCount + " packages; the limit is " + limit);
		this.requestedCount = requestedCount;
		this.limit = limit;
	}

	public int getRequestedCount() {
		return requestedCount;
	}

	public int getLimit() {
		return limit;
	}
}
