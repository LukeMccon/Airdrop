package com.airdropmc.tasks;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

public class RenderPackageGlowTask extends BukkitRunnable {

    private Location location;
    private World world;
    private static final int AMBIENT_PARTICLE_COUNT = 3;
    private static final double AMBIENT_PARTICLE_SPREAD = 0.3;
    private static final double AMBIENT_PARTICLE_SPEED = 0.0;

    /**
     * Renders a single package's special effects once
     * 
     * @param location location of the block the package is on
     * @param world    world the package is in
     */
    public RenderPackageGlowTask(Location location, World world) {
        // Center the effects in the block
        this.location = location.getBlock().getLocation().add(0.5, 1.0, 0.5);
        this.world = world;
    }

    @Override
    public void run() {
        world.spawnParticle(Particle.GLOW, location, AMBIENT_PARTICLE_COUNT,
                AMBIENT_PARTICLE_SPREAD, AMBIENT_PARTICLE_SPREAD, AMBIENT_PARTICLE_SPREAD, AMBIENT_PARTICLE_SPEED,
                null, false);
    }

    public void stopEffect() {
        this.cancel(); // Explicitly cancel the BukkitRunnable task
    }
}
