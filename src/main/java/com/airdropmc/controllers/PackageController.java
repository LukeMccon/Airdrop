package com.airdropmc.controllers;

import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.packages.CreatePackageGui;
import com.airdropmc.packages.PackageManager;
import com.airdropmc.exceptions.PackageNotFoundException;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PackageController {

	private PackageController() {

	}


	/**
	 * Subcommand that handles package deletion
	 * @param sender who executed the command
	 * @param args provided to the command
	 */
	public static void deletePackageCommand(CommandSender sender, String[] args) {
		if (args.length != 3 ) {
			ChatHandler.sendErrorMessage(sender, "Need to specify a package name to delete");
		}
		String packageName = args[2];
		try {
			PackageManager.deletePackage(packageName);
			ChatHandler.sendMessage(sender, ChatColor.AQUA + packageName + ChatColor.BLUE + " was successfully deleted");
		} catch (PackageNotFoundException e) {
			ChatHandler.sendErrorMessage(sender, "Unable to delete package: " + ChatColor.DARK_RED + packageName + ChatColor.RED + " not found");
		}
	}


	/**
	 * Subcommand that handles package creation
	 * @param sender of command
	 * @param args command args
	 */
	public static void createPackageCommand(CommandSender sender, String[] args) {

			if (args.length != 4) {
				ChatHandler.sendErrorMessage(sender, "Package create command requires 4 total arguments");
				ChatHandler.sendErrorMessage(sender, "Example: /airdrop package create myPackage 12.0");
			}

			// Create a new package
			if (!(sender instanceof Player player)) {
				ChatHandler.sendErrorMessage(sender,"Must be a player to use this command");
				return;
			}

		String packageName = args[2];
			String priceString = args[3];
			double price = 0;

			if ( packageName == null || packageName.isBlank()) {
				ChatHandler.sendErrorMessage(sender, "You must provide a name for the package");
				return;
			}

			if ( priceString != null && !priceString.isBlank()) {
				try {
					price = Double.parseDouble(priceString);
				} catch (NumberFormatException e) {
					ChatHandler.sendErrorMessage(sender, "You must provide the package price as a double");
					ChatHandler.sendErrorMessage(sender, "Example: /airdrop package create myPackage 12.0");
					return;
				}
			}


			CreatePackageGui createGui = new CreatePackageGui(packageName, price);

			createGui.openInventory(player);
	}

}