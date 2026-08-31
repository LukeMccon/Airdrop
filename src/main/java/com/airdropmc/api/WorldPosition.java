package com.airdropmc.api;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

/**
 * A pure, detached world position that can be read safely without touching a
 * Bukkit world object.
 *
 * @param worldId stable Bukkit world UUID
 * @param x x coordinate
 * @param y y coordinate
 * @param z z coordinate
 * @param yaw horizontal rotation in degrees
 * @param pitch vertical rotation in degrees
 */
public record WorldPosition(
		UUID worldId,
		double x,
		double y,
		double z,
		float yaw,
		float pitch) {

	/** Validates a finite detached world position. */
	public WorldPosition {
		worldId = Objects.requireNonNull(worldId, "worldId");
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
				|| !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
			throw new IllegalArgumentException("Position coordinates and rotation must be finite");
		}
	}

	/**
	 * Creates a detached position from a Bukkit location.
	 *
	 * <p>This method must be called on the primary server thread because it
	 * accesses a Bukkit {@link World}.</p>
	 *
	 * @param location source Bukkit location
	 * @return detached position
	 */
	public static WorldPosition from(Location location) {
		Location copy = Objects.requireNonNull(location, "location").clone();
		World world = copy.getWorld();
		if (world == null) {
			throw new IllegalArgumentException("Location must have a world");
		}
		UUID worldId = world.getUID();
		if (worldId == null) {
			throw new IllegalArgumentException("Location world must have a UUID");
		}
		return new WorldPosition(
				worldId, copy.getX(), copy.getY(), copy.getZ(), copy.getYaw(), copy.getPitch());
	}

	/**
	 * Creates a new Bukkit location in the matching world.
	 *
	 * <p>This method must be called on the primary server thread because it
	 * accepts and returns Bukkit values.</p>
	 *
	 * @param world matching Bukkit world
	 * @return a new Bukkit location
	 */
	public Location toLocation(World world) {
		World requiredWorld = Objects.requireNonNull(world, "world");
		if (!worldId.equals(requiredWorld.getUID())) {
			throw new IllegalArgumentException("World does not match this position");
		}
		return new Location(requiredWorld, x, y, z, yaw, pitch);
	}

	/**
	 * Returns whether another detached position belongs to the same world.
	 *
	 * @param other position to compare
	 * @return {@code true} when both positions have the same world UUID
	 */
	public boolean isSameWorld(WorldPosition other) {
		return other != null && worldId.equals(other.worldId);
	}
}
