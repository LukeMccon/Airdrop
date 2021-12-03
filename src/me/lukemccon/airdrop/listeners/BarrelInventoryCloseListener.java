package me.lukemccon.airdrop.listeners;

import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;

import me.lukemccon.airdrop.helpers.CrateList;

public class BarrelInventoryCloseListener implements Listener {
	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onInventoryClose(InventoryCloseEvent e) {
				
		if (e.getInventory().getType() != InventoryType.BARREL)
			return;
		
		Barrel barrel = (Barrel) e.getInventory().getHolder();
		
		if (CrateList.barrelList.contains(barrel.getBlock().getLocation())) {
			if(barrel.getInventory().isEmpty()) {
				barrel.getWorld().playEffect(barrel.getLocation(), Effect.STEP_SOUND, Material.BARREL);
				barrel.getBlock().setType(Material.AIR);
				CrateList.barrelList.remove(barrel.getBlock().getLocation());
			}
		}
		
	}

}
