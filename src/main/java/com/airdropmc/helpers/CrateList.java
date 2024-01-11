package com.airdropmc.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.FallingBlock;

import com.airdropmc.Crate;

public class CrateList {

	CrateList() {

	}

	public static Map<FallingBlock, Crate> getCrateMap() {
		return crateMap;
	}

	public static List<Location> getBarrelList() {
		return barrelList;
	}

	// Correlates a particular falling block to a crate object that contains information about
	// what's in the crate, where it is etc.
	protected static Map<FallingBlock, Crate> crateMap = new HashMap<>();
	
	// ArrayList of the locations of created barrels
	protected static List<Location> barrelList = new ArrayList<>();

}
