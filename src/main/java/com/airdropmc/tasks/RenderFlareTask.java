package com.airdropmc.tasks;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

public class RenderFlareTask extends BukkitRunnable {

    private Location location;
    private World world;
    private boolean shouldContinue = true;
    private int ticksElapsed = 0;
    private static final int MAX_TICKS = 200; // 10 seconds

    // Flare effect constants
    private static final int PARTICLES_PER_BURST = 3;
    private static final double MAX_OFFSET = 0.2;
    private static final float SOUND_VOLUME = 0.8f;
    private static final float SOUND_PITCH = 1.2f;
    private static final int SMOKE_PARTICLES = 4;

    /**
     * Creates a flare effect to mark where a package will be received
     * 
     * @param location location where the package will land
     * @param world    world the package will be in
     */
    public RenderFlareTask(Location location, World world) {
        this.location = location.getBlock().getLocation().add(0.5, 0.0, 0.5);
        this.world = world;
    }

    @Override
    public void run() {
        if (!shouldContinue || ticksElapsed >= MAX_TICKS) {
            this.cancel();
            return;
        }

        // Create main red flare effect
        for (int i = 0; i < PARTICLES_PER_BURST; i++) {
            // Random offset for natural look
            double xOffset = (Math.random() - 0.5) * MAX_OFFSET;
            double zOffset = (Math.random() - 0.5) * MAX_OFFSET;

            // Intense red particles
            Location flareLoc = location.clone().add(xOffset, 0.1, zOffset);
            world.spawnParticle(Particle.DUST, flareLoc, 0, 0, 0, 0, 0,
                    new Particle.DustOptions(org.bukkit.Color.RED, 2.0f));

            // Add some flame particles for extra effect
            world.spawnParticle(Particle.FLAME, flareLoc, 1, 0.05, 0.05, 0.05, 0.01);
        }

        // Enhanced smoke effect with rising motion
        if (ticksElapsed % 2 == 0) {
            // Ground level smoke
            Location smokeLoc = location.clone().add(0, 0.2, 0);
            world.spawnParticle(Particle.SMOKE, smokeLoc, SMOKE_PARTICLES, 0.1, 0, 0.1, 0.02);

            // Rising smoke column
            world.spawnParticle(Particle.LARGE_SMOKE, smokeLoc, 2, 0.05, 0.1, 0.05, 0.05);

            // Play sizzling sound (alternating between different sounds for variety)
            if (ticksElapsed % 4 == 0) {
                world.playSound(location, Sound.BLOCK_FIRE_AMBIENT, SOUND_VOLUME, SOUND_PITCH);
            } else if (ticksElapsed % 6 == 0) {
                world.playSound(location, Sound.BLOCK_FIRE_EXTINGUISH, SOUND_VOLUME * 0.3f, SOUND_PITCH * 1.5f);
            }
        }

        // Occasional spark effect
        if (ticksElapsed % 5 == 0) {
            Location sparkLoc = location.clone().add(0, 0.15, 0);
            world.spawnParticle(Particle.LAVA, sparkLoc, 1, 0.1, 0, 0.1, 0);
        }

        ticksElapsed++;
    }

    public void stopEffect() {
        this.shouldContinue = false;
        this.cancel();
    }
}
