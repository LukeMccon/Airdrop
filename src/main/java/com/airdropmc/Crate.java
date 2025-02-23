package com.airdropmc;

import java.util.ArrayList;
import java.util.List;

import com.airdropmc.config.ConfigKeys;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.tasks.RenderPackageInitialSpecialEffectTask;
import com.airdropmc.tasks.RenderPackageSpecialEffectTask;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
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

    // Falling state fields
    private Location dropLocation;
    private FallingBlock fallingCrate;
    private ParachuteSystem parachuteSystem;

    // Landed state fields
    private Location landedLocation;

    // Landed state fields
    private Block blockChest;
    private RenderPackageSpecialEffectTask particleEffect;
    private BukkitTask repeatingParticleTask;

    /**
     * Construct a new Crate object with a location, world, and ArrayList of
     * contents
     * 
     * @param location where crate will drop
     * @param world    where it will drop in
     * @param contents of the crate
     */
    public Crate(Location location, World world, List<ItemStack> contents) {
        this.dropLocation = location.clone();
        this.world = world;
        this.contents = new ArrayList<>(contents);
        this.state = State.FALLING;
        this.parachuteSystem = new ParachuteSystem(world);
    }

    /**
     * Drop the crate
     */
    @SuppressWarnings("deprecation")
    public void dropCrate() {
        if (state != State.FALLING) {
            throw new IllegalStateException("Cannot drop a crate that is not in FALLING state");
        }

        fallingCrate = world.spawnFallingBlock(dropLocation, Material.BARREL, (byte) 0);
        parachuteSystem.initialize(dropLocation, fallingCrate);

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
        Barrel barrel = (Barrel) blockChest.getState();

        for (ItemStack is : contents) {
            barrel.getInventory().addItem(is);
        }

        CrateManager.addCrate(barrel.getLocation(), this);

        if (ConfigKeys.shouldShowLandingParticleEffects()) {
            RenderPackageInitialSpecialEffectTask initialParticleEffect = new RenderPackageInitialSpecialEffectTask(
                    this.landedLocation, world);
            initialParticleEffect.runTaskAsynchronously(Airdrop.getPluginInstance());
        }

        if (ConfigKeys.shouldShowContinuousParticleEffects()) {
            setParticleEffect(new RenderPackageSpecialEffectTask(landedLocation, world));
        }
    }

    /**
     * Gets the current particle effect task
     * 
     * @return The particle effect task, or null if none
     */
    public RenderPackageSpecialEffectTask getParticleEffect() {
        return particleEffect;
    }

    /**
     * Sets the particle effect task and stores it for later cleanup
     * 
     * @param effect The particle effect task
     */
    public void setParticleEffect(RenderPackageSpecialEffectTask effect) {
        if (this.repeatingParticleTask != null) {
            stopParticleEffect();
        }
        this.particleEffect = effect;
        if (effect != null) {
            this.repeatingParticleTask = effect.runTaskTimerAsynchronously(Airdrop.getPluginInstance(), 30L, 1L);
        }
    }

    /**
     * Stops the particle effect if one is running
     */
    public void stopParticleEffect() {
        if (repeatingParticleTask != null) {
            repeatingParticleTask.cancel();
            repeatingParticleTask = null;
        }
        particleEffect = null;
    }

    /**
     * Cleans up resources used by this crate
     */
    public void destroy() {
        if (state == State.LANDED) {
            stopParticleEffect();
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

}
