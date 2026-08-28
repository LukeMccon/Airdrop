package com.airdropmc.listeners;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
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

	@EventHandler(priority = EventPriority.HIGHEST)
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
		if (e.isCancelled()) {
			landedCrate.destroy();
			return;
		}
		e.setCancelled(true);
		// Paper keeps FallingBlock entities alive after event cancellation.
		// Explicitly remove it so it doesn't fire again and place an empty barrel.
		fallingBlock.remove();
		Block landingBlock = e.getBlock();
		if (landingBlock == null) {
			landedCrate.destroy();
			return;
		}
		Location landingLocation = landingBlock.getLocation();
		World world = landingBlock.getWorld();
		try {
			landedCrate.land(landingBlock);
		} catch (RuntimeException landFailure) {
			CrateManager.removeCrateAndDestroy(landedCrate);
			throw landFailure;
		}
		PackageLandEvent landEvent = new PackageLandEvent(
				landedCrate, world, landingLocation, landingBlock);
		Bukkit.getPluginManager().callEvent(landEvent);
	}
}
