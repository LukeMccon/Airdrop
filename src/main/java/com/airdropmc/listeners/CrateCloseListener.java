package com.airdropmc.listeners;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;

import com.airdropmc.Crate;
import com.airdropmc.helpers.CrateManager;

public class CrateCloseListener implements Listener {

	@EventHandler(priority = EventPriority.NORMAL)
	public void onInventoryClose(InventoryCloseEvent e) {
		if (e.getInventory().getType() != InventoryType.BARREL)
			return;

		if (!(e.getInventory().getHolder() instanceof Barrel barrel)) {
			return;
		}

		Location barrelLocation = barrel.getBlock().getLocation();
		Crate expectedCrate = CrateManager.getCrate(barrelLocation);
		if (expectedCrate == null || !expectedCrate.ownsLandedBarrel(barrel)) {
			return;
		}

		Block currentBlock = barrel.getBlock();
		if (currentBlock.getType() != Material.BARREL
				|| !(currentBlock.getState() instanceof Barrel currentBarrel)
				|| !expectedCrate.ownsLandedBarrel(currentBarrel)
				|| !e.getInventory().equals(currentBarrel.getInventory())
				|| !currentBarrel.getInventory().isEmpty()) {
			return;
		}

		if (CrateManager.removeCrateAndDestroy(barrelLocation, expectedCrate)) {
			barrel.getWorld().playEffect(barrelLocation, Effect.STEP_SOUND, Material.BARREL);
		}
	}
}
