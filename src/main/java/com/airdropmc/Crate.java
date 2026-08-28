package com.airdropmc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.airdropmc.config.ConfigKeys;
import com.airdropmc.config.DropOptions;
import com.airdropmc.helpers.AirdropLogger;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLocationKey;
import com.airdropmc.tasks.RenderFlareTask;
import com.airdropmc.tasks.RenderPackageGlowTask;
import com.airdropmc.tasks.RenderPackageLandedTask;
import com.airdropmc.tasks.RenderPackageSmokeTask;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.FallingBlock;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Represents a crate that can be dropped from the sky
 * A.K.A an Airdrop
 */
public class Crate {
	private static final NamespacedKey CRATE_ID_KEY = Objects.requireNonNull(
			NamespacedKey.fromString("airdrop:crate_id"));
	private static final NamespacedKey PAID_KEY = Objects.requireNonNull(
			NamespacedKey.fromString("airdrop:paid"));
	private static final NamespacedKey EXPIRES_AT_KEY = Objects.requireNonNull(
			NamespacedKey.fromString("airdrop:expires_at"));
	private static final NamespacedKey RECOVERY_STATE_KEY = Objects.requireNonNull(
			NamespacedKey.fromString("airdrop:recovery_state"));
	private static final byte PAID_VALUE = 1;

	public enum State {
		FALLING,
		LANDED
	}

	public enum Outcome {
		LANDED,
		FAILED
	}

	public enum RecoveryState {
		LIVE,
		RECOVERABLE
	}

	public record PersistedBarrelData(
			String crateId,
			long expiresAtMillis,
			RecoveryState recoveryState) {
	}

	private final World world;
	private final ArrayList<ItemStack> contents;
	private final String crateId;
	private State state;
	private final DropOptions options;
	private final DropAdmissionController.Lease lease;
	private final Consumer<Outcome> outcomeListener;
	private final boolean paid;
	private Outcome outcome;

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
	private long expiresAtMillis;
	private boolean barrelIdentityPersisted;
	private RenderFlareTask flareEffect;
	private RenderPackageGlowTask glowEffect;
	private RenderPackageSmokeTask smokeEffect;
	private volatile boolean opened;
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
		this(location, world, contents, options, lease, false, ignored -> { });
	}

	public Crate(Location location, World world, List<ItemStack> contents, DropOptions options,
			DropAdmissionController.Lease lease, Consumer<Outcome> outcomeListener) {
		this(location, world, contents, options, lease, false, outcomeListener);
	}

	public Crate(Location location, World world, List<ItemStack> contents, DropOptions options,
			DropAdmissionController.Lease lease, boolean paid, Consumer<Outcome> outcomeListener) {
		this.dropLocation = Objects.requireNonNull(location, "location").clone();
		this.world = Objects.requireNonNull(world, "world");
		this.contents = cloneContents(contents);
		this.crateId = UUID.randomUUID().toString();
		this.state = State.FALLING;
		this.options = Objects.requireNonNull(options, "options");
		this.lease = Objects.requireNonNull(lease, "lease");
		this.paid = paid;
		this.outcomeListener = Objects.requireNonNull(outcomeListener, "outcomeListener");
		this.parachuteSystem = new ParachuteSystem(
				world, options, () -> CrateManager.removeCrateAndDestroy(fallingCrate));
	}

	private Crate(World world, Barrel barrel, PersistedBarrelData persisted,
			DropAdmissionController.Lease lease) {
		this.world = Objects.requireNonNull(world, "world");
		this.contents = new ArrayList<>();
		this.crateId = persisted.crateId();
		this.state = State.LANDED;
		this.options = DropOptions.createDefault();
		this.lease = Objects.requireNonNull(lease, "lease");
		this.paid = true;
		this.outcomeListener = ignored -> { };
		this.outcome = Outcome.LANDED;
		this.landedLocation = barrel.getLocation().clone();
		this.blockChest = barrel.getBlock();
		this.expiresAtMillis = persisted.expiresAtMillis();
		this.barrelIdentityPersisted = true;
	}

	public static Crate recoverPaidLanded(World world, Barrel barrel, PersistedBarrelData persisted,
			DropAdmissionController.Lease lease, Airdrop plugin) {
		Crate crate = new Crate(world, barrel, persisted, lease);
		crate.scheduleExpiry(plugin);
		return crate;
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
			if (!CrateManager.addLandedCrate(candidate, this)) {
				throw new IllegalStateException("Another crate already owns the landed location");
			}

			this.blockChest = block;
			this.landedLocation = candidate;
			this.state = State.LANDED;
			this.expiresAtMillis = Math.addExact(
					System.currentTimeMillis(), ConfigKeys.getDropLimitSettings().landedLifetime().toMillis());
			blockChest.setType(Material.BARREL);
			BlockState barrelState = blockChest.getState();
			if (!(barrelState instanceof Barrel barrel)) {
				throw new IllegalStateException("Failed to create barrel at landed location");
			}
			initializeLandedBarrel(barrel);
			lease.markLanded();
			scheduleExpiry(plugin);
			startLandedEffects(plugin);
			world.playSound(landedLocation, Sound.ENTITY_PLAYER_LEVELUP, .05f, .05f);
			if (flareEffect != null) {
				flareEffect.cancel();
				flareEffect = null;
			}
			reportOutcome(Outcome.LANDED);
		} catch (RuntimeException failure) {
			CrateManager.removeCrateAndDestroy(this);
			throw failure;
		}
	}

	private void initializeLandedBarrel(Barrel barrel) {
		PersistentDataContainer data = barrel.getPersistentDataContainer();
		data.set(CRATE_ID_KEY, PersistentDataType.STRING, crateId);
		if (paid) {
			data.set(PAID_KEY, PersistentDataType.BYTE, PAID_VALUE);
			data.set(EXPIRES_AT_KEY, PersistentDataType.LONG, expiresAtMillis);
			data.set(RECOVERY_STATE_KEY, PersistentDataType.STRING, RecoveryState.LIVE.name());
		}
		Inventory snapshotInventory = barrel.getSnapshotInventory();
		if (paid) {
			setExactContents(snapshotInventory);
		} else {
			insertContents(snapshotInventory);
		}
		if (!barrel.update(true, false)) {
			throw new IllegalStateException("Failed to persist landed crate");
		}
		// Keep the live tile inventory aligned with the committed snapshot.
		barrel.getInventory().setStorageContents(snapshotInventory.getStorageContents());
		barrelIdentityPersisted = true;
	}

	private void setExactContents(Inventory inventory) {
		int storageSize = inventory.getStorageContents().length;
		if (contents.size() > storageSize) {
			throw new IllegalStateException("Package has more item stacks than a barrel can hold");
		}
		ItemStack[] exactContents = new ItemStack[storageSize];
		for (int slot = 0; slot < contents.size(); slot++) {
			exactContents[slot] = contents.get(slot).clone();
		}
		inventory.setStorageContents(exactContents);
	}

	private void insertContents(Inventory inventory) {
		int overflowStackCount = 0;
		for (ItemStack item : contents) {
			Map<Integer, ItemStack> overflow = inventory.addItem(item);
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

	/**
	 * Returns whether the barrel is the physical block created for this crate.
	 */
	public synchronized boolean ownsLandedBarrel(Barrel barrel) {
		return barrelIdentityPersisted && barrel != null && crateId.equals(
				barrel.getPersistentDataContainer().get(CRATE_ID_KEY, PersistentDataType.STRING));
	}

	public synchronized boolean markRecoverable() {
		if (!paid || destroyed || state != State.LANDED) {
			return false;
		}
		try {
			Barrel barrel = getOwnedLandedBarrel();
			return barrel != null && transitionRecoveryState(
					barrel,
					new PersistedBarrelData(crateId, expiresAtMillis, RecoveryState.LIVE),
					RecoveryState.RECOVERABLE);
		} catch (RuntimeException failure) {
			AirdropLogger.log(Level.WARNING,
					"Could not mark paid crate " + crateId + " recoverable", failure);
			return false;
		}
	}

	public synchronized boolean restoreLiveAfterFailedSave() {
		if (!paid || destroyed || state != State.LANDED) {
			return false;
		}
		try {
			Barrel barrel = getOwnedLandedBarrel();
			return barrel != null && transitionRecoveryState(
					barrel,
					new PersistedBarrelData(crateId, expiresAtMillis, RecoveryState.RECOVERABLE),
					RecoveryState.LIVE);
		} catch (RuntimeException failure) {
			AirdropLogger.log(Level.WARNING,
					"Could not restore paid crate " + crateId + " to live state", failure);
			return false;
		}
	}

	public synchronized void detachLandedBarrel() {
		if (destroyed) {
			return;
		}
		destroyed = true;
		stopAllTasks();
		lease.close();
	}

	public synchronized DropAdmissionController.Lease suspendLandedBarrel() {
		if (destroyed || state != State.LANDED) {
			throw new IllegalStateException("Cannot suspend a crate that is not active and landed");
		}
		destroyed = true;
		stopAllTasks();
		return lease;
	}

	public static boolean hasAirdropMarker(Barrel barrel) {
		if (barrel == null) {
			return false;
		}
		Set<NamespacedKey> keys = barrel.getPersistentDataContainer().getKeys();
		return keys.contains(CRATE_ID_KEY)
				|| keys.contains(PAID_KEY)
				|| keys.contains(EXPIRES_AT_KEY)
				|| keys.contains(RECOVERY_STATE_KEY);
	}

	public static PersistedBarrelData readPaidPersistence(Barrel barrel) {
		if (!hasAirdropMarker(barrel)) {
			return null;
		}
		PersistentDataContainer data = barrel.getPersistentDataContainer();
		String crateId = data.get(CRATE_ID_KEY, PersistentDataType.STRING);
		Byte paid = data.get(PAID_KEY, PersistentDataType.BYTE);
		Long expiresAt = data.get(EXPIRES_AT_KEY, PersistentDataType.LONG);
		String stateName = data.get(RECOVERY_STATE_KEY, PersistentDataType.STRING);
		if (crateId == null || crateId.isBlank() || paid == null || paid != PAID_VALUE
				|| expiresAt == null || expiresAt <= 0L || stateName == null) {
			return null;
		}
		try {
			UUID.fromString(crateId);
			return new PersistedBarrelData(
					crateId, expiresAt, RecoveryState.valueOf(stateName));
		} catch (IllegalArgumentException invalid) {
			return null;
		}
	}

	public static boolean markPersistedBarrelLive(Barrel barrel, PersistedBarrelData expected) {
		return transitionRecoveryState(barrel, expected, RecoveryState.LIVE);
	}

	private static boolean transitionRecoveryState(
			Barrel barrel, PersistedBarrelData expected, RecoveryState replacement) {
		try {
			PersistedBarrelData current = readPaidPersistence(barrel);
			if (current == null || !current.equals(expected)
					|| current.recoveryState() == replacement) {
				return false;
			}
			barrel.getPersistentDataContainer().set(
					RECOVERY_STATE_KEY, PersistentDataType.STRING, replacement.name());
			return barrel.update(true, false);
		} catch (RuntimeException failure) {
			AirdropLogger.log(Level.WARNING, "Could not update paid crate recovery marker", failure);
			return false;
		}
	}

	public static boolean clearAirdropMetadata(Barrel barrel) {
		if (barrel == null) {
			return false;
		}
		PersistentDataContainer data = barrel.getPersistentDataContainer();
		data.remove(CRATE_ID_KEY);
		data.remove(PAID_KEY);
		data.remove(EXPIRES_AT_KEY);
		data.remove(RECOVERY_STATE_KEY);
		return barrel.update(true, false);
	}

	private void scheduleExpiry(Airdrop plugin) {
		if (destroyed || expiryTask != null) {
			return;
		}
		long remainingMillis = Math.max(1L, expiresAtMillis - System.currentTimeMillis());
		long ticks = Math.max(1L, Math.floorDiv(remainingMillis + 49L, 50L));
		expiryTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
			synchronized (Crate.this) {
				expiryTask = null;
			}
			CrateManager.removeCrateAndExpire(this);
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
	 * Cleans up resources used by this crate.
	 */
	public synchronized void destroy() {
		if (destroyed) {
			return;
		}
		destroyed = true;
		stopAllTasks();
		boolean deliveryAbsent = state != State.LANDED || removeOwnedLandedBarrelConfirmed();
		lease.close();
		if (deliveryAbsent) {
			reportOutcome(Outcome.FAILED);
		} else {
			AirdropLogger.warning("Could not confirm removal of crate " + crateId
					+ "; no failure outcome will be reported automatically");
		}
	}

	public synchronized void expire() {
		if (destroyed) {
			return;
		}
		destroyed = true;
		stopAllTasks();
		try {
			if (paid) {
				expirePaidBarrelFailClosed();
			} else {
				cleanupResource("landed barrel", this::removeOwnedLandedBarrel);
			}
		} finally {
			lease.close();
			reportOutcome(Outcome.FAILED);
		}
	}

	private void expirePaidBarrelFailClosed() {
		try {
			expirePaidBarrel();
		} catch (RuntimeException failure) {
			AirdropLogger.log(Level.WARNING,
					"Could not expire paid crate " + crateId + "; removing it fail-closed", failure);
			cleanupResource("paid landed barrel", this::removeOwnedLandedBarrel);
		}
	}

	private void expirePaidBarrel() {
		Barrel barrel = getOwnedLandedBarrel();
		if (barrel == null) {
			return;
		}
		if (barrel.getInventory().isEmpty()) {
			blockChest.setType(Material.AIR);
			return;
		}
		if (!clearAirdropMetadata(barrel)) {
			blockChest.setType(Material.AIR);
			AirdropLogger.warning("Removed paid crate " + crateId
					+ " because its expiry metadata could not be cleared safely");
		}
	}

	private void stopAllTasks() {
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
		stopLandedTasks();
		cleanupResource("flare effect", () -> {
			if (flareEffect != null && !flareEffect.isCancelled()) {
				flareEffect.cancel();
			}
		});
		flareEffect = null;
	}

	private void stopLandedTasks() {
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
	}

	private void reportOutcome(Outcome reported) {
		if (outcome != null) {
			return;
		}
		outcome = reported;
		try {
			outcomeListener.accept(reported);
		} catch (RuntimeException failure) {
			try {
				AirdropLogger.log(Level.WARNING, "Failed to report crate outcome " + reported, failure);
			} catch (RuntimeException loggingFailure) {
				failure.addSuppressed(loggingFailure);
			}
		}
	}

	private Barrel getOwnedLandedBarrel() {
		if (state != State.LANDED || blockChest == null || blockChest.getType() != Material.BARREL) {
			return null;
		}
		BlockState currentState = blockChest.getState();
		if (currentState instanceof Barrel barrel && ownsLandedBarrel(barrel)) {
			return barrel;
		}
		return null;
	}

	private void removeOwnedLandedBarrel() {
		if (state != State.LANDED || blockChest == null || blockChest.getType() != Material.BARREL) {
			return;
		}
		if (!barrelIdentityPersisted) {
			blockChest.setType(Material.AIR);
			return;
		}
		if (getOwnedLandedBarrel() != null) {
			blockChest.setType(Material.AIR);
		}
	}

	private boolean removeOwnedLandedBarrelConfirmed() {
		try {
			removeOwnedLandedBarrel();
			return blockChest == null || blockChest.getType() != Material.BARREL;
		} catch (RuntimeException failure) {
			AirdropLogger.log(Level.WARNING, "Failed to remove landed crate " + crateId, failure);
			return false;
		}
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

	public String getCrateId() {
		return crateId;
	}

	public boolean isPaid() {
		return paid;
	}

	public long getExpiresAtMillis() {
		return expiresAtMillis;
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
