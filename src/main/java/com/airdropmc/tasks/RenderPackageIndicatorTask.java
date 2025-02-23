package com.airdropmc.tasks;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.block.Barrel;
import org.bukkit.util.Vector;
import org.bukkit.block.Barrel;

public class RenderPackageIndicatorTask extends BukkitRunnable {
    private Location location;
    private World world;
    private Barrel barrel;

    public RenderPackageIndicatorTask(Location location, World world, Barrel barrel) {
        this.location = location.getBlock().getLocation().add(0.5, 1.0, 0.5);
        this.world = world;
    }

    @Override
    public void run() {

        Vector offset = new Vector(0, 1, 0);
        for(int i =0; i < 20; i ++) {
//            Location newLocation = location.add(offset);
            Location newLocation = location.clone().add(offset.clone().multiply(i));
            world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, newLocation, 0, 0,0,0,0,null ,true );
        }
//        stopEffect();
    }

    public void stopEffect() {
        System.out.println("Cancelling this now.....");
        this.cancel();
    }

}