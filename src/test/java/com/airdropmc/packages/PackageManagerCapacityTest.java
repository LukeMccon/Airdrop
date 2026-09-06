package com.airdropmc.packages;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkitExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockBukkitExtension.class)
class PackageManagerCapacityTest {

	@Test
	void sanitizePackageItems_filtersNullAndAirStacksAndClonesRetainedItems() {
		List<ItemStack> items = new ArrayList<>();
		ItemStack source = new ItemStack(Material.DIRT, 1);
		ItemStack airStack = mock(ItemStack.class);
		when(airStack.getType()).thenReturn(Material.AIR);
		items.add(source);
		items.add(null);
		items.add(airStack);
		items.add(new ItemStack(Material.STONE, 1));

		List<ItemStack> sanitized = PackageManager.sanitizePackageItems(items);

		assertEquals(2, sanitized.size());
		assertTrue(sanitized.stream().noneMatch(item -> item == null || item.getType().isAir()));
		assertNotSame(source, sanitized.getFirst());
	}

	@Test
	void getFilteredItemCount_reportsMoreThanBarrelCapacityBeforeTruncation() {
		List<ItemStack> items = itemStacks(PackageManager.MAX_PACKAGE_ITEM_STACKS + 3);

		int filteredItemCount = PackageManager.getFilteredItemCount(items);

		assertEquals(PackageManager.MAX_PACKAGE_ITEM_STACKS + 3, filteredItemCount);
	}

	@Test
	void materializePackages_sanitizesAndTruncatesItemsToBarrelCapacity() throws Exception {
		YamlConfiguration candidate = baseConfiguration();
		List<ItemStack> rawItems = itemStacks(PackageManager.MAX_PACKAGE_ITEM_STACKS + 3);
		candidate.set("packages.starter.items", rawItems);

		Map<String, Package> materialized = PackageManager.materializePackages(candidate);

		List<ItemStack> items = materialized.get("starter").getItems();
		assertEquals(PackageManager.MAX_PACKAGE_ITEM_STACKS, items.size());
		assertTrue(items.stream().allMatch(item -> item.getType() == Material.DIRT));
	}

	@Test
	void materializePackages_rejectsInvalidItemAfterBarrelCapacity() {
		YamlConfiguration candidate = baseConfiguration();
		List<Object> rawItems = new ArrayList<>(itemStacks(PackageManager.MAX_PACKAGE_ITEM_STACKS));
		rawItems.add("not-an-item");
		candidate.set("packages.starter.items", rawItems);

		PackageMaterializationException failure = assertThrows(PackageMaterializationException.class,
				() -> PackageManager.materializePackages(candidate));

		assertTrue(failure.getMessage().contains("starter"), failure::getMessage);
		assertTrue(failure.getMessage().contains("index 27"), failure::getMessage);
	}

	@Test
	void materializePackages_preservesItemsNamedLikeControlsAcrossLanguages() throws Exception {
		YamlConfiguration candidate = baseConfiguration();
		List<ItemStack> namedItems = List.of(
				namedItem("Save"),
				namedItem("Cancel"),
				namedItem("Back"),
				namedItem("Help"),
				namedItem("Enregistrer"));
		candidate.set("packages.starter.items", namedItems);

		Map<String, Package> materialized = PackageManager.materializePackages(candidate);
		Map<String, Package> compatibilityMaterialized = PackageManager.materializePackages(
				candidate, Set.of("Save", "Cancel", "Back", "Help", "Enregistrer"));

		List<String> displayNames = materialized.get("starter").getItems().stream()
				.map(ItemStack::getItemMeta)
				.map(ItemMeta::getDisplayName)
				.toList();
		List<String> compatibilityDisplayNames = compatibilityMaterialized.get("starter").getItems().stream()
				.map(ItemStack::getItemMeta)
				.map(ItemMeta::getDisplayName)
				.toList();
		assertEquals(List.of("Save", "Cancel", "Back", "Help", "Enregistrer"), displayNames);
		assertEquals(displayNames, compatibilityDisplayNames);
	}

	@Test
	void updateCandidate_persistsOnlySanitizedBarrelCapacityAndDetachesCallerItems() throws Exception {
		YamlConfiguration source = baseConfiguration();
		List<ItemStack> requestedItems = itemStacks(PackageManager.MAX_PACKAGE_ITEM_STACKS + 3);
		ItemStack firstCallerItem = requestedItems.getFirst();

		YamlConfiguration candidate = PackageManager.updatePackageInventoryCandidate(
				source, "STARTER", requestedItems);
		firstCallerItem.setAmount(7);

		List<?> persistedItems = candidate.getList("packages.starter.items");
		assertEquals(PackageManager.MAX_PACKAGE_ITEM_STACKS, persistedItems.size());
		ItemStack persistedFirst = (ItemStack) persistedItems.getFirst();
		assertNotSame(firstCallerItem, persistedFirst);
		assertEquals(1, persistedFirst.getAmount());
		assertEquals(PackageManager.MAX_PACKAGE_ITEM_STACKS,
				PackageManager.materializePackages(candidate).get("starter").getItems().size());
	}

	private static YamlConfiguration baseConfiguration() {
		YamlConfiguration config = new YamlConfiguration();
		config.createSection("packages.starter");
		config.set("packages.starter.price", 0.0);
		config.set("packages.starter.items", List.of());
		return config;
	}

	private static ItemStack namedItem(String displayName) {
		ItemStack item = new ItemStack(Material.PAPER, 1);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(displayName);
		item.setItemMeta(meta);
		return item;
	}

	private static List<ItemStack> itemStacks(int count) {
		List<ItemStack> items = new ArrayList<>();
		for (int index = 0; index < count; index++) {
			items.add(new ItemStack(Material.DIRT, 1));
		}
		return items;
	}
}
