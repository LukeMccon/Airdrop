package com.airdropmc.tasks;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

public class RenderPackageSpecialEffectTask extends BukkitRunnable {

    private Location location;
    private World world;
    private boolean shouldContinue = true;
    private int ticksElapsed = 0;
    private static final int MAX_TICKS = 72000; // 1 hour
    private static final int AMBIENT_PARTICLE_INTERVAL = 5;
    private static final int AMBIENT_PARTICLE_COUNT = 3;
    private static final double AMBIENT_PARTICLE_SPREAD = 0.3;
    private static final double AMBIENT_PARTICLE_SPEED = 0.0;

    /**
     * Renders a single package's special effects once
     * 
     * @param location location of the block the package is on
     * @param world    world the package is in
     */
    public RenderPackageSpecialEffectTask(Location location, World world) {
        // Center the effects in the block
        this.location = location.getBlock().getLocation().add(0.5, 1.0, 0.5);
        this.world = world;
    }

    @Override
    public void run() {
        if (!shouldContinue || ticksElapsed >= MAX_TICKS) {
            this.cancel();
            return;
        }

        // Ambient glow effect
        if (ticksElapsed % AMBIENT_PARTICLE_INTERVAL == 0) {
            world.spawnParticle(Particle.GLOW, location, AMBIENT_PARTICLE_COUNT,
                    AMBIENT_PARTICLE_SPREAD, AMBIENT_PARTICLE_SPREAD, AMBIENT_PARTICLE_SPREAD, AMBIENT_PARTICLE_SPEED,
                    null, false);
        }

        ticksElapsed++;
    }

    public void stopEffect() {
        this.shouldContinue = false;
        this.cancel(); // Explicitly cancel the BukkitRunnable task
    }
}
