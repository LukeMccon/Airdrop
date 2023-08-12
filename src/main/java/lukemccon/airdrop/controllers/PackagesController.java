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
		System.out.println("this command was executed");

		Airdrop.PACKAGES_GUI.openInventory((Player) sender);

		String list = PackageManager.list();
		ChatHandler.sendMessage(sender, list);
		return true;
	}

}
