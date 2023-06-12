package lukemccon.airdrop.controllers;

import org.bukkit.entity.Player;

import lukemccon.airdrop.exceptions.PackageNotFoundException;
import lukemccon.airdrop.helpers.ChatHandler;
import lukemccon.airdrop.packages.PackageManager;

public class PackagesController {
	
	public static boolean onCommand(Player player, String[] args) {
		
		/**
		 * Handles command involving multiple packages
		 */

		// Lists available packages
		// /airdrop packages

		String list = PackageManager.list();
		ChatHandler.sendMessage(player, list);
		return true;
	}

}
