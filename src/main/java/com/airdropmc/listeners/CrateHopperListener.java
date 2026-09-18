package com.airdropmc.listeners;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import com.airdropmc.Crate;
import com.airdropmc.api.RetirementReason;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.limits.DropLocationKey;

public class CrateHopperListener implements Listener {

	private final Consumer<Runnable> nextTickScheduler;

	public CrateHopperListener(Plugin plugin) {
		Plugin requiredPlugin = Objects.requireNonNull(plugin, "plugin");
		this.nextTickScheduler = task -> Bukkit.getScheduler().runTask(requiredPlugin, task);
	}

	CrateHopperListener(Consumer<Runnable> nextTickScheduler) {
		this.nextTickScheduler = Objects.requireNonNull(nextTickScheduler, "nextTickScheduler");
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onInventoryMoveItem(InventoryMoveItemEvent e) {
		Inventory source = e.getSource();
		if (source.getType() != InventoryType.BARREL) {
			return;
		}
		if (!(source.getHolder() instanceof Barrel sourceBarrel)
				|| !source.equals(sourceBarrel.getInventory())) {
			return;
		}
		Location barrelLocation = sourceBarrel.getLocation();
		Crate expectedCrate = CrateManager.getCrate(barrelLocation);
		if (expectedCrate == null) {
			return;
		}
		DropLocationKey locationKey = DropLocationKey.from(barrelLocation);

		nextTickScheduler.accept(
				() -> cleanupCrateAfterExtraction(locationKey, expectedCrate, sourceBarrel));
	}

	private void cleanupCrateAfterExtraction(
			DropLocationKey locationKey, Crate expectedCrate, Barrel sourceBarrel) {
		World world = Bukkit.getWorld(locationKey.worldId());
		if (world == null) {
			return;
		}

		Location barrelLocation = new Location(world, locationKey.x(), locationKey.y(), locationKey.z());
		if (CrateManager.getCrate(barrelLocation) != expectedCrate
				|| !expectedCrate.ownsLandedBarrel(sourceBarrel)) {
			return;
		}
		if (!world.isChunkLoaded(locationKey.x() >> 4, locationKey.z() >> 4)) {
			return;
		}

		Block block = world.getBlockAt(locationKey.x(), locationKey.y(), locationKey.z());
		if (block.getType() != Material.BARREL || !(block.getState() instanceof Barrel barrel)
				|| !expectedCrate.ownsLandedBarrel(barrel)) {
			CrateManager.removeCrateAndDestroy(
					barrelLocation, expectedCrate, RetirementReason.HOPPER_EMPTY);
			return;
		}
		if (!barrel.getInventory().isEmpty()) {
			return;
		}

		CrateManager.removeCrateAndDestroy(
				barrelLocation, expectedCrate, RetirementReason.HOPPER_EMPTY);
	}
}
