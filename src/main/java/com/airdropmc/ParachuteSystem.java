package com.airdropmc;

import java.util.ArrayList;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Slime;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.util.Vector;

import com.airdropmc.config.DropOptions;

/**
 * Manages the parachute system for airdrops, including chicken parachutes and
 * effects
 */
public class ParachuteSystem {
    private final World world;
    private final ArrayList<Chicken> chickenParachutes;
    private final DropOptions options;
    private Slime parachuteLeash;
    private FallingBlock fallingCrate;

    public ParachuteSystem(World world, DropOptions options) {
        this.world = world;
        this.chickenParachutes = new ArrayList<>();
        this.options = options;
    }

    /**
     * Initializes the parachute system for a falling crate
     * 
     * @param dropLocation The location where the crate is being dropped
     * @param fallingCrate The falling block representing the crate
     */
    public void initialize(Location dropLocation, FallingBlock fallingCrate) {
        this.fallingCrate = fallingCrate;

        // Create a tiny invisible slime to hold leashes for the crate
        parachuteLeash = (Slime) world.spawnEntity(dropLocation.add(new Vector(0, 1, 0)), EntityType.SLIME);
        parachuteLeash.setAI(false);
        parachuteLeash.setSize(1);
        parachuteLeash.setInvisible(true);
        parachuteLeash.setInvulnerable(true);

        // Create chicken parachuters and attach them to the slime
        for (int i = 0; i < options.getChickenCount(); i++) {
            Chicken chicken = (Chicken) world.spawnEntity(
                    dropLocation.add(new Vector(Math.random() * 0.25, 1, Math.random() * 0.25)), EntityType.CHICKEN);
            chicken.setInvulnerable(true);
            chicken.setLeashHolder(parachuteLeash);
            chickenParachutes.add(chicken);
        }

        // Add the slime as a passenger on the fallingCrate
        fallingCrate.addPassenger(parachuteLeash);
        fallingCrate.setGravity(false);

        startParachuteTask();
    }

    private void startParachuteTask() {
        Bukkit.getServer().getScheduler().runTaskTimer(Airdrop.getPluginInstance(), new Runnable() {
            @Override
            public void run() {
                if (fallingCrate.isDead()) {
                    releaseParachutes();
                    return;
                }

                // Play smoke effects
                Location effectLoc = fallingCrate.getLocation().add(new Vector(0, 1, 0));
                for (int i = 0; i < 3; i++) {
                    fallingCrate.getWorld().playEffect(effectLoc, Effect.SMOKE, 0);
                }

                // Set the falling velocity equal to the speed in a downward direction
                double fallingVelocity = -ParachuteSystem.this.options.getFallingSpeed();

                // Maintain falling velocity
                fallingCrate.setVelocity(new Vector(0, fallingVelocity, 0));
            }
        }, 0, 2);
    }

    /**
     * Releases the parachutes and cleans up the system
     */
    private void releaseParachutes() {
        // Release chickens and set their velocities
        for (Chicken chicken : chickenParachutes) {
            chicken.setLeashHolder(null);
            double xVel = Math.random() < 0.5 ? Math.random() * 0.5 * -1 : Math.random() * 0.5;
            double zVel = Math.random() < 0.5 ? Math.random() * 0.5 * -1 : Math.random() * 0.5;
            chicken.setVelocity(new Vector(xVel, .5, zVel));
        }

        // Schedule cleanup after the chickens have had time to float up
        Bukkit.getServer().getScheduler().runTaskLater(Airdrop.getPluginInstance(), () -> {
            for (Chicken chicken : chickenParachutes) {
                chicken.remove();
            }
            chickenParachutes.clear();
            parachuteLeash.remove();
        }, 60);
    }
}
