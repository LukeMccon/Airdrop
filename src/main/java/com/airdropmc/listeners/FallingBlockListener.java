package com.airdropmc.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import com.airdropmc.helpers.CrateList;
import com.airdropmc.Crate;

public class FallingBlockListener implements Listener {
		
	@EventHandler(priority = EventPriority.NORMAL)
	public void onEntityChangeBlockEvent(EntityChangeBlockEvent e) {

		Entity entity = e.getEntity();

		Crate aCrate = CrateList.getCrateMap().get(entity);

		if (aCrate != null) {
			aCrate.setChestBlock(entity.getLocation().getBlock());
			aCrate.spawnChest();
			CrateList.getCrateMap().remove(entity);
		} else {
			e.setCancelled(true);
		}

	}
}
