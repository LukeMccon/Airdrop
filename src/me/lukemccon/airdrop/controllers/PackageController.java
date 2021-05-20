package me.lukemccon.airdrop.controllers;

import org.bukkit.entity.Player;

import me.lukemccon.airdrop.helpers.ChatHandler;
import me.lukemccon.airdrop.packages.PackageManager;

public class PackageController {
	
	public static boolean onCommand(Player player, String[] args) {
		switch(args.length) {
		
		case 2:
				switch (args[1]) {
				case "info":
					ChatHandler.sendMessage(player, PackageManager.getInfo(args[2]));
					break;
				}
			
		default:
			String list = PackageManager.list();
			ChatHandler.sendMessage(player, list);
			return true;
		}
	}

}
