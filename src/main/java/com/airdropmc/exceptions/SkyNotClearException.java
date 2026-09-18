package com.airdropmc.exceptions;

import org.bukkit.Location;

import java.util.Objects;

/**
 * Indicates that a drop cannot start because the requested location is below
 * the highest block in its column.
 */
public class SkyNotClearException extends Exception {

	/**
	 * Captures a detached snapshot of the rejected location.
	 *
	 * @param location attempted drop location
	 * @throws NullPointerException if {@code location} is {@code null}
	 */
	public SkyNotClearException(Location location) {
		super("Sky is not clear above the requested drop location");
		this.location = Objects.requireNonNull(location, "location").clone();
	}

	private final Location location;

	/** @return a detached copy of the rejected location */
	public Location getLocation() {
		return location.clone();
	}
}
