package com.airdropmc.helpers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

	// Legacy methods for backward compatibility
	@Deprecated
	public static Map<FallingBlock, Crate> getCrateMap() {
		return crateMap;
	}
}