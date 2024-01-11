package com.airdropmc.exceptions;

import org.bukkit.Location;

public class SkyNotClearException extends Exception {

    /**
     * Indicates that the area above the location is not valid for an Airdrop package to be dropped
     * @param loc attempted drop location
     */
    public SkyNotClearException(Location loc) {
        super("Sky must be clear above the location: " + loc.toString() + " to drop an airdrop");
    }
}
