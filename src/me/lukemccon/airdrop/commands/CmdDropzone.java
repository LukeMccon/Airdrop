package me.lukemccon.airdrop.commands;

import java.util.ArrayList;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import me.lukemccon.airdrop.Airdrop;
import me.lukemccon.airdrop.Crate;
import me.lukemccon.airdrop.exceptions.PackageNotFoundException;
import me.lukemccon.airdrop.helpers.DropHelper;

public class CmdDropzone implements CommandExecutor {
	
	private Location corner1;
	private Location corner2;
	private World world;
	private int frequencyInSeconds = 10;
	private BukkitTask dropzoneRunnerId;
	
	private Random r = new Random();

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		
		if(sender instanceof Player) {
			Player player = (Player) sender;
			
			switch(args[0]) {
			case "setWorld":
				this.world = player.getWorld();
			case "setCorner1":
				this.corner1 = player.getLocation();
				return true;
			case "setCorner2":
				this.corner2 = player.getLocation();
				return true;
			case "setFrequency":
				try {
					this.frequencyInSeconds = Integer.parseInt(args[1]);
				} catch (NumberFormatException e) {
					player.sendMessage("Not a number");
					return false;
				}
			case "enable":
				if(corner1 == null || corner2 == null) {
					player.sendMessage("One of the corners isn't set");
					return false;
				}
				dropzoneRunnerId = Bukkit.getServer().getScheduler().runTaskTimer(Airdrop.PLUGIN_INSTANCE, new Runnable() {
					@Override
					public void run() {
						int xMax = Math.max(corner1.getBlockX(), corner2.getBlockX());
						int xMin = Math.min(corner1.getBlockX(), corner2.getBlockX());
						int zMax = Math.max(corner1.getBlockZ(), corner2.getBlockZ());
						int zMin = Math.min(corner1.getBlockZ(), corner2.getBlockZ());

						int xLoc = r.nextInt(xMax - xMin) + xMin;
						int zLoc = r.nextInt(zMax - zMin) + zMin;
						Location dropLocation = world.getHighestBlockAt(xLoc, zLoc).getLocation().add(new Vector(0.5, 50, 0.5));
						ArrayList<ItemStack> items;
						try {
							items = DropHelper.getItemsInPackage("starter", null);
						} catch (PackageNotFoundException e) {
							items = new ArrayList<ItemStack>();
						}
						
						Bukkit.getServer().broadcastMessage("Dropping an airdrop at X: " + xLoc + ", Z: " + zLoc);
						
						Crate crate = new Crate(dropLocation, world, items);
						crate.dropCrate();
					}
				}, frequencyInSeconds * 20, frequencyInSeconds*20);
				return true;
			case "disable":
				if (dropzoneRunnerId != null) {
					dropzoneRunnerId.cancel();
					return true;
				}
				return false;
			default:
				player.sendMessage("Invalid, use setCorner1, setCorner2, enable, or disable.");
			}
			
		}
		
		return false;
	}

}
