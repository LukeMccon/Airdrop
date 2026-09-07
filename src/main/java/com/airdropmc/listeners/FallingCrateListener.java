package com.airdropmc.listeners;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import com.airdropmc.Crate;
import com.airdropmc.api.RetirementReason;
import com.airdropmc.api.WorldPosition;
import com.airdropmc.helpers.CrateManager;

public class FallingCrateListener implements Listener {

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onEntityChangeBlockEvent(EntityChangeBlockEvent e) {
		Entity entity = e.getEntity();

		if (!(entity instanceof FallingBlock)) {
			return;
		}

		FallingBlock fallingBlock = (FallingBlock) entity;
		Crate landedCrate = CrateManager.getCrate(fallingBlock);
		if (landedCrate == null) {
			return;
		}
		if (e.isCancelled()) {
			CrateManager.removeCrate(fallingBlock, RetirementReason.CANCELLED);
			fallingBlock.remove();
			landedCrate.cancelLanding();
			return;
		}
		Block landingBlock = e.getBlock();
		if (landingBlock == null) {
			e.setCancelled(true);
			CrateManager.removeCrate(fallingBlock, RetirementReason.FAILED);
			fallingBlock.remove();
			landedCrate.destroy();
			return;
		}
		boolean landingAllowed = landedCrate.beginLanding(
				WorldPosition.from(landingBlock.getLocation()));
		e.setCancelled(true);
		CrateManager.detachFallingForLanding(fallingBlock);
		// Paper keeps FallingBlock entities alive after event cancellation.
		// Explicitly remove it so it cannot fire again or place an empty barrel.
		fallingBlock.remove();
		if (!landingAllowed) {
			CrateManager.removeCrate(landedCrate, RetirementReason.CANCELLED);
			landedCrate.cancelLanding();
			return;
		}
		try {
			landedCrate.land(landingBlock);
			landedCrate.completeLanding();
		} catch (RuntimeException landFailure) {
			CrateManager.removeCrate(landedCrate, RetirementReason.FAILED);
			landedCrate.destroy();
			throw landFailure;
		}
	}
}
