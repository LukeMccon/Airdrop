package com.airdropmc.packages;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;

final class PackageEditorInteraction {
	enum VirtualAction {
		DENY,
		CONTROL,
		FULL_STACK,
		SINGLE_ITEM
	}

	private PackageEditorInteraction() {
	}

	static VirtualAction classify(
			ClickType click,
			InventoryAction action,
			ItemStack cursor,
			boolean controlSlot) {
		if (cursor != null && !cursor.getType().isAir()) {
			return VirtualAction.DENY;
		}

		if (click == ClickType.LEFT && action == InventoryAction.PICKUP_ALL) {
			return controlSlot ? VirtualAction.CONTROL : VirtualAction.FULL_STACK;
		}

		if (!controlSlot && click == ClickType.RIGHT && action == InventoryAction.PICKUP_HALF) {
			return VirtualAction.SINGLE_ITEM;
		}

		return VirtualAction.DENY;
	}
}
