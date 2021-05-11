package me.gregoryw.airdrop;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.FallingBlock;
import org.bukkit.inventory.ItemStack;

import me.gregoryw.airdrop.helpers.CrateList;

public class Crate {

	private Location location;
	private World world;

	private ArrayList<ItemStack> contents;

	private FallingBlock fallingCrate;
	private Block blockChest;

	/**
	 * Construct a new Crate object with a location, world, and ArrayList of
	 * contents
	 * 
	 * @param location
	 * @param world
	 * @param contents
	 */
	public Crate(Location location, World world, ArrayList<ItemStack> contents) {

		this.location = location;
		this.world = world;
		this.contents = contents;

	}

	/**
	 * Drop the crate
	 */
	@SuppressWarnings("deprecation")
	public void dropCrate() {

		fallingCrate = world.spawnFallingBlock(location, Material.OAK_PLANKS, (byte) 0);
		
		CrateList.crateMap.put(fallingCrate, this);

	}

	/**
	 * Spawn chest where the falling block was
	 */
	public void spawnChest() {

		blockChest.setType(Material.CHEST);

		Chest chest = (Chest) blockChest.getState();

		for (ItemStack is : contents) {
			chest.getBlockInventory().addItem(is);
		}

	}
	
	/**
	 * Returns the Crate's fallingCrate owned by this object
	 * @return
	 */
	public FallingBlock getFallingCrate() {
		return fallingCrate;
	}
	
	/**
	 * Sets the Crate's blockChest
	 * @param block
	 */
	public void setChestBlock(Block block) {
		blockChest = block;
	}

}
