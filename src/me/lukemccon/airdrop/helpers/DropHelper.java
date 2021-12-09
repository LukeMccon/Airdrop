package me.lukemccon.airdrop.helpers;

import java.util.ArrayList;

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

}
