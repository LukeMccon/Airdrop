package com.airdropmc.exceptions;

import org.bukkit.ChatColor;

public class DuplicatePackageException extends Exception {

	/**
	 * Indicates that a package is not present in Airdrop
	 * 
	 * @param message package's name
	 */
	public DuplicatePackageException(String message) {
		super("A package already exists with name: " + ChatColor.GREEN + message + "\nUse " + ChatColor.YELLOW
				+ "/airdrop packages" + ChatColor.GREEN + " to see all packages");
	}
}
