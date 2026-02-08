package com.airdropmc.controllers;

import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.packages.CreatePackageGui;
import com.airdropmc.packages.PackageManager;
import com.airdropmc.packages.Package;
import com.airdropmc.exceptions.DuplicatePackageException;
import com.airdropmc.exceptions.PackageNotFoundException;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PackageController {

	private PackageController() {

	}

	/**
	 * Subcommand that handles package deletion
	 * 
	 * @param sender who executed the command
	 * @param args   provided to the command
	 */
	public static void deletePackageCommand(CommandSender sender, String[] args) {
		if (args.length != 3) {
			ChatHandler.sendError(sender, MessageKey.PACKAGES_DELETE_SPECIFY);
			return;
		}

		// Create a new package
		if (!PermissionsHelper.isAdmin(sender)) {
			ChatHandler.sendError(sender, MessageKey.PACKAGES_DELETE_PERMISSION);
			return;
		}

		String packageName = args[2];
		try {
			PackageManager.deletePackage(packageName);
			ChatHandler.send(sender, MessageKey.PACKAGES_DELETED, Map.of("name", packageName));
		} catch (PackageNotFoundException e) {
			ChatHandler.sendError(sender, MessageKey.ERROR_PACKAGE_DELETE_NOT_FOUND, Map.of("name", packageName));
		}
	}

	/**
	 * Deletes a package given the name
	 * 
	 * @param packageName name of the package to delete
	 * @throws PackageNotFoundException if the package doesn't exist
	 */
	public static void deletePackage(String packageName) throws PackageNotFoundException {
		PackageManager.deletePackage(packageName);
	}

	/**
	 * Subcommand that handles package creation
	 * 
	 * @param sender of command
	 * @param args   command args
	 */
	public static void createPackageCommand(CommandSender sender, String[] args) {

		if (args.length != 4) {
			ChatHandler.sendError(sender, MessageKey.PACKAGES_CREATE_ARGS);
			ChatHandler.sendError(sender, MessageKey.PACKAGES_CREATE_USAGE);
			ChatHandler.sendError(sender, MessageKey.PACKAGES_CREATE_EXAMPLE);
			return;
		}

		if (!PermissionsHelper.isAdmin(sender)) {
			ChatHandler.sendError(sender, MessageKey.ADMIN_PERMISSION_REQUIRED);
			return;
		}

		// Create a new package
		if (!(sender instanceof Player player)) {
			ChatHandler.sendError(sender, MessageKey.COMMANDS_PLAYER_ONLY);
			return;
		}

		String packageName = args[2];
		String priceString = args[3];
		double price = 0;

		if (packageName == null || packageName.isBlank()) {
			ChatHandler.sendError(sender, MessageKey.PACKAGES_NAME_REQUIRED);
			return;
		}

		if (priceString != null && !priceString.isBlank()) {
			try {
				price = Double.parseDouble(priceString);
			} catch (NumberFormatException e) {
				ChatHandler.sendError(sender, MessageKey.PACKAGES_PRICE_REQUIRED);
				ChatHandler.sendError(sender, MessageKey.PACKAGES_CREATE_USAGE);
				ChatHandler.sendError(sender, MessageKey.PACKAGES_CREATE_EXAMPLE);
				return;
			}
		}

		if (!Package.isValidPrice(price)) {
			ChatHandler.sendError(sender, MessageKey.PACKAGES_PRICE_INVALID);
			return;
		}

		CreatePackageGui createGui = new CreatePackageGui(packageName, price);

		createGui.openInventory(player);
	}

	/**
	 * Creates a new package given a name and price
	 * 
	 * @param packageName name of package to create
	 * @param price       price of package to create
	 * @throws PackageNotFoundException if package already exists
	 */
	public static void createPackage(String packageName, double price) throws DuplicatePackageException {
		List<ItemStack> items = new ArrayList<>();
		Package pkg = new Package(packageName, price, items);
		PackageManager.createPackage(pkg);
	}

	/**
	 * Creates a new package given a name, price, and items
	 * 
	 * @param packageName name of package
	 * @param price       price of package
	 * @param items       items in package
	 * @throws PackageNotFoundException if package already exists
	 */
	public static void createPackage(String packageName, double price, List<ItemStack> items)
			throws DuplicatePackageException {
		Package pkg = new Package(packageName, price, items);
		PackageManager.createPackage(pkg);
	}

}
