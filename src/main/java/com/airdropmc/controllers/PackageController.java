package com.airdropmc.controllers;

import com.airdropmc.Airdrop;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.packages.CreatePackageGui;
import com.airdropmc.packages.Package;
import com.airdropmc.packages.PackageNamePolicy;
import com.airdropmc.exceptions.PackageNotFoundException;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

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

		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin == null || !Airdrop.isReady()) {
			ChatHandler.sendError(sender, MessageKey.ERROR_PLUGIN_NOT_READY);
			return;
		}

		String packageName = args[2];
		try {
			CompletionStage<Boolean> deletion = plugin.deletePackageAsync(packageName);
			if (deletion == null) {
				handleDeleteCompletion(sender, packageName, false, null);
				return;
			}
			deletion.whenComplete((deleted, failure) -> {
				if (Airdrop.isShuttingDown() || Airdrop.getPluginInstance() != plugin) {
					return;
				}
				handleDeleteCompletion(sender, packageName, deleted, failure);
			});
		} catch (RuntimeException failure) {
			if (!Airdrop.isShuttingDown() && Airdrop.getPluginInstance() == plugin) {
				handleDeleteCompletion(sender, packageName, false, failure);
			}
		}
	}

	private static void handleDeleteCompletion(
			CommandSender sender,
			String packageName,
			Boolean deleted,
			Throwable failure) {
		if (failure == null && Boolean.TRUE.equals(deleted)) {
			ChatHandler.send(sender, MessageKey.PACKAGES_DELETED, Map.of("name", packageName));
			return;
		}

		Throwable cause = unwrapCompletionException(failure);
		if (cause instanceof PackageNotFoundException notFound) {
			ChatHandler.sendError(sender, MessageKey.ERROR_PACKAGE_DELETE_NOT_FOUND,
					Map.of("name", notFound.getPackageName()));
			return;
		}
		ChatHandler.sendError(sender, MessageKey.ERROR_PACKAGE_SAVE_FAILED);
	}

	private static Throwable unwrapCompletionException(Throwable failure) {
		Throwable cause = failure;
		while (cause instanceof CompletionException && cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause;
	}

	/**
	 * Deletes a package given the name.
	 *
	 * @param packageName name of the package to delete
	 * @return completion indicating whether the package was committed and deleted
	 */
	public static CompletionStage<Boolean> deletePackage(String packageName) {
		PackageNamePolicy.requireCanonical(packageName);
		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin == null || !Airdrop.isReady()) {
			return CompletableFuture.completedFuture(false);
		}
		return plugin.deletePackageAsync(packageName);
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

		String packageName = args[2] == null ? "" : args[2].trim();
		String priceString = args[3];
		double price = 0;

		PackageNamePolicy.Result nameValidation = PackageNamePolicy.validate(packageName);
		if (!nameValidation.accepted()) {
			MessageKey message = switch (nameValidation.rejection()) {
				case MISSING -> MessageKey.PACKAGES_NAME_REQUIRED;
				case INVALID_CHARACTERS -> MessageKey.PACKAGES_NAME_INVALID;
				case RESERVED -> MessageKey.PACKAGES_NAME_RESERVED;
			};
			ChatHandler.sendError(sender, message);
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

		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin == null || !Airdrop.isReady()) {
			ChatHandler.sendError(sender, MessageKey.ERROR_PLUGIN_NOT_READY);
			return;
		}

		CreatePackageGui createGui = new CreatePackageGui(packageName, price);
		if (!createGui.openInventory(player)) {
			ChatHandler.sendError(sender, MessageKey.PACKAGES_CREATE_OPEN_ERROR);
		}
	}

	/**
	 * Creates a new package given a name and price.
	 *
	 * @param packageName name of package to create
	 * @param price       price of package to create
	 * @return completion indicating whether the package was committed and created
	 */
	public static CompletionStage<Boolean> createPackage(String packageName, double price) {
		return createPackage(packageName, price, new ArrayList<>());
	}

	/**
	 * Creates a new package given a name, price, and items.
	 *
	 * @param packageName name of package
	 * @param price       price of package
	 * @param items       items in package
	 * @return completion indicating whether the package was committed and created
	 */
	public static CompletionStage<Boolean> createPackage(String packageName, double price, List<ItemStack> items) {
		PackageNamePolicy.requireCanonical(packageName);
		Package pkg = new Package(packageName, price, items);
		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin == null || !Airdrop.isReady()) {
			return CompletableFuture.completedFuture(false);
		}
		return plugin.createPackageAsync(pkg);
	}

}
