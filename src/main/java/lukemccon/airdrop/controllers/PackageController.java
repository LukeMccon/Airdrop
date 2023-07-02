package lukemccon.airdrop.controllers;

import lukemccon.airdrop.exceptions.PackageNotFoundException;
import lukemccon.airdrop.helpers.ChatHandler;
import lukemccon.airdrop.packages.PackageManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PackageController {
	
	public static boolean onCommand(CommandSender sender, String[] args) {

		if (args.length < 2) {
			ChatHandler.sendErrorMessage(sender, "Must specify a package name");
			ChatHandler.sendErrorMessage(sender, "Example: /airdrop starter");
			return true;
		}

		String packageName = args[1];

		try {
			ChatHandler.sendMessage(sender, ChatColor.WHITE + "\nPackage info for: " + ChatColor.AQUA + packageName + "\n================\n" +  ChatColor.WHITE + PackageManager.getInfo(packageName));
		} catch (PackageNotFoundException e) {
			ChatHandler.sendErrorMessage(sender, e.getMessage());
		}

		return true;
	}

}