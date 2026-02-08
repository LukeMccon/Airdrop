package com.airdropmc.helpers;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.FallingBlock;

import com.airdropmc.Crate;

/**
 * Manages crates
 */
public class CrateManager {

	private CrateManager() {
		// Private constructor to prevent instantiation
	}

	// Thread-safe map correlating falling blocks to crates
	private static final Map<FallingBlock, Crate> crateMap = new ConcurrentHashMap<>();

	// Thread-safe map for landed crates by location
	private static final Map<Location, Crate> landedCrateMap = new ConcurrentHashMap<>();

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
		landedCrateMap.put(location, crate);
	}

	public static synchronized Crate removeCrate(Location location) {
		Crate crate = landedCrateMap.remove(location);
		if (crate != null) {
			crate.destroy();
		}
		return crate;
	}

	public static synchronized Crate getCrate(Location location) {
		return landedCrateMap.get(location);
	}

	public static synchronized void removeFallingCratesInChunk(Chunk chunk) {
		if (chunk == null) {
			return;
		}
		int chunkX = chunk.getX();
		int chunkZ = chunk.getZ();
		Iterator<Map.Entry<FallingBlock, Crate>> fallingIterator = crateMap.entrySet().iterator();
		while (fallingIterator.hasNext()) {
			Map.Entry<FallingBlock, Crate> entry = fallingIterator.next();
			FallingBlock fallingBlock = entry.getKey();
			if (fallingBlock == null || fallingBlock.getWorld() == null) {
				fallingIterator.remove();
				continue;
			}
			if (!fallingBlock.getWorld().equals(chunk.getWorld())) {
				continue;
			}
			int locationChunkX = fallingBlock.getLocation().getBlockX() >> 4;
			int locationChunkZ = fallingBlock.getLocation().getBlockZ() >> 4;
			if (locationChunkX == chunkX && locationChunkZ == chunkZ) {
				Crate crate = entry.getValue();
				if (crate != null) {
					crate.destroy();
				}
				fallingIterator.remove();
			}
		}
	}

	public static synchronized void removeCratesInChunk(Chunk chunk) {
		if (chunk == null) {
			return;
		}

		removeFallingCratesInChunk(chunk);

		int chunkX = chunk.getX();
		int chunkZ = chunk.getZ();
		Iterator<Map.Entry<Location, Crate>> iterator = landedCrateMap.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Location, Crate> entry = iterator.next();
			Location location = entry.getKey();
			if (location == null || location.getWorld() == null) {
				continue;
			}
			if (!location.getWorld().equals(chunk.getWorld())) {
				continue;
			}
			int locationChunkX = location.getBlockX() >> 4;
			int locationChunkZ = location.getBlockZ() >> 4;
			if (locationChunkX == chunkX && locationChunkZ == chunkZ) {
				Crate crate = entry.getValue();
				if (crate != null) {
					crate.destroy();
				}
				iterator.remove();
			}
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
}
