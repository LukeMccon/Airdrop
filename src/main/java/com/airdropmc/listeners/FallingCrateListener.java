package com.airdropmc.listeners;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.Bukkit;

import com.airdropmc.Crate;
import com.airdropmc.helpers.CrateManager;

import com.airdropmc.events.PackageLandEvent;

public class FallingCrateListener implements Listener {

	@EventHandler(priority = EventPriority.NORMAL)
	public void onEntityChangeBlockEvent(EntityChangeBlockEvent e) {
		Entity entity = e.getEntity();

		if (!(entity instanceof FallingBlock)) {
			return;
		}

		FallingBlock fallingBlock = (FallingBlock) entity;
		if (CrateManager.hasCrate(fallingBlock)) {
			e.setCancelled(true);
			Location loc = entity.getLocation();
			World world = loc.getWorld();
			Crate landedCrate = CrateManager.getCrate(fallingBlock);
			landedCrate.land(loc.getBlock());
			PackageLandEvent landEvent = new PackageLandEvent(landedCrate, world, loc, loc.getBlock());
			Bukkit.getPluginManager().callEvent(landEvent);
		}
	}
}
