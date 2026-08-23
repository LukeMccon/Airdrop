package com.airdropmc;

import java.util.ArrayList;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Slime;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitTask;

import com.airdropmc.config.DropOptions;
import com.airdropmc.helpers.AirdropLogger;

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
    private BukkitTask parachuteTask;
    private BukkitTask delayedCleanupTask;
    private Airdrop plugin;
    private boolean parachutesReleased;

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
    public void initialize(Location dropLocation, FallingBlock fallingCrate, Airdrop plugin) {
        this.fallingCrate = fallingCrate;
        this.plugin = plugin;
        this.parachutesReleased = false;
        cancelDelayedCleanupTask();

        // Create a tiny invisible slime to hold leashes for the crate
        Location leashLocation = dropLocation.clone().add(new Vector(0, 1, 0));
        parachuteLeash = (Slime) world.spawnEntity(leashLocation, EntityType.SLIME);
        parachuteLeash.setAI(false);
        parachuteLeash.setSize(1);
        parachuteLeash.setInvisible(true);
        parachuteLeash.setInvulnerable(true);

        // Create chicken parachuters and attach them to the slime
        for (int i = 0; i < options.getChickenCount(); i++) {
            Location chickenLocation = dropLocation.clone()
                    .add(new Vector(Math.random() * 0.25, 2 + i, Math.random() * 0.25));
            Chicken chicken = (Chicken) world.spawnEntity(
                    chickenLocation, EntityType.CHICKEN);
			chickenParachutes.add(chicken);
            chicken.setInvulnerable(true);
            chicken.setLeashHolder(parachuteLeash);
        }

        // Add the slime as a passenger on the fallingCrate
        fallingCrate.addPassenger(parachuteLeash);
        fallingCrate.setGravity(false);

        startParachuteTask();
    }

    private void startParachuteTask() {
        if (plugin == null || !plugin.isEnabled()) {
            return;
        }
        parachuteTask = Bukkit.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (fallingCrate == null || fallingCrate.isDead()) {
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
     * Releases the parachutes and cleans up the system.
     * Called repeatedly by the parachute task every 2 ticks after the crate lands.
     * Do NOT cancel the parachute task here — cancelling from within run() breaks
     * chicken release. The task keeps running to continuously boost chickens upward
     * and is cleaned up by the delayed cleanup task or cancel().
     */
    private void releaseParachutes() {
        // Apply velocity to chickens on every call — continuous boosting makes
        // them visibly fly up and away (single application is too weak)
        for (Chicken chicken : chickenParachutes) {
            if (chicken == null || chicken.isDead()) {
                continue;
            }
            chicken.setLeashHolder(null);
            double xVel = Math.random() < 0.5 ? Math.random() * 0.5 * -1 : Math.random() * 0.5;
            double zVel = Math.random() < 0.5 ? Math.random() * 0.5 * -1 : Math.random() * 0.5;
            chicken.setVelocity(new Vector(xVel, .5, zVel));
        }

        // Schedule cleanup only once
        if (parachutesReleased) {
            return;
        }
        parachutesReleased = true;

        if (plugin == null || !plugin.isEnabled()) {
            cleanupParachuteEntities();
            return;
        }
        delayedCleanupTask = Bukkit.getServer().getScheduler().runTaskLater(plugin, () -> {
            delayedCleanupTask = null;
            cancelParachuteTask();
            cleanupParachuteEntities();
        }, 60);
    }

    /**
     * Cancels all tasks and immediately cleans up entities.
     * Call this when the crate is destroyed or the plugin is disabled.
     */
    public void cancel() {
        parachutesReleased = true;
		cancelParachuteTask();
        cancelDelayedCleanupTask();
        // Immediately remove all entities
        cleanupParachuteEntities();
    }

	private void cleanupParachuteEntities() {
		ArrayList<Chicken> retainedChickens = new ArrayList<>();
		for (Chicken chicken : chickenParachutes) {
			if (chicken != null && !cleanupResource("chicken parachute", () -> {
				if (chicken.isValid() && !chicken.isDead()) {
					chicken.remove();
				}
			})) {
				retainedChickens.add(chicken);
			}
		}
		chickenParachutes.clear();
		chickenParachutes.addAll(retainedChickens);

		Slime leash = parachuteLeash;
		if (leash == null || cleanupResource("parachute leash", () -> {
			if (leash.isValid() && !leash.isDead()) {
				leash.remove();
			}
		})) {
			parachuteLeash = null;
		}
	}

    private void cancelParachuteTask() {
		BukkitTask task = parachuteTask;
		if (task == null || cleanupResource("parachute task", () -> {
			if (!task.isCancelled()) {
				task.cancel();
			}
		})) {
			parachuteTask = null;
		}
    }

    private void cancelDelayedCleanupTask() {
		BukkitTask task = delayedCleanupTask;
		if (task == null || cleanupResource("delayed parachute cleanup task", () -> {
			if (!task.isCancelled()) {
				task.cancel();
			}
		})) {
			delayedCleanupTask = null;
		}
    }

	private boolean cleanupResource(String resource, Runnable cleanup) {
		try {
			cleanup.run();
			return true;
		} catch (RuntimeException failure) {
			try {
				AirdropLogger.log(Level.WARNING, "Failed to clean up " + resource, failure);
			} catch (RuntimeException loggingFailure) {
				failure.addSuppressed(loggingFailure);
			}
			return false;
		}
	}
}
