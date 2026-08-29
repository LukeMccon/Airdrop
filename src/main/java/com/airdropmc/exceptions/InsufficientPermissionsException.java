package com.airdropmc.exceptions;

/** Indicates that a player cannot request the named package. */
public class InsufficientPermissionsException extends Exception {

	/**
	 * Creates a package-permission rejection.
	 *
	 * @param packageName package the player cannot request
	 */
	public InsufficientPermissionsException(String packageName) {
		super("Insufficient permissions for package: " + packageName);
		this.packageName = packageName;
	}

	private final String packageName;

	/** @return package the player cannot request */
	public String getPackageName() {
		return packageName;
	}
}
