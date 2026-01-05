package com.airdropmc.exceptions;

import com.airdropmc.helpers.ChatTheme;

public class PackageNotFoundException extends Exception {

	/**
	 * Indicates that a package is not present in Airdrop
	 * @param message package's name
	 */
	public PackageNotFoundException(String message) {
		super("Unable to find package with name: " + ChatTheme.success() + message + "\nUse "
				+ ChatTheme.warning() + "/airdrop packages" + ChatTheme.success() + " to see all packages");
	}
}
