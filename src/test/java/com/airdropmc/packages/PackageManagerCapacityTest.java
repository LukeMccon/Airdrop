package com.airdropmc.packages;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackageManagerCapacityTest {

	@Test
	void sanitizePackageItems_filtersNullAndAirStacks() {
		List<ItemStack> items = new ArrayList<>();
		ItemStack airStack = mock(ItemStack.class);
		when(airStack.getType()).thenReturn(Material.AIR);
		items.add(new ItemStack(Material.DIRT, 1));
		items.add(null);
		items.add(airStack);
		items.add(new ItemStack(Material.STONE, 1));

		List<ItemStack> sanitized = PackageManager.sanitizePackageItems(items);

		assertEquals(2, sanitized.size());
		assertTrue(sanitized.stream().noneMatch(item -> item == null || item.getType().isAir()));
	}

	@Test
	void getFilteredItemCount_reportsMoreThanBarrelCapacity() {
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < PackageManager.MAX_PACKAGE_ITEM_STACKS + 3; i++) {
			items.add(new ItemStack(Material.DIRT, 1));
		}

		int filteredItemCount = PackageManager.getFilteredItemCount(items);

		assertEquals(PackageManager.MAX_PACKAGE_ITEM_STACKS + 3, filteredItemCount);
	}
}
