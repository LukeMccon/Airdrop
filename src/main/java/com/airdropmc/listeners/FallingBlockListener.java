package com.airdropmc.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import com.airdropmc.helpers.CrateList;
import com.airdropmc.tasks.RenderPackageSpecialEffectTask;
import com.airdropmc.Airdrop;
import com.airdropmc.Crate;

import java.util.Map;

public class FallingBlockListener implements Listener {

	@EventHandler(priority = EventPriority.NORMAL)
	public void onEntityChangeBlockEvent(EntityChangeBlockEvent e) {

		Map<FallingBlock, Crate> crateMap = CrateList.getCrateMap();
		Entity entity = e.getEntity();

		if (crateMap.containsKey(entity)) {
			e.setCancelled(true);
			Location loc = entity.getLocation();
			Crate aCrate = crateMap.get(entity);
			aCrate.setChestBlock(loc.getBlock());
			aCrate.spawnChest();
			crateMap.remove(entity);
			RenderPackageSpecialEffectTask effect = new RenderPackageSpecialEffectTask(loc);
			effect.runTaskTimerAsynchronously(Airdrop.getPluginInstance(), 0L, 1L);
		}
	}
}
