package lukemccon.airdrop.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.FallingBlock;

import lukemccon.airdrop.Crate;

public class CrateList {
	// Correlates a particular falling block to a crate object that contains information about
	// what's in the crate, where it is etc.
	public static Map<FallingBlock, Crate> crateMap = new HashMap<FallingBlock, Crate>();
	
	// ArrayList of the locations of created barrels
	public static ArrayList<Location> barrelList = new ArrayList<Location>();

}
