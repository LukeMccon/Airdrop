package lukemccon.airdrop.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import lukemccon.airdrop.helpers.CrateList;
import lukemccon.airdrop.Crate;

public class FallingBlockListener implements Listener {
		
	@EventHandler(priority = EventPriority.NORMAL)
	public void onEntityChangeBlockEvent(EntityChangeBlockEvent e) {

		Entity entity = e.getEntity();

		Crate aCrate = CrateList.crateMap.get(entity);

		if (aCrate != null) {
			aCrate.setChestBlock(entity.getLocation().getBlock());
			aCrate.spawnChest();
			CrateList.crateMap.remove(entity);
		} else {
			e.setCancelled(true);
		}

	}
}
