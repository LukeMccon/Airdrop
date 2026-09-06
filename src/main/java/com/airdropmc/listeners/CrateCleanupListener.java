package com.airdropmc.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
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
import org.bukkit.plugin.Plugin;

import com.airdropmc.Airdrop;
import com.airdropmc.Crate;
import com.airdropmc.helpers.AirdropLogger;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.limits.DropAdmissionController;

public class CrateCleanupListener implements Listener {

	private final Plugin plugin;

	/**
	 * Compatibility constructor for integrations compiled against the former listener API.
	 *
	 * @deprecated use {@link #CrateCleanupListener(Plugin)} to provide the scheduler owner explicitly
	 */
	@Deprecated(forRemoval = false)
	@SuppressWarnings("java:S1133") // Retained for binary compatibility with existing integrations.
	public CrateCleanupListener() {
		this.plugin = null;
	}

	public CrateCleanupListener(Plugin plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent e) {
		reconcileExplodedCrates(e.blockList());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent e) {
		reconcileExplodedCrates(e.blockList());
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
		Airdrop airdrop = Airdrop.getPluginInstance();
		DropAdmissionController admission = Airdrop.getDropAdmissionController();
		if (airdrop != null && admission != null && Airdrop.isReady()
				&& !Airdrop.isShuttingDown()) {
			CrateManager.recoverCratesInChunk(airdrop, admission, e.getChunk());
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onWorldUnload(WorldUnloadEvent e) {
		Airdrop airdrop = Airdrop.getPluginInstance();
		if (airdrop == null || !CrateManager.prepareWorldForUnload(e.getWorld(), airdrop)) {
			e.setCancelled(true);
			return;
		}

		UUID worldId = e.getWorld().getUID();
		try {
			Bukkit.getScheduler().runTask(airdrop, () -> {
				World loaded = Bukkit.getWorld(worldId);
				DropAdmissionController admission = Airdrop.getDropAdmissionController();
				if (loaded != null && admission != null && Airdrop.isReady()
						&& !Airdrop.isShuttingDown()) {
					CrateManager.recoverLoadedCratesInWorld(airdrop, admission, loaded);
				}
			});
		} catch (RuntimeException failure) {
			AirdropLogger.log(Level.WARNING,
					"Could not schedule paid crate reconciliation after world unload", failure);
			e.setCancelled(true);
			DropAdmissionController admission = Airdrop.getDropAdmissionController();
			if (admission != null && Airdrop.isReady() && !Airdrop.isShuttingDown()) {
				try {
					CrateManager.recoverLoadedCratesInWorld(airdrop, admission, e.getWorld());
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
		Airdrop airdrop = Airdrop.getPluginInstance();
		DropAdmissionController admission = Airdrop.getDropAdmissionController();
		if (airdrop != null && admission != null && Airdrop.isReady()
				&& !Airdrop.isShuttingDown()) {
			CrateManager.recoverLoadedCratesInWorld(airdrop, admission, e.getWorld());
		}
	}

	private void reconcileExplodedCrates(List<Block> blocks) {
		List<PendingCrateRemoval> pending = new ArrayList<>();
		for (Block block : blocks) {
			if (block.getType() != Material.BARREL) {
				continue;
			}
			Location location = block.getLocation();
			Crate crate = CrateManager.getCrate(location);
			BlockState state = block.getState();
			if (crate != null && state instanceof Barrel barrel && crate.ownsLandedBarrel(barrel)) {
				pending.add(new PendingCrateRemoval(location, crate));
			}
		}
		if (pending.isEmpty()) {
			return;
		}
		Plugin schedulerPlugin = plugin == null ? Airdrop.getPluginInstance() : plugin;
		if (schedulerPlugin == null) {
			AirdropLogger.warning("Could not schedule landed crate explosion reconciliation"
					+ " because Airdrop is unavailable");
			return;
		}
		try {
			Bukkit.getScheduler().runTask(schedulerPlugin, () -> {
				for (PendingCrateRemoval removal : pending) {
					CrateManager.finalizeCrateBreak(removal.location(), removal.crate());
				}
			});
		} catch (RuntimeException failure) {
			AirdropLogger.log(Level.WARNING,
					"Could not schedule landed crate explosion reconciliation", failure);
		}
	}

	private record PendingCrateRemoval(Location location, Crate crate) {
	}
}
