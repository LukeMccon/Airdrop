package com.airdropmc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.airdropmc.config.DropOptions;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.helpers.AirdropLogger;
import com.airdropmc.tasks.RenderFlareTask;
import com.airdropmc.tasks.RenderPackageGlowTask;
import com.airdropmc.tasks.RenderPackageLandedTask;
import com.airdropmc.tasks.RenderPackageSmokeTask;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.FallingBlock;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * Represents a crate that can be dropped from the sky
 * A.K.A an Airdrop
 */
public class Crate {
    public enum State {
        FALLING,
        LANDED
    }

    private final World world;
    private final ArrayList<ItemStack> contents;
    private State state;
    private final DropOptions options;

    // Falling state fields
    private Location dropLocation;
    private FallingBlock fallingCrate;
    private ParachuteSystem parachuteSystem;

    // Landed state fields
    private Location landedLocation;
    private Block blockChest;
    private BukkitTask glowTask;
    private BukkitTask smokeTask;
    private RenderFlareTask flareEffect;
    private RenderPackageGlowTask glowEffect;
    private RenderPackageSmokeTask smokeEffect;
    private volatile boolean opened = false;

    /**
     * Construct a new Crate object with a location, world, and ArrayList of
     * contents
     * 
     * @param location where crate will drop
     * @param world    where it will drop in
     * @param contents of the crate
     */
    public Crate(Location location, World world, List<ItemStack> contents, DropOptions options) {
        this.dropLocation = location.clone();
        this.world = world;
        this.contents = cloneContents(contents);
        this.state = State.FALLING;
        this.options = options;
        this.parachuteSystem = new ParachuteSystem(world, options);
    }

    private static ArrayList<ItemStack> cloneContents(List<ItemStack> contents) {
        ArrayList<ItemStack> clonedContents = new ArrayList<>();
        if (contents == null) {
            return clonedContents;
        }

        for (ItemStack content : contents) {
            if (content == null) {
                continue;
            }
            clonedContents.add(content.clone());
        }
        return clonedContents;
    }

    /**
     * Drop the crate
     */
    public void dropCrate() {
        if (state != State.FALLING) {
            throw new IllegalStateException("Cannot drop a crate that is not in FALLING state");
        }
        Airdrop plugin = getEnabledPlugin();
        if (plugin == null) {
            throw new IllegalStateException("Cannot drop crate while plugin is unavailable");
        }

        // Create flare effect at ground level (drop height blocks below drop location)
        Location groundLocation = dropLocation.clone();
        groundLocation.setY(dropLocation.getY() - options.getDropHeight() + 1);
        if (options.shouldShowFlareEffects()) {
            flareEffect = new RenderFlareTask(groundLocation, world);
            flareEffect.runTaskTimer(plugin, 0L, 1L);
        }
        fallingCrate = world.spawn(dropLocation, FallingBlock.class, fb -> {
            fb.setBlockData(Material.BARREL.createBlockData());
        });
        parachuteSystem.initialize(dropLocation, fallingCrate, plugin);

        CrateManager.addCrate(fallingCrate, this);
    }

    /**
     * Transitions the crate from FALLING to LANDED state
     * 
     * @param block The block where the crate landed
     */
    public void land(Block block) {
        if (state != State.FALLING) {
            throw new IllegalStateException("Cannot land a crate that is not in FALLING state");
        }

        this.blockChest = block;
        this.landedLocation = block.getLocation().clone();
        this.state = State.LANDED;

		// Initialize landed state
		blockChest.setType(Material.BARREL);
		BlockState barrelState = blockChest.getState();
		if (!(barrelState instanceof Barrel barrel)) {
			throw new IllegalStateException("Failed to create barrel at landed location");
		}

		int overflowStackCount = 0;
		for (ItemStack is : contents) {
			Map<Integer, ItemStack> overflow = barrel.getInventory().addItem(is);
			if (overflow.isEmpty()) {
				continue;
			}
			for (ItemStack remaining : overflow.values()) {
				if (remaining == null || remaining.getType().isAir()) {
					continue;
				}
				overflowStackCount++;
				world.dropItemNaturally(this.landedLocation.clone().add(0.5, 0.5, 0.5), remaining);
			}
		}
		if (overflowStackCount > 0) {
			AirdropLogger.warning("Dropped " + overflowStackCount
					+ " overflow item stack(s) at a landed crate because barrel inventory was full");
		}
        CrateManager.addCrate(barrel.getLocation(), this);
        Airdrop plugin = getEnabledPlugin();

        if (plugin != null && options.shouldShowLandingEffects()) {
            RenderPackageLandedTask landedEffect = new RenderPackageLandedTask(
                    this.landedLocation.clone(), world);
            landedEffect.runTask(plugin);
        }

        if (plugin != null && options.shouldShowContinuousEffects()) {
            glowEffect = new RenderPackageGlowTask(landedLocation.clone(), world);
            this.glowTask = glowEffect.runTaskTimer(plugin, 0L, 10L);
        }

        if (plugin != null && options.isSmokeEnabled()) {
            smokeEffect = new RenderPackageSmokeTask(landedLocation.clone(), world, options.getSmokeHeight());
            this.smokeTask = smokeEffect.runTaskTimer(plugin, 0L, 100L);
        }

        // Play landing sound effect
        world.playSound(landedLocation, Sound.ENTITY_PLAYER_LEVELUP, .05f, .05f);

        if (this.flareEffect != null) {
            this.flareEffect.cancel();
        }
    }

    /**
     * Stop particle effects
     */
    public void stopEffects() {
        if (glowTask != null) {
            glowTask.cancel();
        }
        if (smokeTask != null) {
            smokeTask.cancel();
        }
    }

    /**
     * Cleans up resources used by this crate
     */
    public void destroy() {
        if (state == State.FALLING && fallingCrate != null && !fallingCrate.isDead()) {
            fallingCrate.setGravity(true);
            fallingCrate.remove();
        }

        // Cancel parachute system tasks and cleanup entities
        if (parachuteSystem != null) {
            parachuteSystem.cancel();
        }

        // Stop particle effects if landed
        if (state == State.LANDED) {
            stopEffects();
            if (blockChest != null && blockChest.getType() == Material.BARREL) {
                blockChest.setType(Material.AIR);
            } else if (landedLocation != null && landedLocation.getBlock().getType() == Material.BARREL) {
                landedLocation.getBlock().setType(Material.AIR);
            }
        }

        // Cancel flare effect if still running
        if (flareEffect != null && !flareEffect.isCancelled()) {
            flareEffect.cancel();
        }
    }

    /**
     * Returns the Crate's current state
     */
    public State getState() {
        return state;
    }

    /**
     * Returns the Crate's fallingCrate owned by this object
     */
    public FallingBlock getFallingCrate() {
        return fallingCrate;
    }

    /**
     * Gets the current location of the crate
     */
    /**
     * Gets the current location of the crate based on its state
     */
    public Location getLocation() {
        return state == State.FALLING ? dropLocation : landedLocation;
    }

    /**
     * Gets the original drop location of the crate
     */
    public Location getDropLocation() {
        return dropLocation;
    }

    /**
     * Gets the landed location of the crate if it has landed, null otherwise
     */
    public Location getLandedLocation() {
        return state == State.LANDED ? landedLocation : null;
    }

    private Airdrop getEnabledPlugin() {
        Airdrop plugin = Airdrop.getPluginInstance();
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        return plugin;
    }

    public boolean getOpened() {
        return opened;
    }

    public void setOpened(boolean opened) {
        this.opened = opened;
        if (opened) {
            this.stopEffects();
        }
    }

}
