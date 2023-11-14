package lukemccon.airdrop.controllers;

import lukemccon.airdrop.Airdrop;
import lukemccon.airdrop.packages.PackagesGui;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import lukemccon.airdrop.exceptions.PackageNotFoundException;
import lukemccon.airdrop.helpers.ChatHandler;
import lukemccon.airdrop.packages.PackageManager;

public class PackagesController {
	
	public static boolean onCommand(CommandSender sender, String[] args) {
		
		/**
		 * Handles command involving multiple packages
		 */

		// Lists available packages
		// /airdrop packages

		if (!(sender instanceof Player)) {
			ChatHandler.sendErrorMessage(sender,"Must be a player to use this command");
			return true;
		}

		Airdrop.PACKAGES_GUI.openInventory((Player) sender);
		return true;
	}

}
