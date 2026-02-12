package com.airdropmc.listeners;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;

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
		if (CrateManager.getCrate(barrelLocation) == null) {
			return;
		}

		boolean barrelInventoryIsEmpty = barrel.getInventory().isEmpty();

		if (barrelInventoryIsEmpty) {
			barrel.getWorld().playEffect(barrel.getLocation(), Effect.STEP_SOUND, Material.BARREL);
			CrateManager.removeCrateAndDestroy(barrelLocation);
		}
	}
}
