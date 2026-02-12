package com.airdropmc.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import com.airdropmc.helpers.CrateManager;

public class CrateDestroyListener implements Listener {

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent e) {
		if (e.getBlock().getType() != Material.BARREL) {
			return;
		}
		Location barrelLocation = e.getBlock().getLocation();

		CrateManager.removeCrateAndDestroy(barrelLocation);
	}
}
