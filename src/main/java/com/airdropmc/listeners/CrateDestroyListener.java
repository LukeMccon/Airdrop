package com.airdropmc.listeners;

import java.util.Objects;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;

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
		try {
			Bukkit.getScheduler().runTask(plugin,
					() -> CrateManager.finalizeCrateBreak(barrelLocation));
		} catch (RuntimeException failure) {
			AirdropLogger.log(Level.WARNING,
					"Could not schedule landed crate break reconciliation", failure);
		}
	}
}
