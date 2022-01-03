package lukemccon.airdrop;

import java.util.ArrayList;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Slime;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import lukemccon.airdrop.helpers.CrateList;

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

		// Create a tiny invisible slime to hold leashes for the crate
		Slime parachuteLeash = (Slime) world.spawnEntity(location.add(new Vector(0, 1, 0)), EntityType.SLIME);
		parachuteLeash.setAI(false);
		parachuteLeash.setSize(1);
		parachuteLeash.setInvisible(true);
		parachuteLeash.setInvulnerable(true);

		fallingCrate = world.spawnFallingBlock(location, Material.BARREL, (byte) 0);
		
		// Create a bunch of chicken parachuters and attach them to the slime
		ArrayList<Chicken> chickenParachutes = new ArrayList<Chicken>();
		for(int i = 0; i < 5; i++) {
			Chicken chicken = (Chicken) world.spawnEntity(location.add(new Vector(Math.random()*0.25, 1, Math.random()*0.25)), EntityType.CHICKEN);
			chicken.setInvulnerable(true);
			chicken.setLeashHolder(parachuteLeash);
			chickenParachutes.add(chicken);
		}
					
		// Add the tiny slime as a passenger on the fallingCrate, this will make it looks like the crate is holding the chicken leashes
		fallingCrate.addPassenger(parachuteLeash);
		
		fallingCrate.setGravity(false);
		
		Bukkit.getServer().getScheduler().runTaskTimer(Airdrop.PLUGIN_INSTANCE, new Runnable() {
			
			@Override
			public void run() {
				// When the fallingCrate dies, have the chickens fly away for 3 seconds then despawn
				if(fallingCrate.isDead()) {
					for(Chicken c : chickenParachutes) {
						c.setLeashHolder(null);
						double xVel = Math.random() < 0.5 ? Math.random()*0.5*-1 : Math.random()*0.5;
						double zVel = Math.random() < 0.5 ? Math.random()*0.5*-1 : Math.random()*0.5;
						c.setVelocity(new Vector(xVel, 0.5, zVel));
						Bukkit.getServer().getScheduler().runTaskLater(Airdrop.PLUGIN_INSTANCE, new Runnable() {
							@Override
							public void run() {
								c.remove();
								return;
							}
						}, 60);
					}
					parachuteLeash.remove();
					return;
				}
				
				// Play some smoke effects
				fallingCrate.getWorld().playEffect(fallingCrate.getLocation().add(new Vector(0, 1, 0)), Effect.SMOKE, 0);
				fallingCrate.getWorld().playEffect(fallingCrate.getLocation().add(new Vector(0, 1, 0)), Effect.SMOKE, 0);
				fallingCrate.getWorld().playEffect(fallingCrate.getLocation().add(new Vector(0, 1, 0)), Effect.SMOKE, 0);
				
				// Set the falling vector repeatedly to ensure the crate doesn't slow down due to no gravity
				fallingCrate.setVelocity(new Vector(0, -0.3, 0));

			}
			
		}, 0, 2);
		
		CrateList.crateMap.put(fallingCrate, this);

	}

	/**
	 * Spawn chest where the falling block was
	 */
	public void spawnChest() {

		blockChest.setType(Material.BARREL);
		
		Barrel barrel = (Barrel) blockChest.getState();

		for (ItemStack is : contents) {
			barrel.getInventory().addItem(is);
		}
		
		CrateList.barrelList.add(barrel.getLocation());

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
