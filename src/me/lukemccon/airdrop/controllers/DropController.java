package me.lukemccon.airdrop.controllers;

import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.lukemccon.airdrop.Crate;
import me.lukemccon.airdrop.helpers.ChatHandler;
import me.lukemccon.airdrop.helpers.DropHelper;
import me.lukemccon.airdrop.packages.PackageManager;

public class DropController {
	/**
	 * 
	 * @param player sender of the command
	 * @param args passed in via commandline
	 * @return
	 */
	public static boolean onCommand(Player player, String[] args) {
		
		/**
		 * Handles the following subcommands:
		 * 
		 * drop
		 * gift
		 */
		
		String packageName = null;
		Player dropPlayer = null;
		Boolean gift = false;
		Location loc = null;
		
		switch (args[0]) {

			case "gift":
				gift = true;
				
				if (args.length < 3) {
					ChatHandler.sendErrorMessage(player, "Invalid gift format");
					ChatHandler.sendMessage(player, "Usage: /airdrop gift [player] [package]");
					ChatHandler.sendMessage(player, "Example usage:" );
					ChatHandler.sendMessage(player, "/airdrop gift notch starter");
					return true;
				}
				
				packageName = args[2];
				
				if (!PackageManager.has(packageName)) {
					ChatHandler.sendErrorMessage(player, "Couldn't find package: " + args[2]);
					return true;
				}
				
				dropPlayer = Bukkit.getServer().getPlayer(String.valueOf(args[1]));
				if (dropPlayer == null) {
					ChatHandler.sendErrorMessage(player, "Player not found with username" + args[1]);
					return true;
				}
				break;

			case "drop":
				packageName = args[1];
				dropPlayer = player;
				loc = dropPlayer.getLocation();
				if (args.length > 2) {
					switch (args[1]) {
						case "loc":
							packageName = args[2];
							loc = DropHelper.resolveLocation(dropPlayer, dropPlayer.getWorld(), args[3], args[4]);
							break;
						default:
							break;
					}
				}
				
				break;
	
			default:
				return true;

		}

		ArrayList<ItemStack> items = DropHelper.getItemsInPackage(packageName, dropPlayer);

		if (items.isEmpty()) {
			String errorMsg =
					"Couldn't find package " + packageName + "\n" +
					"Use /airdrop packages to view avaliable packages";
			ChatHandler.sendErrorMessage(player, errorMsg);
			return true;
		}


		boolean noBlocksAbovePlayer = DropHelper.checkBlocksAbovePlayer(loc);

		if (noBlocksAbovePlayer) {

			Crate crate = new Crate(loc, loc.getWorld(), items);
			crate.dropCrate();

			if (!gift) {
			ChatHandler.sendMessage(player, "Calling in airdrop " + packageName);
			}
			else {
				ChatHandler.sendMessage(player, "Sending " + packageName + " to " + dropPlayer.getName());
				ChatHandler.sendMessage(dropPlayer, "Incoming package from " + player.getName());
			}
				
			
			return true;

		} else {
			// Send some error
			ChatHandler.sendErrorMessage(player, "No space above " + dropPlayer.getName());
		}
		
		return true;
		
	}

}
