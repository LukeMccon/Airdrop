package lukemccon.airdrop.controllers;

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

		String list = PackageManager.list();
		ChatHandler.sendMessage(sender, list);
		return true;
	}

}
