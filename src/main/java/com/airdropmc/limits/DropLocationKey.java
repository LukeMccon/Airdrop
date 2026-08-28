package com.airdropmc.limits;

import org.bukkit.Location;

import java.util.Objects;
import java.util.UUID;

public record DropLocationKey(UUID worldId, int x, int y, int z) {

	public DropLocationKey {
		Objects.requireNonNull(worldId, "worldId");
	}

	public static DropLocationKey from(Location location) {
		if (location == null || location.getWorld() == null) {
			throw new IllegalArgumentException("Drop location must have a world");
		}
		return new DropLocationKey(
				location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
	}
}
