package com.airdropmc.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import com.airdropmc.helpers.CrateList;
import com.airdropmc.Crate;

import java.util.Map;

public class FallingBlockListener implements Listener {
		
	@EventHandler(priority = EventPriority.NORMAL)
	public void onEntityChangeBlockEvent(EntityChangeBlockEvent e) {


		Map<FallingBlock, Crate> crateMap = CrateList.getCrateMap();

		if (crateMap.containsKey(e.getEntity())) {
			e.setCancelled(true);
			Crate aCrate = crateMap.get(e.getEntity());
			aCrate.setChestBlock(e.getEntity().getLocation().getBlock());
			aCrate.spawnChest();
			crateMap.remove(e.getEntity());
		}
	}
}
