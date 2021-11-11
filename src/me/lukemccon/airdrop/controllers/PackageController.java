package me.lukemccon.airdrop.controllers;

import org.bukkit.entity.Player;

import me.lukemccon.airdrop.exceptions.PackageNotFoundException;
import me.lukemccon.airdrop.helpers.ChatHandler;
import me.lukemccon.airdrop.packages.PackageManager;

public class PackageController {
	
	public static boolean onCommand(Player player, String[] args) {
		
		/**
		 * Handles package directory related commands
		 */
		
		switch(args.length) {
			case 2:
				switch (args[1]) {
				// Get information about a package
				// Ex: /airdrop packages info starter
				case "info":
					String packageInfo;
					try {
						packageInfo = PackageManager.getInfo(args[2]);
						ChatHandler.sendMessage(player, packageInfo );
					} catch (PackageNotFoundException e) {
						ChatHandler.sendErrorMessage(player, e.getMessage());
					}
					break;
				}
				
			default:
				
				// Lists avaliable packages
				// /airdrop packages
				
				String list = PackageManager.list();
				ChatHandler.sendMessage(player, list);
				return true;
		}
	}

}
