package me.lukemccon.airdrop.helpers;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.FallingBlock;

import me.lukemccon.airdrop.Crate;

public class CrateList {
	// Correlates a particular falling block to a crate object that contains information about
	// what's in the crate, where it is etc.
	public static Map<FallingBlock, Crate> crateMap = new HashMap<FallingBlock, Crate>();

}
