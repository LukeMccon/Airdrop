package com.airdropmc.listeners;

import java.util.List;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import com.airdropmc.helpers.CrateManager;

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
			CrateManager.removeCrate(e.getBlock().getLocation());
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onChunkUnload(ChunkUnloadEvent e) {
		Chunk chunk = e.getChunk();
		// Landed crates are persistent barrels and should remain tracked after unload.
		CrateManager.removeFallingCratesInChunk(chunk);
	}

	private void removeCrates(List<Block> blocks) {
		for (Block block : blocks) {
			if (block.getType() == Material.BARREL) {
				CrateManager.removeCrate(block.getLocation());
			}
		}
	}
}
