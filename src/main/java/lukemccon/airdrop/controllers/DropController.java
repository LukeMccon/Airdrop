package lukemccon.airdrop.controllers;

import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import lukemccon.airdrop.exceptions.PackageNotFoundException;
import lukemccon.airdrop.helpers.ChatHandler;
import lukemccon.airdrop.helpers.DropHelper;
import lukemccon.airdrop.packages.PackageManager;
import lukemccon.airdrop.Crate;

public class DropController {
	
	public static boolean onCommand(Player player, String[] args) {
			
		// args[0] will be the kit name
		if(!PackageManager.has(args[0])) {
			ChatHandler.sendErrorMessage(player, "Invalid Package Name");
			return false;
		}
		
		String packageName = args[0];
		ArrayList<ItemStack> items = null;
		try {
			items = DropHelper.getItemsInPackage(packageName, player);
		} catch (PackageNotFoundException e) {
			items = new ArrayList<ItemStack>();
		}
		
		if(args.length == 1) {
			
			World world = player.getWorld();
			Location playerLoc = player.getLocation();
			Location highestLocation = world.getHighestBlockAt(playerLoc.getBlockX(), playerLoc.getBlockZ()).getLocation().add(new Vector(0.5, 0, 0.5));
			
			if (playerLoc.getBlockY() <= highestLocation.getBlockY()) {
				ChatHandler.sendErrorMessage(player, "Must be below open sky for an airdrop!");
				return false;
			}
			
			Crate crate = new Crate(highestLocation.add(new Vector(0, 20, 0)), world, items);
			crate.dropCrate();

			
		} else if (args.length == 2) {
			
			Player dropTarget = Bukkit.getServer().getPlayer(args[1]);
			if (dropTarget == null) {
				ChatHandler.sendErrorMessage(player, "Player " + args[1] + " is not online!");
				return false;
			}
			
			World world = dropTarget.getWorld();
			Location playerLoc = dropTarget.getLocation();
			Location highestLocation = world.getHighestBlockAt(playerLoc.getBlockX(), playerLoc.getBlockZ()).getLocation().add(new Vector(0.5, 0, 0.5));
			
			if (playerLoc.getBlockY() <= highestLocation.getBlockY()) {
				ChatHandler.sendErrorMessage(player, "Target must be below open sky for an airdrop!");
				return false;
			}
			
			Crate crate = new Crate(highestLocation.add(new Vector(0, 20, 0)), world, items);
			crate.dropCrate();
			
		} else if (args.length == 3) {
			
			World world = player.getWorld();
			int x = 0;
			int z = 0;
			
			try {
				x = Integer.parseInt(args[1]);
				z = Integer.parseInt(args[2]);
			} catch (NumberFormatException e) {
				ChatHandler.sendErrorMessage(player, "Invalid x or z location! Please input valid integers.");
				return false;
			}
			
			
			Location highestLocation = world.getHighestBlockAt(x, z).getLocation().add(new Vector(0.5, 20, 0.5));
			
			Crate crate = new Crate(highestLocation, world, items);
			crate.dropCrate();
			
		} else {
			return false;
		}
		
		return true;
		
	}

}
