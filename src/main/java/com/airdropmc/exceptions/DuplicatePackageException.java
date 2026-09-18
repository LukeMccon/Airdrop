package com.airdropmc.exceptions;

/** Indicates that a package name is already present in Airdrop. */
public class DuplicatePackageException extends Exception {

	/**
	 * Creates a duplicate-package rejection.
	 *
	 * @param packageName conflicting package name
	 */
	public DuplicatePackageException(String packageName) {
		super("Package already exists: " + packageName);
		this.packageName = packageName;
	}

	private final String packageName;

	/** @return conflicting package name */
	public String getPackageName() {
		return packageName;
	}
}
