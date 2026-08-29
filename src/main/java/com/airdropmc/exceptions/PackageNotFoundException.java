package com.airdropmc.exceptions;

/** Indicates that no configured package matches the requested name. */
public class PackageNotFoundException extends Exception {

	/**
	 * Creates a missing-package rejection.
	 *
	 * @param packageName requested package name
	 */
	public PackageNotFoundException(String packageName) {
		super("Package not found: " + packageName);
		this.packageName = packageName;
	}

	private final String packageName;

	/** @return requested package name */
	public String getPackageName() {
		return packageName;
	}
}
