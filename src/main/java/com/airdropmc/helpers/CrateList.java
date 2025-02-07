package com.airdropmc.helpers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.FallingBlock;

import com.airdropmc.Crate;
import com.airdropmc.LandedCrate;

public class CrateList {

	private CrateList() {
		// Private constructor to prevent instantiation
	}

	// Thread-safe map correlating falling blocks to crates
	private static final Map<FallingBlock, Crate> crateMap = new ConcurrentHashMap<>();

	// Thread-safe map for landed crates by location
	private static final Map<Location, LandedCrate> landedCrateMap = new ConcurrentHashMap<>();

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

	// Thread-safe access methods for landedCrateMap
	public static synchronized void addLandedCrate(Location location, LandedCrate landedCrate) {
		landedCrateMap.put(location, landedCrate);
	}

	public static synchronized LandedCrate removeLandedCrate(Location location) {
		LandedCrate crate = landedCrateMap.get(location);
		crate.destroy();
		return crate;
	}

	public static synchronized LandedCrate getLandedCrate(Location location) {
		return landedCrateMap.get(location);
	}

	// Legacy methods for backward compatibility
	@Deprecated
	public static Map<FallingBlock, Crate> getCrateMap() {
		return crateMap;
	}
}