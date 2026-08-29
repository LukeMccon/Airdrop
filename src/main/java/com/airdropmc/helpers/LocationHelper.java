package com.airdropmc.helpers;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

public final class LocationHelper {

	private LocationHelper() {
	}

	public static Location copyInWorld(Location location, World world, String locationName) {
		Location requiredLocation = Objects.requireNonNull(location, locationName);
		World requiredWorld = Objects.requireNonNull(world, "world");
		UUID worldId = requiredWorld.getUID();
		if (worldId == null) {
			throw new IllegalArgumentException("world must have a UUID");
		}
		World locationWorld = requiredLocation.getWorld();
		if (locationWorld == null || !worldId.equals(locationWorld.getUID())) {
			throw new IllegalArgumentException(locationName + " must be in the supplied world");
		}
		return requiredLocation.clone();
	}
}
