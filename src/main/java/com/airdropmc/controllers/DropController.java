package com.airdropmc.controllers;

import java.util.List;

import com.airdropmc.exceptions.CannotAffordException;
import com.airdropmc.exceptions.EconomyUnavailableException;
import com.airdropmc.exceptions.InsufficientPermissionsException;
import com.airdropmc.exceptions.SkyNotClearException;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.packages.Package;
import com.airdropmc.events.PackageDropEvent;
import com.airdropmc.Airdrop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import com.airdropmc.Crate;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.config.DropOptions;

public class DropController {

	private static final int ZERO_BLOCKS = 0;
	private static final double HALF_BLOCK = 0.5;

	/**
	 * Drops an airdrop at a given location
	 * 
	 * @param pkg   package to drop
	 * @param world to drop package in
	 * @param loc   to drop the package on
	 * @throws SkyNotClearException cannot drop a package due to the location not in
	 *                              the open sky
	 */
	public static void dropPackage(Package pkg, World world, Location loc) throws SkyNotClearException {
		dropPackage(pkg, world, loc, DropOptions.createDefault());
	}

	/**
	 * Drops an airdrop at a given location with custom options
	 * 
	 * @param pkg     package to drop
	 * @param world   to drop package in
	 * @param loc     to drop the package on
	 * @param options custom configuration options for this drop
	 * @throws SkyNotClearException cannot drop a package due to the location not in
	 *                              the open sky
	 */
	public static void dropPackage(Package pkg, World world, Location loc, DropOptions options)
			throws SkyNotClearException {
		// Handle null options by using defaults
		options = options != null ? options : DropOptions.createDefault();
		Location spawnLocation = getSpawnLocation(world, loc, options);
		dropPackageAtLocation(pkg, world, spawnLocation, options);
	}

	/**
	 * Drops package on a given player
	 * 
	 * @param pkg    to drop on player
	 * @param player to drop the package on
	 */
	public static void dropPackageOnPlayer(Package pkg, Player player) throws SkyNotClearException {
		dropPackageOnPlayer(pkg, player, DropOptions.createDefault());
	}

	/**
	 * Drops package on a given player with custom options
	 * 
	 * @param pkg     to drop on player
	 * @param player  to drop the package on
	 * @param options custom configuration options for this drop
	 */
	public static void dropPackageOnPlayer(Package pkg, Player player, DropOptions options)
			throws SkyNotClearException {
		// Handle null options by using defaults
		options = options != null ? options : DropOptions.createDefault();
		World world = player.getWorld();
		Location playerLoc = player.getLocation();

		dropPackage(pkg, world, playerLoc, options);
	}

	/**
	 * Drops a package on a player, initiated by player (takes into account
	 * permissions and econ)
	 * 
	 * @param pkg    to be dropped
	 * @param player getting the package
	 * @throws CannotAffordException            if player cannot afford the package
	 * @throws EconomyUnavailableException      if economy is enabled but no provider
	 *                                          is available
	 * @throws InsufficientPermissionsException player does not have permissions to
	 *                                          drop the package
	 * @throws SkyNotClearException             sky above player's location is not
	 *                                          available
	 */
	public static void playerInitiatedDropPackage(Package pkg, Player player)
			throws CannotAffordException, EconomyUnavailableException, InsufficientPermissionsException, SkyNotClearException {
		playerInitiatedDropPackage(pkg, player, DropOptions.createDefault());
	}

	/**
	 * Drops a package on a player with custom options, initiated by player (takes
	 * into account permissions and econ)
	 * 
	 * @param pkg     to be dropped
	 * @param player  getting the package
	 * @param options custom configuration options for this drop
	 * @throws CannotAffordException            if player cannot afford the package
	 * @throws EconomyUnavailableException      if economy is enabled but no provider
	 *                                          is available
	 * @throws InsufficientPermissionsException player does not have permissions to
	 *                                          drop the package
	 * @throws SkyNotClearException             sky above player's location is not
	 *                                          available
	 */
	public static void playerInitiatedDropPackage(Package pkg, Player player, DropOptions options)
			throws CannotAffordException, EconomyUnavailableException, InsufficientPermissionsException, SkyNotClearException {
		// Handle null options by using defaults
		options = options != null ? options : DropOptions.createDefault();

		boolean canDropPackage = PermissionsHelper.hasPermission(player, pkg.getName());

		if (!canDropPackage) {
			throw new InsufficientPermissionsException(pkg.getName());
		}
		if (ConfigKeys.isEconomyEnabled() && Airdrop.getAirdropEconomy() == null) {
			throw new EconomyUnavailableException();
		}

		if (!pkg.canAfford(player)) {
			throw new CannotAffordException(player.getName(), pkg.getPrice());
		}

		World world = player.getWorld();
		Location playerLoc = player.getLocation();
		Location spawnLocation = getSpawnLocation(world, playerLoc, options);

		// Charge before crate spawn to prevent unpaid drops on transaction failure.
		pkg.chargeUser(player);
		try {
			dropPackageAtLocation(pkg, world, spawnLocation, options);
		} catch (RuntimeException dropFailure) {
			attemptRefundOnDropFailure(pkg, player, dropFailure);
			throw dropFailure;
		}
	}

	private static void attemptRefundOnDropFailure(Package pkg, Player player, RuntimeException dropFailure) {
		if (!ConfigKeys.isEconomyEnabled()) {
			return;
		}

		Economy economy = Airdrop.getAirdropEconomy();
		if (economy == null) {
			dropFailure.addSuppressed(new IllegalStateException(
					"Drop failed after charging " + player.getName() + " but no economy provider was available for refund"));
			return;
		}

		try {
			EconomyResponse response = economy.depositPlayer(player, pkg.getPrice());
			if (!response.transactionSuccess()) {
				dropFailure.addSuppressed(new IllegalStateException(
						"Drop failed after charging " + player.getName() + " and refund transaction failed"));
			}
		} catch (RuntimeException refundFailure) {
			dropFailure.addSuppressed(refundFailure);
		}
	}

	private static Location getSpawnLocation(World world, Location loc, DropOptions options) throws SkyNotClearException {
		Location highestLocation = world.getHighestBlockAt(loc.getBlockX(), loc.getBlockZ()).getLocation()
				.add(HALF_BLOCK, ZERO_BLOCKS, HALF_BLOCK);

		if (loc.getBlockY() < highestLocation.getBlockY()) {
			throw new SkyNotClearException(loc);
		}

		return highestLocation.add(ZERO_BLOCKS, options.getDropHeight(), ZERO_BLOCKS);
	}

	private static void dropPackageAtLocation(Package pkg, World world, Location spawnLocation, DropOptions options) {
		List<ItemStack> items = pkg.getItems();
		Crate crate = new Crate(spawnLocation.clone(), world, items, options);
		crate.dropCrate();
		Bukkit.getPluginManager().callEvent(new PackageDropEvent(crate, world, crate.getDropLocation()));
	}

}
