package com.airdropmc.packages;

/**
 * Indicates that a packages configuration candidate cannot be materialized.
 */
public final class PackageMaterializationException extends Exception {

	public PackageMaterializationException(String message) {
		super(message);
	}

	public PackageMaterializationException(String message, Throwable cause) {
		super(message, cause);
	}
}
