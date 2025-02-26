package com.airdropmc.listeners;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.block.Barrel;

import com.airdropmc.Crate;
import com.airdropmc.helpers.CrateManager;

public class CrateOpenListener implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryOpen(InventoryOpenEvent e) {

        if (e.getInventory().getType() != InventoryType.BARREL)
            return;

        Barrel barrel = (Barrel) e.getInventory().getHolder();

        if (barrel == null) {
            return;
        }

        Location barrelLocation = barrel.getBlock().getLocation();
        Crate landedCrate = CrateManager.getCrate(barrelLocation);
        if (landedCrate != null) {
            landedCrate.stopEffects();
        }
    }
}
