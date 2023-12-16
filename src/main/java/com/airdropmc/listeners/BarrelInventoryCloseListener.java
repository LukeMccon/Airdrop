package com.airdropmc.listeners;

import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;

import com.airdropmc.helpers.CrateList;

public class BarrelInventoryCloseListener implements Listener {
	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onInventoryClose(InventoryCloseEvent e) {
				
		if (e.getInventory().getType() != InventoryType.BARREL)
			return;
		
		Barrel barrel = (Barrel) e.getInventory().getHolder();

        if (barrel == null) {
			return;
		}

        boolean barrelListHasLocation = CrateList.getBarrelList().contains(barrel.getBlock().getLocation());
		boolean barrelInventoryIsEmpty = barrel.getInventory().isEmpty();

		if(barrelListHasLocation && barrelInventoryIsEmpty) {
			barrel.getWorld().playEffect(barrel.getLocation(), Effect.STEP_SOUND, Material.BARREL);
			barrel.getBlock().setType(Material.AIR);
			CrateList.getBarrelList().remove(barrel.getBlock().getLocation());
		}
		
	}

}
