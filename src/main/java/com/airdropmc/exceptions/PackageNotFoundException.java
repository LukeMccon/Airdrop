package com.airdropmc.exceptions;

public class PackageNotFoundException extends Exception {

	/**
	 * Indicates that a package is not present in Airdrop
	 * @param packageName package's name
	 */
	public PackageNotFoundException(String packageName) {
		this.packageName = packageName;
	}

	private final String packageName;

	public String getPackageName() {
		return packageName;
	}
}
