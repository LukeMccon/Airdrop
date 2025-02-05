package com.airdropmc.tasks;

import org.bukkit.util.Vector;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

public class RenderPackageInitialSpecialEffectTask extends BukkitRunnable {

    private Location location;
    private World world;
    private boolean shouldContinue = true;
    private int ticksElapsed = 0;
    private static final int MAX_TICKS = 20; // 1 second (20 ticks per second)
    private static final double VERTICAL_OFFSET = 0.5;
    private static final double BURST_RADIUS = 1.5;
    private static final int GLOW_PARTICLE_COUNT = 15;
    private static final double GLOW_PARTICLE_SPREAD = 0.3;
    private static final double GLOW_PARTICLE_SPEED = 0.05;
    private static final int WAVE_PARTICLE_COUNT = 20;
    private static final double WAVE_HEIGHT = 0.1;

    public RenderPackageInitialSpecialEffectTask(Location location, World world) {
        // Make the effects appear midway in the block
        Vector reposition = new Vector(0.5, VERTICAL_OFFSET, 0.5);
        this.location = location.add(reposition);
        this.world = world;
    }

    @Override
    public void run() {
        if (!shouldContinue || ticksElapsed >= MAX_TICKS) {
            this.cancel();
            return;
        }

        // Glowing particles rising effect
        double progress = (double) ticksElapsed / MAX_TICKS;
        double height = progress * 2.0; // Rise up to 2 blocks

        world.spawnParticle(Particle.GLOW,
                location.clone().add(0, height, 0),
                GLOW_PARTICLE_COUNT,
                GLOW_PARTICLE_SPREAD, 0.1, GLOW_PARTICLE_SPREAD,
                GLOW_PARTICLE_SPEED);

        // Ground wave effect
        for (int i = 0; i < WAVE_PARTICLE_COUNT; i++) {
            double angle = (2 * Math.PI * i) / WAVE_PARTICLE_COUNT;
            double radius = BURST_RADIUS * (1 - progress); // Shrinking radius
            double x = Math.cos(angle) * radius;
            double y = Math.sin(ticksElapsed * 0.5) * WAVE_HEIGHT;
            double z = Math.sin(angle) * radius;

            Location waveLoc = location.clone().add(x, y, z);
            world.spawnParticle(Particle.END_ROD, waveLoc, 1,
                    0, 0, 0, 0);
        }

        ticksElapsed++;
    }

    public void setContinue(boolean shouldContinue) {
        this.shouldContinue = shouldContinue;
    }
}
