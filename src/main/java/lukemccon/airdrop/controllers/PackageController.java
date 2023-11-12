package lukemccon.airdrop.controllers;

import lukemccon.airdrop.exceptions.PackageNotFoundException;
import lukemccon.airdrop.helpers.ChatHandler;
import lukemccon.airdrop.packages.CreatePackageGui;
import lukemccon.airdrop.packages.PackageManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

public class PackageController {
	
	public static boolean onCommand(CommandSender sender, String[] args) {

		if (args.length < 2) {
			ChatHandler.sendErrorMessage(sender, "Must specify a package name");
			ChatHandler.sendErrorMessage(sender, "Example: /airdrop starter");
			return true;
		}

		if (Objects.equals(args[1], "create")) {
			return createPackageCommand(sender, args);
		}

		if (Objects.equals(args[1], "delete")) {
			return deletePackageCommand(sender, args);
		}

		String packageName = args[1];

		try {
			ChatHandler.sendMessage(sender, ChatColor.WHITE + "\nPackage info for: " + ChatColor.AQUA + packageName + "\n================\n" +  ChatColor.WHITE + PackageManager.getInfo(packageName));
		} catch (PackageNotFoundException e) {
			ChatHandler.sendErrorMessage(sender, e.getMessage());
		}

		return true;
	}

	public static boolean deletePackageCommand(CommandSender sender, String[] args) {
		if (args.length != 3 ) {
			ChatHandler.sendErrorMessage(sender, "Need to specify a package name to delete");
		}

		String packageName = args[2];
		try {
			PackageManager.deletePackage(packageName);
			ChatHandler.sendMessage(sender, ChatColor.AQUA + packageName + ChatColor.BLUE + " was successfully deleted");
		} catch (PackageNotFoundException e) {
			ChatHandler.sendErrorMessage(sender, "Unable to delete package " + ChatColor.DARK_RED + packageName + ChatColor.RED + " not found");
		}
		return true;
	}



	public static boolean createPackageCommand(CommandSender sender, String[] args) {

			if (args.length != 4) {
				ChatHandler.sendErrorMessage(sender, "Package create command requires 4 total arguments");
				ChatHandler.sendErrorMessage(sender, "Example: /airdrop package create myPackage 12.0");
			}

			// Create a new package
			if (!(sender instanceof Player)) {
				ChatHandler.sendErrorMessage(sender,"Must be a player to use this command");
				return true;
			}

			Player player = (Player) sender;

			String packageName = args[2];
			String priceString = args[3];
			Double price = null;

			if ( packageName == null || packageName.isEmpty()) {
				ChatHandler.sendErrorMessage(sender, "You must provide a name for the package");
				return true;
			}

			if ( priceString != null && !priceString.isEmpty()) {
				try {
					price = Double.parseDouble(priceString);
				} catch (NumberFormatException e) {
					ChatHandler.sendErrorMessage(sender, "You must provide the package price as a double");
					ChatHandler.sendErrorMessage(sender, "Example: /airdrop package create myPackage 12.0");
					return true;
				}
			}


			CreatePackageGui createGui = new CreatePackageGui(packageName, price);

			createGui.openInventory(player);

			return true;

	}

}