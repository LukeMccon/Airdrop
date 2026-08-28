package com.airdropmc.listeners;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import com.airdropmc.Airdrop;
import com.airdropmc.helpers.AirdropLogger;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.limits.DropAdmissionController;

public class CrateCleanupListener implements Listener {

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent e) {
		removeCrates(e.blockList());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent e) {
		removeCrates(e.blockList());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBurn(BlockBurnEvent e) {
		if (e.getBlock().getType() == Material.BARREL) {
			CrateManager.removeCrateAndDestroy(e.getBlock().getLocation());
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onChunkUnload(ChunkUnloadEvent e) {
		CrateManager.prepareChunkForUnload(e.getChunk());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkLoad(ChunkLoadEvent e) {
		Airdrop plugin = Airdrop.getPluginInstance();
		DropAdmissionController admission = Airdrop.getDropAdmissionController();
		if (plugin != null && admission != null && Airdrop.isReady()
				&& !Airdrop.isShuttingDown()) {
			CrateManager.recoverCratesInChunk(plugin, admission, e.getChunk());
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onWorldUnload(WorldUnloadEvent e) {
		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin == null || !CrateManager.prepareWorldForUnload(e.getWorld(), plugin)) {
			e.setCancelled(true);
			return;
		}

		UUID worldId = e.getWorld().getUID();
		try {
			Bukkit.getScheduler().runTask(plugin, () -> {
				World loaded = Bukkit.getWorld(worldId);
				DropAdmissionController admission = Airdrop.getDropAdmissionController();
				if (loaded != null && admission != null && Airdrop.isReady()
						&& !Airdrop.isShuttingDown()) {
					CrateManager.recoverLoadedCratesInWorld(plugin, admission, loaded);
				}
			});
		} catch (RuntimeException failure) {
			AirdropLogger.log(Level.WARNING,
					"Could not schedule paid crate reconciliation after world unload", failure);
			e.setCancelled(true);
			DropAdmissionController admission = Airdrop.getDropAdmissionController();
			if (admission != null && Airdrop.isReady() && !Airdrop.isShuttingDown()) {
				try {
					CrateManager.recoverLoadedCratesInWorld(plugin, admission, e.getWorld());
				} catch (RuntimeException recoveryFailure) {
					AirdropLogger.log(Level.SEVERE,
							"Could not reconcile paid crates after cancelling world unload",
							recoveryFailure);
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorldLoad(WorldLoadEvent e) {
		Airdrop plugin = Airdrop.getPluginInstance();
		DropAdmissionController admission = Airdrop.getDropAdmissionController();
		if (plugin != null && admission != null && Airdrop.isReady()
				&& !Airdrop.isShuttingDown()) {
			CrateManager.recoverLoadedCratesInWorld(plugin, admission, e.getWorld());
		}
	}

	private void removeCrates(List<Block> blocks) {
		for (Block block : blocks) {
			if (block.getType() == Material.BARREL) {
				CrateManager.removeCrateAndDestroy(block.getLocation());
			}
		}
	}
}
