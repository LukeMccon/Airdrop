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
		Crate landedCrate = CrateManager.removeCrate(fallingBlock);
		if (landedCrate == null) {
			return;
		}
		e.setCancelled(true);
		Location loc = entity.getLocation();
		World world = loc.getWorld();
		if (world == null) {
			landedCrate.destroy();
			return;
		}
		try {
			landedCrate.land(loc.getBlock());
		} catch (RuntimeException landFailure) {
			rollbackFailedLanding(landedCrate);
			throw landFailure;
		}
		PackageLandEvent landEvent = new PackageLandEvent(landedCrate, world, loc, loc.getBlock());
		Bukkit.getPluginManager().callEvent(landEvent);
	}

	private void rollbackFailedLanding(Crate crate) {
		Location landedLocation = crate.getLandedLocation();
		if (landedLocation != null) {
			if (CrateManager.removeCrateAndDestroy(landedLocation)) {
				return;
			}
		}
		crate.destroy();
	}
}
