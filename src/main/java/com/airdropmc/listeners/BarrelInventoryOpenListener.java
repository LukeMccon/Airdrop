package com.airdropmc.listeners;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.block.Barrel;

import com.airdropmc.LandedCrate;
import com.airdropmc.helpers.CrateList;

public class BarrelInventoryOpenListener implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryOpen(InventoryOpenEvent e) {

        System.out.println("Here listening");
        System.out.println(e.getInventory().getType());
        System.out.println((Barrel) e.getInventory().getHolder());

        if (e.getInventory().getType() != InventoryType.BARREL)
            return;

        Barrel barrel = (Barrel) e.getInventory().getHolder();

        if (barrel == null) {
            return;
        }

        Location barrelLocation = barrel.getBlock().getLocation();
        LandedCrate landedCrate = CrateList.getLandedCrate(barrelLocation);
        System.out.println(barrel.getBlock().getLocation());
        System.out.println(CrateList.getLandedCrate(barrelLocation));

        if (landedCrate != null) {
            System.out.println("Here gonna call stopParticleEffect");
            landedCrate.stopParticleEffect();
        }
    }
}
