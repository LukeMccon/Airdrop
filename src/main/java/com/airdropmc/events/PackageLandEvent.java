package com.airdropmc.events;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.block.Block;
import com.airdropmc.Crate;

/**
 * Event that is called when a package lands and transforms into a barrel
 */
public class PackageLandEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Crate crate;
    private final World world;
    private final Location landingLocation;
    private final Block landedBlock;

    public PackageLandEvent(Crate crate, World world, Location landingLocation, Block landedBlock) {
        this.crate = crate;
        this.world = world;
        this.landingLocation = landingLocation;
        this.landedBlock = landedBlock;
    }

    /**
     * Gets the crate that landed
     * 
     * @return The crate
     */
    public Crate getCrate() {
        return crate;
    }

    /**
     * Gets the world where the package landed
     * 
     * @return The world
     */
    public World getWorld() {
        return world;
    }

    /**
     * Gets the location where the package landed
     * 
     * @return The landing location
     */
    public Location getLandingLocation() {
        return landingLocation;
    }

    /**
     * Gets the block that the package turned into (usually a barrel)
     * 
     * @return The landed block
     */
    public Block getLandedBlock() {
        return landedBlock;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
