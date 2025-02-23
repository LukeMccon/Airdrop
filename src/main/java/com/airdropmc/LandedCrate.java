package com.airdropmc;

import java.util.ArrayList;
import java.util.List;

import com.airdropmc.config.ConfigKeys;
import com.airdropmc.helpers.CrateList;
import com.airdropmc.tasks.RenderPackageInitialSpecialEffectTask;
import com.airdropmc.tasks.RenderPackageIndicatorTask;
import com.airdropmc.tasks.RenderPackageSpecialEffectTask;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public class LandedCrate {
    private final Block blockChest;
    private RenderPackageIndicatorTask particleEffect;
    private BukkitTask repeatingParticleTask;
    private final ArrayList<ItemStack> contents;
    private Location loc;
    private World world;

    public LandedCrate(Block block, List<ItemStack> contents) {
        this.blockChest = block;
        this.contents = new ArrayList<>(contents);
        this.loc = block.getLocation();
        this.world = block.getWorld();
        init();
    }

    private void init() {
        blockChest.setType(Material.BARREL);
        Barrel barrel = (Barrel) blockChest.getState();

        for (ItemStack is : contents) {
            barrel.getInventory().addItem(is);
        }

        CrateList.addLandedCrate(barrel.getLocation(), this);

        if (ConfigKeys.shouldShowLandingParticleEffects()) {
            RenderPackageInitialSpecialEffectTask intitalParticleEffect = new RenderPackageInitialSpecialEffectTask(loc,
                    world);
            intitalParticleEffect.runTaskAsynchronously(Airdrop.getPluginInstance());
        }

        if (ConfigKeys.shouldShowContinuousParticleEffects()) {
            this.setParticleEffect(new RenderPackageIndicatorTask(loc, world));
//            RenderPackageIndicatorTask particleEffect = new RenderPackageIndicatorTask(loc, world, barrel);
//            particleEffect.runTaskTimer(Airdrop.getPluginInstance(), 0L, 100L);
        }
    }

    /**
     * Gets the location of the landed crate
     * 
     * @return Location of the crate
     */
    public Location getLocation() {
        return blockChest.getLocation();
    }

    /**
     * Gets the current particle effect task
     * 
     * @return The particle effect task, or null if none
     */
    public RenderPackageIndicatorTask getParticleEffect() {
        return particleEffect;
    }

    /**
     * Sets the particle effect task and stores it for later cleanup
     * 
     * @param effect The particle effect task
     */
    // Indicator support
    public void setParticleEffect(RenderPackageIndicatorTask effect) {
        if (this.repeatingParticleTask != null) {
            stopParticleEffect();
        }
        this.particleEffect = effect;
        if (effect != null) {
//            this.repeatingParticleTask = effect.runTaskTimerAsynchronously(Airdrop.getPluginInstance(), 30L, 1L);
            this.repeatingParticleTask = effect.runTaskTimer(Airdrop.getPluginInstance(), 0L, 100L);
        }
    }

    /**
     * Stops the particle effect if one is running
     */
    // Indicator
    public void stopParticleEffect() {
        Bukkit.getLogger().info("[Airdrop] Attempting to stop particle effect");
        if (repeatingParticleTask != null) {
            Bukkit.getLogger().info("[Airdrop] Found particle task, cancelling it");
            repeatingParticleTask.cancel();
            repeatingParticleTask = null;
        } else {
            Bukkit.getLogger().warning("[Airdrop] No particle task found to cancel");
        }
        particleEffect = null;
    }

    public void destroy() {
        stopParticleEffect();
    }
}
