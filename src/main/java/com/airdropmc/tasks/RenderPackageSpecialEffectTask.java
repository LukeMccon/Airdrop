package com.airdropmc.tasks;

import org.bukkit.util.Vector;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

public class RenderPackageSpecialEffectTask extends BukkitRunnable {

    private Location location;
    private World world;
    private boolean shouldContinue = true;
    private int ticksElapsed = 0;
    private static final int MAX_TICKS = 100; // 5 seconds (20 ticks per second)

    public RenderPackageSpecialEffectTask(Location location) {
        Vector reposition = new Vector(0, .5, 0);
        this.location = location.add(reposition);
        this.world = location.getWorld();
    }

    @Override
    public void run() {
        if (!shouldContinue || ticksElapsed >= MAX_TICKS) {
            this.cancel();
            return;
        }

        // Create main spiral effect
        double radius = 0.8;
        double y = Math.sin(ticksElapsed * 0.2) * 0.2;
        double x = Math.cos(ticksElapsed * 0.2) * radius;
        double z = Math.sin(ticksElapsed * 0.2) * radius;

        Location particleLoc = location.clone().add(x, y, z);

        // Main glowing particles
        world.spawnParticle(Particle.END_ROD, particleLoc, 2, 0.02, 0.02, 0.02, 0.01, null, false);

        // Ambient glow effect
        if (ticksElapsed % 5 == 0) { // Every 5 ticks
            world.spawnParticle(Particle.GLOW, location, 3, 0.3, 0.3, 0.3, 0, null, false);
        }

        ticksElapsed++;
    }

    public void stopEffect() {
        this.shouldContinue = false;
    }
}
