package com.airdropmc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

import com.airdropmc.config.DropOptions;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.helpers.AirdropLogger;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLocationKey;
import com.airdropmc.tasks.RenderFlareTask;
import com.airdropmc.tasks.RenderPackageGlowTask;
import com.airdropmc.tasks.RenderPackageLandedTask;
import com.airdropmc.tasks.RenderPackageSmokeTask;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
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
    private final DropOptions options;
	private final DropAdmissionController.Lease lease;

    // Falling state fields
    private Location dropLocation;
    private FallingBlock fallingCrate;
    private ParachuteSystem parachuteSystem;

    // Landed state fields
    private Location landedLocation;
    private Block blockChest;
    private BukkitTask glowTask;
    private BukkitTask smokeTask;
	private BukkitTask landingEffectTask;
	private BukkitTask expiryTask;
    private RenderFlareTask flareEffect;
    private RenderPackageGlowTask glowEffect;
    private RenderPackageSmokeTask smokeEffect;
    private volatile boolean opened = false;
	private boolean destroyed;

    /**
     * Construct a new Crate object with a location, world, and ArrayList of
     * contents
     * 
     * @param location where crate will drop
     * @param world    where it will drop in
     * @param contents of the crate
     */
	public Crate(Location location, World world, List<ItemStack> contents, DropOptions options,
			DropAdmissionController.Lease lease) {
        this.dropLocation = location.clone();
        this.world = world;
        this.contents = cloneContents(contents);
        this.state = State.FALLING;
        this.options = options;
		this.lease = Objects.requireNonNull(lease, "lease");
        this.parachuteSystem = new ParachuteSystem(world, options);
    }

    private static ArrayList<ItemStack> cloneContents(List<ItemStack> contents) {
        ArrayList<ItemStack> clonedContents = new ArrayList<>();
        if (contents == null) {
            return clonedContents;
        }

        for (ItemStack content : contents) {
            if (content == null) {
                continue;
            }
            clonedContents.add(content.clone());
        }
        return clonedContents;
    }

    /**
     * Drop the crate
     */
    public void dropCrate() {
        if (state != State.FALLING) {
            throw new IllegalStateException("Cannot drop a crate that is not in FALLING state");
        }
        Airdrop plugin = getEnabledPlugin();
        if (plugin == null) {
            throw new IllegalStateException("Cannot drop crate while plugin is unavailable");
        }

        // Create flare effect at ground level (drop height blocks below drop location)
        Location groundLocation = dropLocation.clone();
        groundLocation.setY(dropLocation.getY() - options.getDropHeight() + 1);
        if (options.shouldShowFlareEffects()) {
            flareEffect = new RenderFlareTask(groundLocation, world);
            flareEffect.runTaskTimer(plugin, 0L, 1L);
        }
        fallingCrate = world.spawn(dropLocation, FallingBlock.class, fb -> {
            fb.setBlockData(Material.BARREL.createBlockData());
        });
        parachuteSystem.initialize(dropLocation, fallingCrate, plugin);

		if (!CrateManager.addCrate(fallingCrate, this)) {
			throw new IllegalStateException("Falling crate entity is already tracked");
		}
    }

    /**
     * Transitions the crate from FALLING to LANDED state
     * 
     * @param block The block where the crate landed
     */
	public synchronized void land(Block block) {
		try {
			if (destroyed || state != State.FALLING) {
				throw new IllegalStateException("Cannot land a crate that is not active and falling");
			}
			if (block == null) {
				throw new IllegalArgumentException("Landing block is required");
			}
			Location candidate = block.getLocation().clone();
			DropLocationKey actualKey = DropLocationKey.from(candidate);
			if (!lease.owns(actualKey)) {
				throw new IllegalStateException("Landed block does not match the reserved location");
			}
			Airdrop plugin = getEnabledPlugin();
			if (plugin == null) {
				throw new IllegalStateException("Cannot land crate while plugin is unavailable");
			}
			if (!CrateManager.addCrate(candidate, this)) {
				throw new IllegalStateException("Another crate already owns the landed location");
			}

			this.blockChest = block;
			this.landedLocation = candidate;
			this.state = State.LANDED;
			blockChest.setType(Material.BARREL);
			BlockState barrelState = blockChest.getState();
			if (!(barrelState instanceof Barrel barrel)) {
				throw new IllegalStateException("Failed to create barrel at landed location");
			}
			insertContents(barrel);
			lease.markLanded();
			scheduleExpiry(plugin);
			startLandedEffects(plugin);
			world.playSound(landedLocation, Sound.ENTITY_PLAYER_LEVELUP, .05f, .05f);
			if (flareEffect != null) {
				flareEffect.cancel();
				flareEffect = null;
			}
		} catch (RuntimeException failure) {
			CrateManager.removeCrateAndDestroy(this);
			throw failure;
		}
	}

	private void insertContents(Barrel barrel) {
		int overflowStackCount = 0;
		for (ItemStack item : contents) {
			Map<Integer, ItemStack> overflow = barrel.getInventory().addItem(item);
			for (ItemStack remaining : overflow.values()) {
				if (remaining == null || remaining.getType().isAir()) {
					continue;
				}
				overflowStackCount++;
				world.dropItemNaturally(landedLocation.clone().add(0.5, 0.5, 0.5), remaining);
			}
		}
		if (overflowStackCount > 0) {
			AirdropLogger.warning("Dropped " + overflowStackCount
					+ " overflow item stack(s) at a landed crate because barrel inventory was full");
		}
	}

	private void scheduleExpiry(Airdrop plugin) {
		long ticks = com.airdropmc.config.ConfigKeys.getDropLimitSettings().landedLifetimeTicks();
		expiryTask = org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
			synchronized (Crate.this) {
				expiryTask = null;
			}
			CrateManager.removeCrateAndDestroy(this);
		}, ticks);
	}

	private void startLandedEffects(Airdrop plugin) {
		if (options.shouldShowLandingEffects()) {
			RenderPackageLandedTask landedEffect = new RenderPackageLandedTask(landedLocation.clone(), world);
			landingEffectTask = landedEffect.runTask(plugin);
		}
		if (options.shouldShowContinuousEffects()) {
			glowEffect = new RenderPackageGlowTask(landedLocation.clone(), world);
			glowTask = glowEffect.runTaskTimer(plugin, 0L, 10L);
		}
		if (options.isSmokeEnabled()) {
			smokeEffect = new RenderPackageSmokeTask(landedLocation.clone(), world, options.getSmokeHeight());
			smokeTask = smokeEffect.runTaskTimer(plugin, 0L, 100L);
		}
	}

    /**
     * Stop particle effects
     */
	public synchronized void stopEffects() {
		cleanupResource("glow task", () -> {
			if (glowTask != null && !glowTask.isCancelled()) {
				glowTask.cancel();
			}
		});
		cleanupResource("smoke task", () -> {
			if (smokeTask != null && !smokeTask.isCancelled()) {
				smokeTask.cancel();
			}
		});
		glowTask = null;
		smokeTask = null;
    }

    /**
     * Cleans up resources used by this crate
     */
	public synchronized void destroy() {
		if (destroyed) {
			return;
		}
		destroyed = true;
		cleanupResource("falling crate gravity", () -> {
			if (state == State.FALLING && fallingCrate != null && !fallingCrate.isDead()) {
				fallingCrate.setGravity(true);
			}
		});
		cleanupResource("falling crate entity", () -> {
			if (state == State.FALLING && fallingCrate != null && !fallingCrate.isDead()) {
				fallingCrate.remove();
			}
		});
		cleanupResource("parachute system", () -> {
			if (parachuteSystem != null) {
				parachuteSystem.cancel();
			}
		});
		stopEffects();
		cleanupResource("landed expiry", () -> {
			if (expiryTask != null && !expiryTask.isCancelled()) {
				expiryTask.cancel();
			}
		});
		expiryTask = null;
		cleanupResource("landing effect", () -> {
			if (landingEffectTask != null && !landingEffectTask.isCancelled()) {
				landingEffectTask.cancel();
			}
		});
		landingEffectTask = null;
		cleanupResource("flare effect", () -> {
			if (flareEffect != null && !flareEffect.isCancelled()) {
				flareEffect.cancel();
			}
		});
		cleanupResource("landed barrel", () -> {
			if (state == State.LANDED && blockChest != null && blockChest.getType() == Material.BARREL) {
				blockChest.setType(Material.AIR);
			} else if (state == State.LANDED && landedLocation != null
					&& landedLocation.getBlock().getType() == Material.BARREL) {
				landedLocation.getBlock().setType(Material.AIR);
			}
		});
		lease.close();
    }

	private void cleanupResource(String resource, Runnable cleanup) {
		try {
			cleanup.run();
		} catch (RuntimeException failure) {
			try {
				AirdropLogger.log(Level.WARNING, "Failed to clean up crate " + resource, failure);
			} catch (RuntimeException loggingFailure) {
				failure.addSuppressed(loggingFailure);
			}
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

    private Airdrop getEnabledPlugin() {
        Airdrop plugin = Airdrop.getPluginInstance();
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        return plugin;
    }

    public boolean getOpened() {
        return opened;
    }

    public void setOpened(boolean opened) {
        this.opened = opened;
        if (opened) {
            this.stopEffects();
        }
    }

}
