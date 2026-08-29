package com.airdropmc.events;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import com.airdropmc.Crate;
import com.airdropmc.helpers.LocationHelper;

/**
 * Event that is called when a package is dropped
 */
public class PackageDropEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();
	private final Crate crate;
	private final World world;
	private final Location dropLocation;

	public PackageDropEvent(Crate crate, World world, Location dropLocation) {
		this.crate = crate;
		this.world = Objects.requireNonNull(world, "world");
		this.dropLocation = LocationHelper.copyInWorld(dropLocation, this.world, "dropLocation");
	}

    /**
     * Gets the crate that was dropped
     * @return The crate
     */
	public Crate getCrate() {
		return crate;
	}

    /**
     * Gets the world where the package was dropped
     * @return The world
     */
	public World getWorld() {
		return world;
	}

    /**
     * Gets the location where the package was dropped
     * @return The drop location
     */
	public Location getDropLocation() {
		return dropLocation.clone();
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
