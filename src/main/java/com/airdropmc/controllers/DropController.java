package com.airdropmc.controllers;

import java.util.List;

import com.airdropmc.exceptions.CannotAffordException;
import com.airdropmc.exceptions.InsufficientPermissionsException;
import com.airdropmc.exceptions.SkyNotClearException;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.packages.Package;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import net.md_5.bungee.api.ChatColor;
import com.airdropmc.Crate;

public class DropController {

	private static final int TWENTY_BLOCKS = 20;
	private static final int ZERO_BLOCKS = 0;
	private static final double HALF_BLOCK = 0.5;

	private DropController() {

	}

	/**
	 * Drops an airdrop at a given location
	 * @param pkg package to drop
	 * @param world to drop package in
	 * @param loc to drop the package on
	 * @throws SkyNotClearException cannot drop a package due to the location not in the open sky
	 */
	public static void dropPackage(Package pkg, World world, Location loc) throws SkyNotClearException {

		List<ItemStack> items = pkg.getItems();
		Location highestLocation = world.getHighestBlockAt(loc.getBlockX(), loc.getBlockZ()).getLocation().add(new Vector(HALF_BLOCK, ZERO_BLOCKS, HALF_BLOCK));


		if (loc.getBlockY() <= highestLocation.getBlockY()) {
			throw new SkyNotClearException(loc);
		}

		Crate crate = new Crate(highestLocation.add(new Vector(ZERO_BLOCKS, TWENTY_BLOCKS, ZERO_BLOCKS)), world, items);
		crate.dropCrate();
	}

	/**
	 * Drops package on a given player
	 * @param pkg to drop on player
	 * @param player to drop the package on
	 */
	public static void dropPackageOnPlayer(Package pkg, Player player) throws SkyNotClearException {
		World world = player.getWorld();
		Location playerLoc = player.getLocation();

		dropPackage(pkg, world, playerLoc);
	}

	/**
	 * Drops a package on a player, initiated by player (takes into account permissions and econ)
	 * @param pkg to be dropped
	 * @param player getting the package
	 * @throws CannotAffordException if player cannot afford the package
	 * @throws InsufficientPermissionsException player does not have permissions to drop the package
	 * @throws SkyNotClearException sky above player's location is not available
	 */
	public static void playerInitiatedDropPackage(Package pkg, Player player) throws CannotAffordException, InsufficientPermissionsException, SkyNotClearException {

		boolean canDropPackage = PermissionsHelper.hasPermission(player, pkg.getName());

		if (!canDropPackage) {
			throw new InsufficientPermissionsException("You have insufficient permissions to drop that package, you must have" + ChatColor.AQUA + "airdrop.package." + pkg.getName());
		}

		if (Boolean.FALSE.equals(pkg.canAfford(player))) {
			throw new CannotAffordException(player.getName() + " cannot afford package price of " + ChatColor.AQUA + pkg.getPrice() );
		}

		dropPackageOnPlayer(pkg, player);
		// User is charged only if drop package has no exception
		pkg.chargeUser(player);

	}

}
