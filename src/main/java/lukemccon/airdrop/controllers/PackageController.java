package lukemccon.airdrop.controllers;

import lukemccon.airdrop.exceptions.PackageNotFoundException;
import lukemccon.airdrop.helpers.ChatHandler;
import lukemccon.airdrop.packages.PackageManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class PackageController {
	
	public static boolean onCommand(Player player, String[] args) {

		if (args.length < 2) {
			ChatHandler.sendErrorMessage(player, "Must specify a package name");
		}

		String packageName = args[1];

		try {
			ChatHandler.sendMessage(player, ChatColor.WHITE + PackageManager.getInfo(packageName));
		} catch (PackageNotFoundException e) {
			ChatHandler.sendErrorMessage(player, e.getMessage());
		}

		return true;
	}

}
