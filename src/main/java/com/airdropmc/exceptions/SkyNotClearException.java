package com.airdropmc.exceptions;

import org.bukkit.Location;

public class SkyNotClearException extends Exception {

    /**
     * Indicates that the area above the location is not valid for an Airdrop package to be dropped
     * @param loc attempted drop location
     */
    public SkyNotClearException(Location loc) {
        this.location = loc;
    }

    private final Location location;

    public Location getLocation() {
        return location;
    }
}
