package me.lukemccon.airdrop.helpers;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import me.lukemccon.airdrop.PackagesConfig;
import me.lukemccon.airdrop.exceptions.PackageNotFoundException;

public class DropHelper {

	/**
	 * Returns an ArrayList of ItemStacks in the specified package. If the
	 * package is invalid, returns an empty ArrayList
	 * 
	 * @param packageName
	 * @param player
	 * @return
	 * @throws PackageNotFoundException 
	 */
	public static ArrayList<ItemStack> getItemsInPackage(String packageName, Player player) throws PackageNotFoundException {

		ArrayList<ItemStack> itemsToReturn = new ArrayList<ItemStack>();

		if (PackagesConfig.getConfig().contains("packages." + packageName)) {
			int itemNumber = 1;
			while (PackagesConfig.getConfig().contains("packages." + packageName + ".items." + itemNumber)) {
				itemsToReturn.add(
						PackagesConfig.getConfig().getItemStack("packages." + packageName + ".items." + itemNumber));
				itemNumber++;
			}
		} else {
			throw new PackageNotFoundException(packageName);
		}

		return itemsToReturn;

	}

	/**
	 * Checks if there are any blocks within 20 blocks above the player
	 * 
	 * @param loc
	 * @return true if a non-air block is above the player
	 */
	public static boolean checkBlocksAbovePlayer(Location loc) {
		boolean isAbove = true;

		for (int x = 0; x < 20; x++) {

			if (!loc.getBlock().getType().equals(Material.AIR)) {
				isAbove = false;
			}

			loc.add(0, 1.0, 0);
		}

		return isAbove;
	}
	
	/**
	 * Resolves a place to place the package based on the max height at a given x and z
	 * @param player sender
	 * @param world that we are dropping it in
	 * @param x location
	 * @param z location
	 * @return
	 */
	public static Location resolveLocation(Player player, World w, String x, String z) {
		Double xloc = null;
		Double zloc = null;
		Double yloc = null;
		Double ylocPackage = null;
		
		try {
		
		 xloc = (double) Math.round(Double.parseDouble(x));
		 zloc = (double) Math.round(Double.parseDouble(z));
		
		} catch (NumberFormatException e) {
			ChatHandler.sendErrorMessage(player, "You did not provide numbers");
		}
		
		
		Location topblock = new Location(w,xloc,0,zloc);
		yloc = new Double(w.getHighestBlockAt(topblock).getY());
		yloc = (double) Math.round(yloc);
		topblock = new Location(w,xloc,yloc,zloc);
		ylocPackage = yloc + 1;
		Location pkg = new Location(w,xloc,ylocPackage,zloc);
		
		return pkg;
	}
}
