package com.airdropmc.exceptions;

import com.airdropmc.helpers.ChatTheme;

public class DuplicatePackageException extends Exception {

	/**
	 * Indicates that a package is not present in Airdrop
	 * 
	 * @param message package's name
	 */
	public DuplicatePackageException(String message) {
		super("A package already exists with name: " + ChatTheme.success() + message + "\nUse " + ChatTheme.warning()
				+ "/airdrop packages" + ChatTheme.success() + " to see all packages");
	}
}
