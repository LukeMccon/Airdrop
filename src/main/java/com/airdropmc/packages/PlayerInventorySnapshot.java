package com.airdropmc.packages;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class PlayerInventorySnapshot {
	private ItemStack[] contents = new ItemStack[0];
	private ItemStack cursorItem;
	private boolean captured;
	private boolean restored;

	void capture(Player player) {
		contents = cloneItemStacks(player.getInventory().getContents());
		cursorItem = cloneItemStack(player.getItemOnCursor());
		captured = true;
		restored = false;
	}

	void restore(Player player) {
		if (!captured || restored) {
			return;
		}

		player.getInventory().setContents(cloneItemStacks(contents));
		player.setItemOnCursor(cloneItemStack(cursorItem));
		player.updateInventory();
		restored = true;
	}

	private static ItemStack[] cloneItemStacks(ItemStack[] items) {
		ItemStack[] cloned = new ItemStack[items.length];
		for (int i = 0; i < items.length; i++) {
			cloned[i] = cloneItemStack(items[i]);
		}
		return cloned;
	}

	private static ItemStack cloneItemStack(ItemStack item) {
		return item == null ? null : item.clone();
	}
}
