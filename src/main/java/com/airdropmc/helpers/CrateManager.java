package com.airdropmc.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.FallingBlock;

import com.airdropmc.Crate;

/**
 * Manages crates
 */
public class CrateManager {

	private CrateManager() {
		// Private constructor to prevent instantiation
	}

	// Guarded by synchronized access methods in this class
	private static final Map<FallingBlock, Crate> crateMap = new HashMap<>();

	// Guarded by synchronized access methods in this class
	private static final Map<BlockKey, Crate> landedCrateMap = new HashMap<>();

	// Thread-safe access methods for crateMap
	public static synchronized void addCrate(FallingBlock block, Crate crate) {
		crateMap.put(block, crate);
	}

	public static synchronized Crate removeCrate(FallingBlock block) {
		return crateMap.remove(block);
	}

	public static synchronized Crate getCrate(FallingBlock block) {
		return crateMap.get(block);
	}

	public static synchronized boolean hasCrate(FallingBlock block) {
		return crateMap.containsKey(block);
	}

	// Thread-safe access methods for CrateMap
	public static synchronized void addCrate(Location location, Crate crate) {
		BlockKey key = toBlockKey(location);
		if (key == null) {
			return;
		}
		landedCrateMap.put(key, crate);
	}

	public static synchronized Crate removeCrate(Location location) {
		BlockKey key = toBlockKey(location);
		if (key == null) {
			return null;
		}
		return landedCrateMap.remove(key);
	}

	public static synchronized boolean removeCrateAndDestroy(Location location) {
		Crate removedCrate = removeCrate(location);
		if (removedCrate == null) {
			return false;
		}
		removedCrate.destroy();
		return true;
	}

	public static synchronized boolean removeCrateAndDestroy(FallingBlock block) {
		Crate removedCrate = removeCrate(block);
		if (removedCrate == null) {
			return false;
		}
		removedCrate.destroy();
		return true;
	}

	public static synchronized Crate getCrate(Location location) {
		BlockKey key = toBlockKey(location);
		if (key == null) {
			return null;
		}
		return landedCrateMap.get(key);
	}

	public static synchronized void removeFallingCratesInChunk(Chunk chunk) {
		if (chunk == null) {
			return;
		}
		int chunkX = chunk.getX();
		int chunkZ = chunk.getZ();
		List<FallingBlock> blocksToRemove = new ArrayList<>();
		Iterator<Map.Entry<FallingBlock, Crate>> fallingIterator = crateMap.entrySet().iterator();
		while (fallingIterator.hasNext()) {
			Map.Entry<FallingBlock, Crate> entry = fallingIterator.next();
			FallingBlock fallingBlock = entry.getKey();
			if (fallingBlock == null || fallingBlock.getWorld() == null) {
				blocksToRemove.add(fallingBlock);
				continue;
			}
			if (!fallingBlock.getWorld().equals(chunk.getWorld())) {
				continue;
			}
			int locationChunkX = fallingBlock.getLocation().getBlockX() >> 4;
			int locationChunkZ = fallingBlock.getLocation().getBlockZ() >> 4;
			if (locationChunkX == chunkX && locationChunkZ == chunkZ) {
				blocksToRemove.add(fallingBlock);
			}
		}
		for (FallingBlock fallingBlock : blocksToRemove) {
			removeCrateAndDestroy(fallingBlock);
		}
	}

	public static synchronized void removeCratesInChunk(Chunk chunk) {
		if (chunk == null) {
			return;
		}

		removeFallingCratesInChunk(chunk);

		int chunkX = chunk.getX();
		int chunkZ = chunk.getZ();
		UUID chunkWorldId = chunk.getWorld().getUID();
		List<Location> landedLocationsToRemove = new ArrayList<>();
		Iterator<Map.Entry<BlockKey, Crate>> iterator = landedCrateMap.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<BlockKey, Crate> entry = iterator.next();
			BlockKey key = entry.getKey();
			if (key == null) {
				Crate crate = entry.getValue();
				if (crate != null) {
					crate.destroy();
				}
				iterator.remove();
				continue;
			}
			if (!chunkWorldId.equals(key.worldId())) {
				continue;
			}
			int locationChunkX = key.x() >> 4;
			int locationChunkZ = key.z() >> 4;
			if (locationChunkX == chunkX && locationChunkZ == chunkZ) {
				landedLocationsToRemove.add(new Location(chunk.getWorld(), key.x(), key.y(), key.z()));
			}
		}
		for (Location location : landedLocationsToRemove) {
			removeCrateAndDestroy(location);
		}
	}

	public static synchronized void removeCratesInWorld(World world) {
		if (world == null) {
			return;
		}

		UUID worldId = world.getUID();
		List<FallingBlock> fallingBlocksToRemove = new ArrayList<>();
		Iterator<Map.Entry<FallingBlock, Crate>> fallingIterator = crateMap.entrySet().iterator();
		while (fallingIterator.hasNext()) {
			Map.Entry<FallingBlock, Crate> entry = fallingIterator.next();
			FallingBlock fallingBlock = entry.getKey();
			if (fallingBlock == null || fallingBlock.getWorld() == null) {
				fallingBlocksToRemove.add(fallingBlock);
				continue;
			}
			if (!worldId.equals(fallingBlock.getWorld().getUID())) {
				continue;
			}
			fallingBlocksToRemove.add(fallingBlock);
		}
		for (FallingBlock fallingBlock : fallingBlocksToRemove) {
			removeCrateAndDestroy(fallingBlock);
		}

		List<Location> landedLocationsToRemove = new ArrayList<>();
		Iterator<Map.Entry<BlockKey, Crate>> landedIterator = landedCrateMap.entrySet().iterator();
		while (landedIterator.hasNext()) {
			Map.Entry<BlockKey, Crate> entry = landedIterator.next();
			BlockKey key = entry.getKey();
			if (key == null) {
				Crate crate = entry.getValue();
				if (crate != null) {
					crate.destroy();
				}
				landedIterator.remove();
				continue;
			}
			if (!worldId.equals(key.worldId())) {
				continue;
			}
			landedLocationsToRemove.add(new Location(world, key.x(), key.y(), key.z()));
		}
		for (Location location : landedLocationsToRemove) {
			removeCrateAndDestroy(location);
		}
	}

	public static synchronized void clearAll() {
		Set<Crate> crates = new HashSet<>();
		crates.addAll(crateMap.values());
		crates.addAll(landedCrateMap.values());
		for (Crate crate : crates) {
			if (crate != null) {
				crate.destroy();
			}
		}
		crateMap.clear();
		landedCrateMap.clear();
	}

	// Legacy methods for backward compatibility
	@Deprecated
	public static Map<FallingBlock, Crate> getCrateMap() {
		return crateMap;
	}

	private static BlockKey toBlockKey(Location location) {
		if (location == null || location.getWorld() == null) {
			return null;
		}
		return new BlockKey(
				location.getWorld().getUID(),
				location.getBlockX(),
				location.getBlockY(),
				location.getBlockZ());
	}

	private record BlockKey(UUID worldId, int x, int y, int z) {
	}
}
