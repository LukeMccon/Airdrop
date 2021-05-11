package me.lukemccon.airdrop.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import me.lukemccon.airdrop.Crate;
import me.lukemccon.airdrop.helpers.CrateList;

public class FallingBlockListener implements Listener {
		
	@EventHandler(priority = EventPriority.NORMAL)
	public void onEntityChangeBlockEvent(EntityChangeBlockEvent e) {
		
		if(CrateList.crateMap.containsKey(e.getEntity())) {
			e.setCancelled(true);
			Crate aCrate = CrateList.crateMap.get(e.getEntity());
			aCrate.setChestBlock(e.getEntity().getLocation().getBlock());
			aCrate.spawnChest();
			CrateList.crateMap.remove(e.getEntity());
		}

	}
}
