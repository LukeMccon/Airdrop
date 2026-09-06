package com.airdropmc.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.bukkit.Chunk;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.BlockState;
import org.bukkit.entity.FallingBlock;
import org.jetbrains.annotations.ApiStatus;

import com.airdropmc.Airdrop;
import com.airdropmc.Crate;
import com.airdropmc.Crate.PersistedBarrelData;
import com.airdropmc.Crate.RecoveryState;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLocationKey;
import com.airdropmc.api.AirdropView;
import com.airdropmc.api.FallingAirdropView;
import com.airdropmc.api.LandedAirdropView;
import com.airdropmc.api.RetirementReason;
import com.airdropmc.api.event.AirdropRecoveredEvent;
import com.airdropmc.api.event.AirdropRetiredEvent;
import com.airdropmc.internal.drop.ActiveDropRegistry;
import com.airdropmc.internal.recovery.RecoveryReport;

/**
 * Manages crates.
 */
@ApiStatus.Internal
public class CrateManager {

	private CrateManager() {
		// Private constructor to prevent instantiation
	}

	// Mutated on the primary thread; short synchronized sections keep related
	// physical indexes coherent without running lifecycle cleanup or events under the monitor.
	private static final Map<FallingBlock, Crate> crateMap = new HashMap<>();
	private static final ActiveDropRegistry activeDropRegistry = new ActiveDropRegistry();
	private static final Map<Crate, UUID> activeViewIds = new IdentityHashMap<>();
	private static final Map<DropLocationKey, Crate> landedCrateMap = new HashMap<>();
	private static final Map<String, Crate> activeCrateIds = new HashMap<>();
	private static final Map<Crate, String> indexedCrateIds = new IdentityHashMap<>();
	private static final Map<String, DropLocationKey> knownCrateLocations = new HashMap<>();
	private static final Map<String, DropAdmissionController.Lease> suspendedLeases = new HashMap<>();
	private static final Set<String> compromisedCrateIds = new HashSet<>();
	private static final Set<String> retiredCrateIds = new HashSet<>();
	private static volatile RecoveryReport recoveryReport = RecoveryReport.empty();
	private static final Consumer<World> DEFAULT_WORLD_SAVER = world -> world.save(true);
	private static Consumer<World> worldSaver = DEFAULT_WORLD_SAVER;

	private record RecoveryCandidate(
			Barrel barrel,
			PersistedBarrelData persisted,
			DropLocationKey location) {
	}

	private record PendingRecovery(
			RecoveryCandidate candidate,
			DropAdmissionController.Lease lease) {
	}

	private record Removal(Crate crate, AirdropView view, boolean removed) {
	}

	private record ClearedState(
			List<AirdropView> views,
			List<DropAdmissionController.Lease> suspendedLeases) {
	}

	static synchronized void setWorldSaverForTesting(Consumer<World> saver) {
		worldSaver = saver == null ? DEFAULT_WORLD_SAVER : saver;
	}

	static synchronized void resetWorldSaverForTesting() {
		worldSaver = DEFAULT_WORLD_SAVER;
	}

	public static synchronized boolean addCrate(FallingBlock block, Crate crate) {
		return block != null && crate != null && crateMap.putIfAbsent(block, crate) == null;
	}

	/** Registers the physical falling crate and supported read model atomically. */
	public static synchronized boolean addActiveCrate(
			FallingBlock block, Crate crate, FallingAirdropView view) {
		if (block == null || crate == null || view == null || crateMap.containsKey(block)) {
			return false;
		}
		try {
			activeDropRegistry.registerFalling(view);
		} catch (RuntimeException duplicate) {
			return false;
		}
		crateMap.put(block, crate);
		activeViewIds.put(crate, view.crateId());
		return true;
	}

	public static Crate removeCrate(FallingBlock block) {
		return removeCrate(block, RetirementReason.FAILED);
	}

	public static Crate removeCrate(FallingBlock block, RetirementReason reason) {
		Removal removal;
		synchronized (CrateManager.class) {
			Crate removed = crateMap.remove(block);
			removal = new Removal(removed, removeActiveView(removed), removed != null);
		}
		publishRetired(removal.view(), reason);
		return removal.crate();
	}

	/** Removes only the physical falling index while preserving the active view for landing. */
	public static synchronized Crate detachFallingForLanding(FallingBlock block) {
		return crateMap.remove(block);
	}

	public static synchronized Crate getCrate(FallingBlock block) {
		return crateMap.get(block);
	}

	public static synchronized boolean hasCrate(FallingBlock block) {
		return crateMap.containsKey(block);
	}

	public static synchronized boolean addCrate(Location location, Crate crate) {
		DropLocationKey key = toDropLocationKey(location);
		return key != null && crate != null && landedCrateMap.putIfAbsent(key, crate) == null;
	}

	public static synchronized boolean addLandedCrate(Location location, Crate crate) {
		DropLocationKey key = toDropLocationKey(location);
		if (key == null || crate == null || landedCrateMap.containsKey(key)) {
			return false;
		}
		String crateId = getCrateId(crate);
		if (crateId == null || !isValidCrateId(crateId) || !canIndex(crateId, key, crate)) {
			return false;
		}

		LandedAirdropView landedView = crate.snapshotLandedView();
		if (landedView != null) {
			try {
				AirdropView current = activeDropRegistry.findByCrateId(landedView.crateId()).orElse(null);
				if (current instanceof FallingAirdropView falling) {
					activeDropRegistry.transitionToLanded(
							landedView.crateId(), falling.fallingEntityId(), key, landedView);
				} else if (landedView.recovered()) {
					activeDropRegistry.registerRecovered(key, landedView);
					activeViewIds.put(crate, landedView.crateId());
				}
			} catch (RuntimeException duplicate) {
				return false;
			}
		}
		landedCrateMap.put(key, crate);
		index(crateId, key, crate);
		return true;
	}

	/** Validates landed raw/identity indexes without mutating them. */
	public static synchronized boolean canAddLandedCrate(Location location, Crate crate) {
		DropLocationKey key = toDropLocationKey(location);
		if (key == null || crate == null || landedCrateMap.containsKey(key)) {
			return false;
		}
		String crateId = getCrateId(crate);
		return crateId != null && isValidCrateId(crateId) && canIndex(crateId, key, crate);
	}

	public static Crate removeCrate(Location location) {
		return removeCrate(location, RetirementReason.FAILED);
	}

	public static Crate removeCrate(Location location, RetirementReason reason) {
		Removal removal;
		synchronized (CrateManager.class) {
			DropLocationKey key = toDropLocationKey(location);
			if (key == null) {
				return null;
			}
			Crate removed = landedCrateMap.remove(key);
			retireIdentity(removed, key);
			removal = new Removal(removed, removeActiveView(removed), removed != null);
		}
		publishRetired(removal.view(), reason);
		return removal.crate();
	}

	/** Removes every live index for a crate without invoking its lifecycle callbacks. */
	public static boolean removeCrate(Crate crate) {
		return removeCrate(crate, RetirementReason.FAILED);
	}

	public static boolean removeCrate(Crate crate, RetirementReason reason) {
		Removal removal;
		synchronized (CrateManager.class) {
			removal = removeCrateTracking(crate);
		}
		publishRetired(removal.view(), reason);
		return removal.removed();
	}

	/** Samples a tracked falling entity on the primary thread for pure API readers. */
	public static synchronized void refreshFallingView(FallingBlock block) {
		Crate crate = crateMap.get(block);
		UUID crateId = activeViewIds.get(crate);
		if (crateId == null
				|| !(activeDropRegistry.findByCrateId(crateId).orElse(null)
						instanceof FallingAirdropView)) {
			return;
		}
		FallingAirdropView view = crate.snapshotFallingView(block);
		if (view != null) {
			activeDropRegistry.replaceFalling(view);
		}
	}

	/** Replaces a landed view after observable fields such as opened change. */
	public static synchronized void refreshLandedView(Crate crate) {
		if (crate == null) {
			return;
		}
		String indexedId = indexedCrateIds.get(crate);
		DropLocationKey key = indexedId == null ? null : knownCrateLocations.get(indexedId);
		LandedAirdropView view = crate.snapshotLandedView();
		if (key != null && view != null
				&& activeDropRegistry.findByCrateId(view.crateId()).isPresent()) {
			activeDropRegistry.replaceLanded(key, view);
		}
	}

	/** @return immutable active-drop aggregate safe for off-thread reads */
	public static List<AirdropView> activeDrops() {
		return activeDropRegistry.activeDrops();
	}

	public static java.util.Optional<AirdropView> findByRequestId(UUID requestId) {
		return activeDropRegistry.findByRequestId(requestId);
	}

	public static java.util.Optional<AirdropView> findByCrateId(UUID crateId) {
		return activeDropRegistry.findByCrateId(crateId);
	}

	public static java.util.Optional<AirdropView> findByFallingEntityId(UUID entityId) {
		return activeDropRegistry.findByFallingEntityId(entityId);
	}

	public static java.util.Optional<AirdropView> findByLandedLocation(DropLocationKey location) {
		return activeDropRegistry.findByLandedLocation(location);
	}

	public static int fallingCount() {
		return activeDropRegistry.fallingCount();
	}

	public static int landedCount() {
		return activeDropRegistry.landedCount();
	}

	/** @return atomic active-drop counts safe for off-thread status reads */
	public static ActiveDropRegistry.Counts activeCounts() {
		return activeDropRegistry.counts();
	}

	/** @return bounded internal paid-crate recovery health report */
	public static RecoveryReport recoveryReport() {
		return recoveryReport;
	}

	public static boolean removeCrateAndDestroy(Location location) {
		return removeCrateAndDestroy(location, RetirementReason.FAILED);
	}

	public static boolean removeCrateAndDestroy(Location location, RetirementReason reason) {
		Crate removedCrate = removeCrate(location, reason);
		if (removedCrate == null) {
			return false;
		}
		removedCrate.destroy();
		return true;
	}

	public static boolean removeCrateAndDetach(Location location) {
		Crate removedCrate = removeCrate(location, RetirementReason.FAILED);
		if (removedCrate == null) {
			return false;
		}
		removedCrate.detachLandedBarrel();
		return true;
	}

	public static boolean finalizeCrateBreak(Location location) {
		DropLocationKey key = toDropLocationKey(location);
		Crate crate;
		synchronized (CrateManager.class) {
			crate = key == null ? null : landedCrateMap.get(key);
		}
		if (crate == null) {
			return false;
		}
		BlockState current = location.getBlock().getState();
		if (current instanceof Barrel barrel && crate.ownsLandedBarrel(barrel)) {
			return false;
		}
		Removal removal;
		synchronized (CrateManager.class) {
			if (!landedCrateMap.remove(key, crate)) {
				return false;
			}
			retireIdentity(crate, key);
			removal = new Removal(crate, removeActiveView(crate), true);
		}
		crate.detachLandedBarrel();
		publishRetired(removal.view(), RetirementReason.BROKEN);
		return true;
	}

	public static boolean removeCrateAndDestroy(FallingBlock block) {
		return removeCrateAndDestroy(block, RetirementReason.FAILED);
	}

	public static boolean removeCrateAndDestroy(FallingBlock block, RetirementReason reason) {
		Crate removedCrate = removeCrate(block, reason);
		if (removedCrate == null) {
			return false;
		}
		removedCrate.destroy();
		return true;
	}

	public static boolean removeCrateAndDestroy(Crate crate) {
		return removeCrateAndDestroy(crate, RetirementReason.FAILED);
	}

	public static boolean removeCrateAndDestroy(Crate crate, RetirementReason reason) {
		if (crate == null) {
			return false;
		}
		boolean removed = removeCrate(crate, reason);
		crate.destroy();
		return removed;
	}

	public static boolean removeCrateAndExpire(Crate crate) {
		if (crate == null) {
			return false;
		}
		boolean removed = removeCrate(crate, RetirementReason.EXPIRED);
		crate.expire();
		return removed;
	}

	public static synchronized Crate getCrate(Location location) {
		DropLocationKey key = toDropLocationKey(location);
		return key == null ? null : landedCrateMap.get(key);
	}

	public static void removeFallingCratesInChunk(Chunk chunk) {
		if (chunk == null) {
			return;
		}
		int chunkX = chunk.getX();
		int chunkZ = chunk.getZ();
		List<Removal> cratesToDestroy = new ArrayList<>();
		synchronized (CrateManager.class) {
			Iterator<Map.Entry<FallingBlock, Crate>> entries = crateMap.entrySet().iterator();
			while (entries.hasNext()) {
				Map.Entry<FallingBlock, Crate> entry = entries.next();
				FallingBlock fallingBlock = entry.getKey();
				boolean belongsToChunk = fallingBlock == null || fallingBlock.getWorld() == null;
				if (!belongsToChunk && fallingBlock.getWorld().equals(chunk.getWorld())) {
					int locationChunkX = fallingBlock.getLocation().getBlockX() >> 4;
					int locationChunkZ = fallingBlock.getLocation().getBlockZ() >> 4;
					belongsToChunk = locationChunkX == chunkX && locationChunkZ == chunkZ;
				}
				if (belongsToChunk) {
					Crate crate = entry.getValue();
					cratesToDestroy.add(new Removal(
							crate, removeActiveView(crate), crate != null));
					entries.remove();
				}
			}
		}
		for (Removal removal : cratesToDestroy) {
			publishRetired(removal.view(), RetirementReason.CHUNK_UNLOAD);
			safeDestroy(removal.crate());
		}
	}

	/**
	 * Durably prepares paid crates before their chunk unloads.
	 *
	 * @return whether the event must keep the chunk-save flag enabled
	 */
	public static boolean prepareChunkForUnload(Chunk chunk) {
		if (chunk == null) {
			return false;
		}
		removeFallingCratesInChunk(chunk);
		return prepareLandedCratesForChunkUnload(chunk);
	}

	private static boolean prepareLandedCratesForChunkUnload(Chunk chunk) {
		World world = chunk.getWorld();
		UUID worldId = world.getUID();
		int chunkX = chunk.getX();
		int chunkZ = chunk.getZ();
		boolean touchedPaidCrate = false;
		List<Map.Entry<DropLocationKey, Crate>> paid = new ArrayList<>();
		List<Map.Entry<DropLocationKey, Crate>> crates = landedCrateMap.entrySet().stream()
				.filter(entry -> inChunk(entry.getKey(), worldId, chunkX, chunkZ))
				.map(entry -> Map.entry(entry.getKey(), entry.getValue()))
				.toList();
		for (Map.Entry<DropLocationKey, Crate> entry : crates) {
			DropLocationKey key = entry.getKey();
			Crate crate = entry.getValue();
			if (crate == null) {
				landedCrateMap.remove(key);
				continue;
			}
			if (!isPaid(crate)) {
				removeTrackedAndDestroy(key, crate, RetirementReason.CHUNK_UNLOAD);
				continue;
			}

			touchedPaidCrate = true;
			if (!ensurePaidIdentityIndexed(key, crate)) {
				AirdropLogger.warning("Paid crate identity was invalid during chunk unload"
						+ " and was removed fail-closed");
				removeTrackedAndDestroy(key, crate);
				continue;
			}
			paid.add(entry);
		}
		if (!touchedPaidCrate) {
			return false;
		}

		try {
			// Save the current LIVE marker first. If another plugin disables the final
			// chunk save, the stale disk copy will be purged instead of replayed.
			saveWorld(world);
		} catch (RuntimeException | LinkageError failure) {
			AirdropLogger.log(Level.SEVERE,
					"Could not save paid crates before unloading chunk "
							+ world.getName() + " " + chunkX + "," + chunkZ
							+ "; removing them fail-closed",
					failure);
			for (Map.Entry<DropLocationKey, Crate> entry : paid) {
				removeTrackedAndDestroy(entry.getKey(), entry.getValue());
			}
			saveFailClosedWorld(world);
			return true;
		}

		for (Map.Entry<DropLocationKey, Crate> entry : paid) {
			Crate crate = entry.getValue();
			if (!markRecoverable(crate)) {
				AirdropLogger.warning("Paid crate could not be prepared for chunk unload"
						+ " and was removed fail-closed");
				removeTrackedAndDestroy(entry.getKey(), crate);
				continue;
			}
			try {
				detachForRecovery(
						entry.getKey(), crate, true, RetirementReason.CHUNK_UNLOAD);
			} catch (RuntimeException failure) {
				AirdropLogger.log(Level.WARNING,
						"Could not suspend paid crate for chunk unload; removing it fail-closed", failure);
				removeTrackedAndDestroy(entry.getKey(), crate);
			}
		}
		return true;
	}

	/**
	 * Backward-compatible destructive cleanup used by older callers and tests.
	 */
	public static void removeCratesInChunk(Chunk chunk) {
		if (chunk == null) {
			return;
		}
		removeFallingCratesInChunk(chunk);
		UUID worldId = chunk.getWorld().getUID();
		List<DropLocationKey> keys = landedCrateMap.keySet().stream()
				.filter(key -> inChunk(key, worldId, chunk.getX(), chunk.getZ()))
				.toList();
		for (DropLocationKey key : keys) {
			removeCrateAndDestroy(
					new Location(chunk.getWorld(), key.x(), key.y(), key.z()),
					RetirementReason.CHUNK_UNLOAD);
		}
	}

	public static boolean prepareWorldForUnload(World world, Airdrop plugin) {
		if (world == null || plugin == null) {
			return false;
		}
		removeFallingCratesInWorld(world, RetirementReason.WORLD_UNLOAD);
		List<Map.Entry<DropLocationKey, Crate>> paid = new ArrayList<>();
		for (Map.Entry<DropLocationKey, Crate> entry : landedEntriesInWorld(world.getUID())) {
			Crate crate = entry.getValue();
			if (isPaid(crate) && ensurePaidIdentityIndexed(entry.getKey(), crate)) {
				paid.add(entry);
			} else {
				removeTrackedAndDestroy(
						entry.getKey(), crate, RetirementReason.WORLD_UNLOAD);
			}
		}
		if (paid.isEmpty()) {
			return true;
		}

		try {
			// Persist a fail-closed LIVE baseline before exposing RECOVERABLE state
			// to the rest of the unload event.
			saveWorld(world);
		} catch (RuntimeException | LinkageError failure) {
			AirdropLogger.log(Level.SEVERE,
					"Could not save paid crates before unloading world " + world.getName(), failure);
			return false;
		}

		List<Crate> prepared = new ArrayList<>();
		for (Map.Entry<DropLocationKey, Crate> entry : paid) {
			if (!markRecoverable(entry.getValue())) {
				removeTrackedAndDestroy(entry.getKey(), entry.getValue());
				rollbackPreparedWorld(world, paid, prepared);
				return false;
			}
			prepared.add(entry.getValue());
		}
		for (Map.Entry<DropLocationKey, Crate> entry : paid) {
			try {
				detachForRecovery(
						entry.getKey(), entry.getValue(), true, RetirementReason.WORLD_UNLOAD);
			} catch (RuntimeException failure) {
				AirdropLogger.log(Level.WARNING,
						"Could not suspend paid crate for world unload; removing it fail-closed", failure);
				removeTrackedAndDestroy(entry.getKey(), entry.getValue());
			}
		}
		return true;
	}

	public static void prepareForShutdown(Airdrop plugin) {
		try {
			for (FallingBlock fallingBlock : new ArrayList<>(crateMap.keySet())) {
				try {
					removeCrateAndDestroy(fallingBlock, RetirementReason.SHUTDOWN);
				} catch (RuntimeException failure) {
					AirdropLogger.log(Level.WARNING, "Could not remove falling crate during shutdown", failure);
				}
			}

			Map<World, List<Map.Entry<DropLocationKey, Crate>>> paidByWorld = new LinkedHashMap<>();
			for (Map.Entry<DropLocationKey, Crate> entry : new ArrayList<>(landedCrateMap.entrySet())) {
				Crate crate = entry.getValue();
				Location landed = safeLandedLocation(crate);
				if (crate == null || !isPaid(crate) || landed == null || landed.getWorld() == null
						|| !ensurePaidIdentityIndexed(entry.getKey(), crate)) {
					removeTrackedAndDestroy(
							entry.getKey(), crate, RetirementReason.SHUTDOWN);
					continue;
				}
				paidByWorld.computeIfAbsent(landed.getWorld(), ignored -> new ArrayList<>())
						.add(Map.entry(entry.getKey(), crate));
			}

			for (Map.Entry<World, List<Map.Entry<DropLocationKey, Crate>>> worldEntry
					: paidByWorld.entrySet()) {
				World world = worldEntry.getKey();
				List<Map.Entry<DropLocationKey, Crate>> prepared = new ArrayList<>();
				for (Map.Entry<DropLocationKey, Crate> entry : worldEntry.getValue()) {
					if (markRecoverable(entry.getValue())) {
						prepared.add(entry);
					} else {
						AirdropLogger.warning("Removing paid crate that could not be prepared for shutdown");
						removeTrackedAndDestroy(entry.getKey(), entry.getValue());
					}
				}

				try {
					saveWorld(world);
				} catch (RuntimeException | LinkageError failure) {
					AirdropLogger.log(Level.SEVERE,
							"Could not save paid crates while disabling in world " + world.getName(), failure);
					for (Map.Entry<DropLocationKey, Crate> entry : prepared) {
						removeTrackedAndDestroy(entry.getKey(), entry.getValue());
					}
					saveFailClosedWorld(world);
					continue;
				}

				boolean cleanupChanged = false;
				for (Map.Entry<DropLocationKey, Crate> entry : prepared) {
					try {
						detachForRecovery(
								entry.getKey(), entry.getValue(), false, RetirementReason.SHUTDOWN);
					} catch (RuntimeException failure) {
						AirdropLogger.log(Level.WARNING,
								"Could not detach paid crate during shutdown; removing it fail-closed", failure);
						removeTrackedAndDestroy(entry.getKey(), entry.getValue());
						cleanupChanged = true;
					}
				}
				if (cleanupChanged) {
					saveFailClosedWorld(world);
				}
			}
		} catch (RuntimeException | LinkageError failure) {
			AirdropLogger.log(Level.SEVERE, "Unexpected paid crate shutdown cleanup failure", failure);
		} finally {
			Set<Crate> remaining = distinctTrackedCrates();
			ClearedState cleared = clearMaps();
			publishRetired(cleared.views(), RetirementReason.SHUTDOWN);
			closeSuspendedLeases(cleared.suspendedLeases());
			for (Crate crate : remaining) {
				safeDestroy(crate);
			}
		}
	}

	public static void recoverLoadedCrates(
			Airdrop plugin, DropAdmissionController admission) {
		if (plugin == null || admission == null) {
			return;
		}
		for (World world : plugin.getServer().getWorlds()) {
			recoverCrates(plugin, admission, world, Arrays.asList(world.getLoadedChunks()));
		}
	}

	public static void recoverCratesInChunk(
			Airdrop plugin, DropAdmissionController admission, Chunk chunk) {
		if (plugin == null || admission == null || chunk == null) {
			return;
		}
		recoverCrates(plugin, admission, chunk.getWorld(), List.of(chunk));
	}

	public static void recoverLoadedCratesInWorld(
			Airdrop plugin, DropAdmissionController admission, World world) {
		if (plugin == null || admission == null || world == null) {
			return;
		}
		recoverCrates(plugin, admission, world, Arrays.asList(world.getLoadedChunks()));
	}

	private static void recoverCrates(Airdrop plugin, DropAdmissionController admission,
			World world, List<Chunk> chunks) {
		List<Barrel> stale = new ArrayList<>();
		Map<String, List<RecoveryCandidate>> byId = new LinkedHashMap<>();
		Map<UUID, List<RecoveryCandidate>> byRequestId = new LinkedHashMap<>();
		Map<DropLocationKey, String> discoveredIds = new HashMap<>();
		boolean foundUntrackedMarker = false;
		for (Chunk chunk : chunks) {
			for (BlockState state : chunk.getTileEntities()) {
				if (!(state instanceof Barrel barrel) || !Crate.hasAirdropMarker(barrel)) {
					continue;
				}
				DropLocationKey key = DropLocationKey.from(barrel.getLocation());
				Crate tracked = landedCrateMap.get(key);
				if (tracked != null && tracked.ownsLandedBarrel(barrel)) {
					continue;
				}
				foundUntrackedMarker = true;
				if (tracked != null) {
					removeTrackedAndDetach(key, tracked);
				}

				PersistedBarrelData persisted = Crate.readPaidPersistence(barrel);
				if (persisted == null) {
					stale.add(barrel);
					continue;
				}
				discoveredIds.put(key, persisted.crateId());
				RecoveryCandidate candidate = new RecoveryCandidate(barrel, persisted, key);
				byId.computeIfAbsent(persisted.crateId(), ignored -> new ArrayList<>())
						.add(candidate);
				persisted.recoveryDescriptor().ifPresent(descriptor ->
						byRequestId.computeIfAbsent(descriptor.requestId(), ignored -> new ArrayList<>())
								.add(candidate));
			}
		}

		Set<RecoveryCandidate> duplicateRequests = new HashSet<>();
		for (Map.Entry<UUID, List<RecoveryCandidate>> entry : byRequestId.entrySet()) {
			if (entry.getValue().size() > 1
					|| activeDropRegistry.findByRequestId(entry.getKey()).isPresent()) {
				duplicateRequests.addAll(entry.getValue());
			}
		}

		Set<String> mismatchedSuspendedIds = findMismatchedSuspendedIdentities(
				world.getUID(), chunks, discoveredIds);

		for (Barrel barrel : stale) {
			purgeRecoveryBarrel(barrel, "stale or invalid lifecycle marker");
		}

		List<RecoveryCandidate> valid = new ArrayList<>();
		for (Map.Entry<String, List<RecoveryCandidate>> entry : byId.entrySet()) {
			String crateId = entry.getKey();
			List<RecoveryCandidate> candidates = entry.getValue();
			if (candidates.stream().anyMatch(duplicateRequests::contains)) {
				retireCrateId(crateId);
				for (RecoveryCandidate candidate : candidates) {
					purgeRecoveryBarrel(candidate.barrel(), "duplicate request identity");
				}
				continue;
			}
			if (mismatchedSuspendedIds.contains(crateId)) {
				for (RecoveryCandidate candidate : candidates) {
					purgeRecoveryBarrel(candidate.barrel(), "moved or changed recovery marker");
				}
				continue;
			}
			if (retiredCrateIds.contains(crateId)) {
				for (RecoveryCandidate candidate : candidates) {
					purgeRecoveryBarrel(candidate.barrel(), "retired crate identity");
				}
				continue;
			}
			DropLocationKey knownLocation = knownCrateLocations.get(crateId);
			boolean duplicate = compromisedCrateIds.contains(crateId) || candidates.size() != 1;
			if (!duplicate && knownLocation != null && !knownLocation.equals(candidates.getFirst().location())) {
				duplicate = true;
			}
			if (duplicate) {
				compromiseCrateId(crateId);
				for (RecoveryCandidate candidate : candidates) {
					purgeRecoveryBarrel(candidate.barrel(), "duplicate crate identity");
				}
				continue;
			}
			RecoveryCandidate candidate = candidates.getFirst();
			if (candidate.persisted().recoveryState() != RecoveryState.RECOVERABLE) {
				retireCrateId(crateId);
				purgeRecoveryBarrel(candidate.barrel(), "stale lifecycle marker");
				continue;
			}
			valid.add(candidate);
		}

		List<PendingRecovery> pending = new ArrayList<>();
		for (RecoveryCandidate candidate : valid) {
			DropAdmissionController.Lease lease;
			try {
				lease = takeSuspendedLease(candidate.persisted().crateId(), candidate.location());
				if (lease == null) {
					lease = admission.restoreLanded(candidate.location());
				}
			} catch (RuntimeException failure) {
				AirdropLogger.log(Level.WARNING,
						"Could not restore admission for paid crate " + candidate.persisted().crateId(), failure);
				retireCrateId(candidate.persisted().crateId());
				purgeRecoveryBarrel(candidate.barrel(), "duplicate or invalid admission claim");
				continue;
			}
			if (!Crate.markPersistedBarrelLive(candidate.barrel(), candidate.persisted())) {
				lease.close();
				retireCrateId(candidate.persisted().crateId());
				purgeRecoveryBarrel(candidate.barrel(), "could not claim recoverable marker");
				continue;
			}
			pending.add(new PendingRecovery(candidate, lease));
		}

		if (!foundUntrackedMarker && mismatchedSuspendedIds.isEmpty()) {
			return;
		}
		try {
			saveWorld(world);
		} catch (RuntimeException | LinkageError failure) {
			AirdropLogger.log(Level.SEVERE,
					"Could not durably reconcile paid crates in world " + world.getName()
							+ "; removing claimed markers fail-closed", failure);
			for (PendingRecovery recovery : pending) {
				recovery.lease().close();
				retireCrateId(recovery.candidate().persisted().crateId());
				purgeRecoveryBarrel(recovery.candidate().barrel(), "claim save failed");
			}
			if (saveFailClosedWorld(world)) {
				retireMismatchedSuspendedIdentities(mismatchedSuspendedIds);
			}
			if (failure instanceof RuntimeException runtimeFailure) {
				throw runtimeFailure;
			}
			throw (LinkageError) failure;
		}
		retireMismatchedSuspendedIdentities(mismatchedSuspendedIds);

		boolean saveAgain = false;
		for (PendingRecovery recovery : pending) {
			RecoveryCandidate candidate = recovery.candidate();
			Crate crate = null;
			LandedAirdropView recoveredView = null;
			try {
				crate = Crate.recoverPaidLanded(
						world, candidate.barrel(), candidate.persisted(), recovery.lease(), plugin);
				if (!addLandedCrate(candidate.barrel().getLocation(), crate)) {
					throw new IllegalStateException("Recovered crate identity is already active");
				}
				recoveredView = crate.snapshotLandedView();
				recordRecoveredCrate();
			} catch (RuntimeException failure) {
				AirdropLogger.log(Level.WARNING,
						"Could not activate recovered paid crate " + candidate.persisted().crateId(), failure);
				if (crate != null) {
					crate.detachLandedBarrel();
				} else {
					recovery.lease().close();
				}
				retireCrateId(candidate.persisted().crateId());
				purgeRecoveryBarrel(candidate.barrel(), "runtime recovery failed");
				saveAgain = true;
			}
			publishRecovered(recoveredView);
		}
		if (saveAgain) {
			saveFailClosedWorld(world);
		}
	}

	public static void removeCratesInWorld(World world) {
		if (world == null) {
			return;
		}
		removeFallingCratesInWorld(world, RetirementReason.WORLD_UNLOAD);
		for (Map.Entry<DropLocationKey, Crate> entry : landedEntriesInWorld(world.getUID())) {
			removeTrackedAndDestroy(
					entry.getKey(), entry.getValue(), RetirementReason.WORLD_UNLOAD);
		}
	}

	public static void purgeForHotDisable(Airdrop plugin) {
		Set<World> changedWorlds = Collections.newSetFromMap(new IdentityHashMap<>());
		try {
			for (FallingBlock fallingBlock : new ArrayList<>(crateMap.keySet())) {
				try {
					removeCrateAndDestroy(fallingBlock, RetirementReason.HOT_DISABLE);
				} catch (RuntimeException failure) {
					AirdropLogger.log(Level.WARNING,
							"Could not remove falling crate during plugin disable", failure);
				}
			}
			for (Map.Entry<DropLocationKey, Crate> entry
					: new ArrayList<>(landedCrateMap.entrySet())) {
				Location landed = safeLandedLocation(entry.getValue());
				removeTrackedAndDestroy(
						entry.getKey(), entry.getValue(), RetirementReason.HOT_DISABLE);
				if (landed != null && landed.getWorld() != null) {
					changedWorlds.add(landed.getWorld());
				}
			}

			if (plugin != null) {
				purgeLoadedAirdropMarkers(plugin, changedWorlds);
				for (Map.Entry<String, DropLocationKey> entry
						: new ArrayList<>(knownCrateLocations.entrySet())) {
					DropLocationKey key = entry.getValue();
					World world = plugin.getServer().getWorld(key.worldId());
					if (world == null) {
						AirdropLogger.warning("Could not purge suspended paid crate " + entry.getKey()
								+ " because its world is unavailable");
						retireCrateId(entry.getKey());
						continue;
					}
					Location location = new Location(world, key.x(), key.y(), key.z());
					purgeOwnedPaidMarker(location, entry.getKey());
					retireCrateId(entry.getKey());
					changedWorlds.add(world);
				}
			}
			for (World world : changedWorlds) {
				saveFailClosedWorld(world);
			}
		} catch (RuntimeException | LinkageError failure) {
			AirdropLogger.log(Level.SEVERE, "Unexpected hot-disable crate cleanup failure", failure);
		} finally {
			Set<Crate> remaining = distinctTrackedCrates();
			ClearedState cleared = clearMaps();
			publishRetired(cleared.views(), RetirementReason.HOT_DISABLE);
			closeSuspendedLeases(cleared.suspendedLeases());
			for (Crate crate : remaining) {
				safeDestroy(crate);
			}
		}
	}

	private static void purgeLoadedAirdropMarkers(Airdrop plugin, Set<World> changedWorlds) {
		for (World world : plugin.getServer().getWorlds()) {
			try {
				boolean changed = false;
				for (Chunk chunk : world.getLoadedChunks()) {
					for (BlockState state : chunk.getTileEntities()) {
						if (!(state instanceof Barrel barrel) || !Crate.hasAirdropMarker(barrel)) {
							continue;
						}
						purgeMarkedBarrel(barrel, "plugin disabled before lifecycle recovery");
						changed = true;
					}
				}
				if (changed) {
					changedWorlds.add(world);
				}
			} catch (RuntimeException | LinkageError failure) {
				AirdropLogger.log(Level.WARNING,
						"Could not scan loaded paid crates in world " + world.getName()
								+ " during plugin disable",
						failure);
			}
		}
	}

	public static void clearAll() {
		Set<Crate> crates;
		ClearedState cleared;
		synchronized (CrateManager.class) {
			crates = distinctTrackedCrates();
			cleared = clearMaps();
		}
		publishRetired(cleared.views(), RetirementReason.FAILED);
		closeSuspendedLeases(cleared.suspendedLeases());
		for (Crate crate : crates) {
			safeDestroy(crate);
		}
	}

	@Deprecated
	public static Map<FallingBlock, Crate> getCrateMap() {
		synchronized (CrateManager.class) {
			return Collections.unmodifiableMap(new HashMap<>(crateMap));
		}
	}

	private static boolean ensurePaidIdentityIndexed(DropLocationKey key, Crate crate) {
		String crateId = getCrateId(crate);
		if (key == null || crateId == null || !isValidCrateId(crateId)
				|| retiredCrateIds.contains(crateId)) {
			return false;
		}

		String indexedId = indexedCrateIds.get(crate);
		if (indexedId != null) {
			return indexedId.equals(crateId)
					&& activeCrateIds.get(crateId) == crate
					&& key.equals(knownCrateLocations.get(crateId))
					&& !compromisedCrateIds.contains(crateId);
		}
		if (!canIndex(crateId, key, crate)) {
			compromiseCrateId(crateId);
			return false;
		}
		index(crateId, key, crate);
		return true;
	}

	private static boolean canIndex(String crateId, DropLocationKey key, Crate crate) {
		if (crateId == null) {
			return true;
		}
		if (compromisedCrateIds.contains(crateId) || retiredCrateIds.contains(crateId)) {
			return false;
		}
		DropLocationKey knownLocation = knownCrateLocations.get(crateId);
		if (knownLocation != null && !knownLocation.equals(key)) {
			return false;
		}
		Crate active = activeCrateIds.get(crateId);
		return active == null || active == crate;
	}

	private static void index(String crateId, DropLocationKey key, Crate crate) {
		if (crateId == null) {
			return;
		}
		knownCrateLocations.putIfAbsent(crateId, key);
		activeCrateIds.put(crateId, crate);
		indexedCrateIds.put(crate, crateId);
	}

	private static void retireIdentity(Crate crate, DropLocationKey key) {
		String crateId = indexedCrateIds.remove(crate);
		if (crateId == null) {
			return;
		}
		activeCrateIds.remove(crateId, crate);
		knownCrateLocations.remove(crateId, key);
		retireCrateId(crateId);
	}

	private static void retireCrateId(String crateId) {
		if (crateId == null || crateId.isBlank()) {
			return;
		}
		retiredCrateIds.add(crateId);
		DropAdmissionController.Lease suspended = suspendedLeases.remove(crateId);
		if (suspended != null) {
			suspended.close();
		}
		if (!activeCrateIds.containsKey(crateId)) {
			knownCrateLocations.remove(crateId);
		}
	}

	private static DropAdmissionController.Lease takeSuspendedLease(
			String crateId, DropLocationKey location) {
		DropAdmissionController.Lease lease = suspendedLeases.remove(crateId);
		if (lease == null) {
			return null;
		}
		if (!lease.owns(location)) {
			lease.close();
			throw new IllegalStateException("Suspended lease location does not match paid crate");
		}
		return lease;
	}

	private static void detachForRecovery(
			DropLocationKey key,
			Crate crate,
			boolean preserveIdentity,
			RetirementReason reason) {
		String crateId = indexedCrateIds.get(crate);
		if (preserveIdentity && crateId == null) {
			throw new IllegalStateException("Paid crate identity is not indexed");
		}
		if (preserveIdentity) {
			DropAdmissionController.Lease lease = crate.suspendLandedBarrel();
			DropAdmissionController.Lease previous = suspendedLeases.putIfAbsent(crateId, lease);
			if (previous != null) {
				lease.close();
				throw new IllegalStateException("Paid crate identity already has a suspended lease");
			}
		} else {
			crate.detachLandedBarrel();
		}

		landedCrateMap.remove(key, crate);
		AirdropView removedView = removeActiveView(crate);
		indexedCrateIds.remove(crate);
		if (crateId != null) {
			activeCrateIds.remove(crateId, crate);
			if (!preserveIdentity) {
				knownCrateLocations.remove(crateId, key);
			}
		}
		publishRetired(removedView, reason);
	}

	private static void removeTrackedAndDestroy(DropLocationKey key, Crate crate) {
		removeTrackedAndDestroy(key, crate, RetirementReason.FAILED);
	}

	private static void removeTrackedAndDestroy(
			DropLocationKey key, Crate crate, RetirementReason reason) {
		boolean paid = isPaid(crate);
		String crateId = paid ? getCrateId(crate) : null;
		Location landed = paid ? safeLandedLocation(crate) : null;
		AirdropView removedView;
		synchronized (CrateManager.class) {
			landedCrateMap.remove(key, crate);
			retireIdentity(crate, key);
			removedView = removeActiveView(crate);
		}
		publishRetired(removedView, reason);
		safeDestroy(crate);
		if (paid) {
			purgeOwnedPaidMarker(landed, crateId);
		}
	}

	private static void removeTrackedAndDetach(DropLocationKey key, Crate crate) {
		AirdropView removedView;
		synchronized (CrateManager.class) {
			landedCrateMap.remove(key, crate);
			retireIdentity(crate, key);
			removedView = removeActiveView(crate);
		}
		publishRetired(removedView, RetirementReason.RECOVERY_PURGED);
		if (crate != null) {
			crate.detachLandedBarrel();
		}
	}

	private static void removeFallingCratesInWorld(World world, RetirementReason reason) {
		UUID worldId = world.getUID();
		List<FallingBlock> fallingBlocks = new ArrayList<>();
		for (FallingBlock block : crateMap.keySet()) {
			if (block == null || block.getWorld() == null
					|| worldId.equals(block.getWorld().getUID())) {
				fallingBlocks.add(block);
			}
		}
		for (FallingBlock block : fallingBlocks) {
			removeCrateAndDestroy(block, reason);
		}
	}

	private static List<Map.Entry<DropLocationKey, Crate>> landedEntriesInWorld(UUID worldId) {
		return landedCrateMap.entrySet().stream()
				.filter(entry -> entry.getKey() != null && worldId.equals(entry.getKey().worldId()))
				.map(entry -> Map.entry(entry.getKey(), entry.getValue()))
				.toList();
	}

	private static void rollbackPreparedWorld(World world,
			List<Map.Entry<DropLocationKey, Crate>> allPaid, List<Crate> prepared) {
		boolean restored = true;
		for (Crate crate : prepared) {
			restored &= crate.restoreLiveAfterFailedSave();
		}
		if (restored) {
			try {
				saveWorld(world);
				return;
			} catch (RuntimeException | LinkageError failure) {
				AirdropLogger.log(Level.SEVERE,
						"Could not restore live paid crate markers in world " + world.getName(), failure);
			}
		}

		AirdropLogger.warning("Removing paid crates in world " + world.getName()
				+ " because their lifecycle markers could not be saved safely");
		for (Map.Entry<DropLocationKey, Crate> entry : allPaid) {
			removeTrackedAndDestroy(entry.getKey(), entry.getValue());
		}
		saveFailClosedWorld(world);
	}

	private static void compromiseCrateId(String crateId) {
		DropAdmissionController.Lease suspended;
		Crate active;
		AirdropView removedView;
		DropLocationKey knownLocation;
		synchronized (CrateManager.class) {
			compromisedCrateIds.add(crateId);
			retiredCrateIds.add(crateId);
			suspended = suspendedLeases.remove(crateId);
			knownLocation = knownCrateLocations.remove(crateId);
			active = activeCrateIds.remove(crateId);
			removedView = removeActiveView(active);
			if (active != null) {
				indexedCrateIds.remove(active);
				if (knownLocation != null) {
					landedCrateMap.remove(knownLocation, active);
				}
			}
		}
		publishRetired(removedView, RetirementReason.RECOVERY_PURGED);
		if (suspended != null) {
			suspended.close();
		}
		if (active != null) {
			Location activeLocation = safeLandedLocation(active);
			World affectedWorld = activeLocation == null ? null : activeLocation.getWorld();
			safeDestroy(active);
			if (affectedWorld != null) {
				saveFailClosedWorld(affectedWorld);
			}
		}
		AirdropLogger.warning("Removed duplicate paid crate identity " + crateId + " fail-closed");
	}

	private static boolean purgeMarkedBarrel(Barrel barrel, String reason) {
		if (barrel == null || barrel.getBlock().getType() != Material.BARREL
				|| !Crate.hasAirdropMarker((Barrel) barrel.getBlock().getState())) {
			return false;
		}
		barrel.getBlock().setType(Material.AIR);
		AirdropLogger.warning("Removed Airdrop barrel at " + format(barrel.getLocation())
				+ " fail-closed: " + reason);
		return true;
	}

	private static void purgeRecoveryBarrel(Barrel barrel, String reason) {
		if (purgeMarkedBarrel(barrel, reason)) {
			recordPurgedCrate(reason);
		}
	}

	private static synchronized void recordRecoveredCrate() {
		recoveryReport = recoveryReport.withRecoveredCrate();
	}

	private static synchronized void recordPurgedCrate(String diagnostic) {
		recoveryReport = recoveryReport.withPurgedCrate(diagnostic);
	}

	private static void saveWorld(World world) {
		worldSaver.accept(world);
	}

	private static boolean saveFailClosedWorld(World world) {
		try {
			saveWorld(world);
			return true;
		} catch (RuntimeException | LinkageError failure) {
			AirdropLogger.log(Level.SEVERE,
					"Could not save fail-closed crate cleanup in world " + world.getName(), failure);
			return false;
		}
	}

	private static boolean markRecoverable(Crate crate) {
		if (crate == null) {
			return false;
		}
		try {
			return crate.markRecoverable();
		} catch (RuntimeException | LinkageError failure) {
			AirdropLogger.log(Level.WARNING,
					"Could not mark paid crate recoverable fail-closed", failure);
			return false;
		}
	}

	private static boolean isPaid(Crate crate) {
		if (crate == null) {
			return false;
		}
		try {
			return crate.isPaid();
		} catch (RuntimeException | LinkageError failure) {
			return false;
		}
	}

	private static Location safeLandedLocation(Crate crate) {
		if (crate == null) {
			return null;
		}
		try {
			return crate.getLandedLocation();
		} catch (RuntimeException failure) {
			return null;
		}
	}

	private static void safeDestroy(Crate crate) {
		if (crate == null) {
			return;
		}
		try {
			crate.destroy();
		} catch (RuntimeException failure) {
			AirdropLogger.log(Level.WARNING, "Could not destroy crate fail-closed", failure);
		}
	}

	private static void purgeOwnedPaidMarker(Location location, String crateId) {
		if (location == null || location.getWorld() == null || crateId == null) {
			return;
		}
		try {
			BlockState state = location.getBlock().getState();
			if (!(state instanceof Barrel barrel)) {
				return;
			}
			PersistedBarrelData persisted = Crate.readPaidPersistence(barrel);
			if ((persisted != null && crateId.equals(persisted.crateId()))
					|| (persisted == null && Crate.hasAirdropMarker(barrel))) {
				purgeMarkedBarrel(barrel, "terminal paid crate cleanup");
			}
		} catch (RuntimeException failure) {
			AirdropLogger.log(Level.WARNING,
					"Could not purge paid crate marker " + crateId + " fail-closed", failure);
		}
	}

	private static Set<Crate> distinctTrackedCrates() {
		Set<Crate> crates = Collections.newSetFromMap(new IdentityHashMap<>());
		crates.addAll(crateMap.values());
		crates.addAll(landedCrateMap.values());
		return crates;
	}

	private static ClearedState clearMaps() {
		List<DropAdmissionController.Lease> leases = List.copyOf(suspendedLeases.values());
		List<AirdropView> removedViews = activeDropRegistry.clear();
		crateMap.clear();
		landedCrateMap.clear();
		activeCrateIds.clear();
		indexedCrateIds.clear();
		knownCrateLocations.clear();
		suspendedLeases.clear();
		compromisedCrateIds.clear();
		retiredCrateIds.clear();
		recoveryReport = RecoveryReport.empty();
		activeViewIds.clear();
		return new ClearedState(removedViews, leases);
	}

	private static void closeSuspendedLeases(List<DropAdmissionController.Lease> leases) {
		for (DropAdmissionController.Lease lease : leases) {
			try {
				lease.close();
			} catch (RuntimeException failure) {
				AirdropLogger.log(Level.WARNING, "Could not release suspended paid crate lease", failure);
			}
		}
	}

	private static Removal removeCrateTracking(Crate crate) {
		if (crate == null) {
			return new Removal(null, null, false);
		}
		boolean removed = crateMap.entrySet().removeIf(entry -> entry.getValue() == crate);
		List<DropLocationKey> landedKeys = landedCrateMap.entrySet().stream()
				.filter(entry -> entry.getValue() == crate)
				.map(Map.Entry::getKey)
				.toList();
		for (DropLocationKey key : landedKeys) {
			landedCrateMap.remove(key, crate);
			retireIdentity(crate, key);
			removed = true;
		}
		AirdropView removedView = removeActiveView(crate);
		return new Removal(crate, removedView, removed || removedView != null);
	}

	private static AirdropView removeActiveView(Crate crate) {
		if (crate == null) {
			return null;
		}
		UUID crateId = activeViewIds.remove(crate);
		if (crateId == null) {
			return null;
		}
		return activeDropRegistry.remove(crateId).orElse(null);
	}

	private static void publishRetired(AirdropView view, RetirementReason reason) {
		if (view != null) {
			Bukkit.getPluginManager().callEvent(new AirdropRetiredEvent(view, reason));
		}
	}

	private static void publishRetired(List<AirdropView> views, RetirementReason reason) {
		for (AirdropView view : views) {
			publishRetired(view, reason);
		}
	}

	private static void publishRecovered(LandedAirdropView view) {
		if (view != null) {
			Bukkit.getPluginManager().callEvent(new AirdropRecoveredEvent(view));
		}
	}

	private static String getCrateId(Crate crate) {
		if (crate == null) {
			return null;
		}
		try {
			String crateId = crate.getCrateId();
			return crateId == null || crateId.isBlank() ? null : crateId;
		} catch (RuntimeException failure) {
			return null;
		}
	}

	private static boolean isValidCrateId(String crateId) {
		try {
			UUID.fromString(crateId);
			return true;
		} catch (IllegalArgumentException invalid) {
			return false;
		}
	}

	private static Set<String> findMismatchedSuspendedIdentities(UUID worldId,
			List<Chunk> chunks, Map<DropLocationKey, String> discoveredIds) {
		Set<String> mismatched = new HashSet<>();
		for (String expectedId : new ArrayList<>(suspendedLeases.keySet())) {
			DropLocationKey expectedLocation = knownCrateLocations.get(expectedId);
			if (expectedLocation == null) {
				AirdropLogger.warning("Released suspended paid crate " + expectedId
						+ " because its expected location was unavailable");
				retireCrateId(expectedId);
				continue;
			}
			if (!inAnyChunk(expectedLocation, worldId, chunks)) {
				continue;
			}
			if (!expectedId.equals(discoveredIds.get(expectedLocation))) {
				mismatched.add(expectedId);
			}
		}
		return mismatched;
	}

	private static void retireMismatchedSuspendedIdentities(Set<String> crateIds) {
		for (String crateId : crateIds) {
			AirdropLogger.warning("Released suspended paid crate " + crateId
					+ " because its recovery marker was missing or changed");
			retireCrateId(crateId);
		}
	}

	private static boolean inAnyChunk(DropLocationKey key, UUID worldId, List<Chunk> chunks) {
		if (key == null || !worldId.equals(key.worldId())) {
			return false;
		}
		for (Chunk chunk : chunks) {
			if (chunk != null && inChunk(key, worldId, chunk.getX(), chunk.getZ())) {
				return true;
			}
		}
		return false;
	}

	private static boolean inChunk(DropLocationKey key, UUID worldId, int chunkX, int chunkZ) {
		return key != null && worldId.equals(key.worldId())
				&& (key.x() >> 4) == chunkX && (key.z() >> 4) == chunkZ;
	}

	private static DropLocationKey toDropLocationKey(Location location) {
		if (location == null || location.getWorld() == null) {
			return null;
		}
		return DropLocationKey.from(location);
	}

	private static String format(Location location) {
		return location.getWorld() == null
				? location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ()
				: location.getWorld().getName() + " " + location.getBlockX() + ","
						+ location.getBlockY() + "," + location.getBlockZ();
	}
}
