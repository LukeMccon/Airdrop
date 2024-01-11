package com.airdropmc.exceptions;

import org.bukkit.ChatColor;

public class PackageNotFoundException extends Exception {

	/**
	 * Indicates that a package is not present in Airdrop
	 * @param message package's name
	 */
	public PackageNotFoundException(String message) {
		super("Unable to find package with name: " + ChatColor.GREEN + message + "\nUse "+ ChatColor.YELLOW +"/airdrop packages" + ChatColor.GREEN + " to see all packages");
	}
}
