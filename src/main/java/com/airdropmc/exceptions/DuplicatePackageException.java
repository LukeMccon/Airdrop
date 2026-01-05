package com.airdropmc.exceptions;

public class DuplicatePackageException extends Exception {

	/**
	 * Indicates that a package already exists in Airdrop
	 *
	 * @param packageName package's name
	 */
	public DuplicatePackageException(String packageName) {
		this.packageName = packageName;
	}

	private final String packageName;

	public String getPackageName() {
		return packageName;
	}
}
