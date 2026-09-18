package com.airdropmc.packages;

/**
 * Indicates that a packages configuration candidate cannot be materialized.
 */
public final class PackageMaterializationException extends Exception {

	/**
	 * Creates a package-configuration rejection.
	 *
	 * @param message diagnostic describing the invalid configuration
	 */
	public PackageMaterializationException(String message) {
		super(message);
	}

	/**
	 * Creates a package-configuration rejection caused by a lower-level failure.
	 *
	 * @param message diagnostic describing the invalid configuration
	 * @param cause lower-level failure
	 */
	public PackageMaterializationException(String message, Throwable cause) {
		super(message, cause);
	}
}
