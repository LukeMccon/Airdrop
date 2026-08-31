package com.airdropmc.listeners;

import java.util.Objects;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;

import com.airdropmc.Crate;
import com.airdropmc.helpers.AirdropLogger;
import com.airdropmc.helpers.CrateManager;

public class CrateDestroyListener implements Listener {

	private final Plugin plugin;

	public CrateDestroyListener(Plugin plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent e) {
		if (e.getBlock().getType() != Material.BARREL) {
			return;
		}
		Location barrelLocation = e.getBlock().getLocation();
		Crate crate = CrateManager.getCrate(barrelLocation);
		BlockState state = e.getBlock().getState();
		if (crate == null || !(state instanceof Barrel barrel) || !crate.ownsLandedBarrel(barrel)) {
			return;
		}
		try {
			Bukkit.getScheduler().runTask(plugin,
					() -> CrateManager.finalizeCrateRemoval(barrelLocation, crate));
		} catch (RuntimeException failure) {
			AirdropLogger.log(Level.WARNING,
					"Could not schedule landed crate break reconciliation", failure);
		}
	}
}
