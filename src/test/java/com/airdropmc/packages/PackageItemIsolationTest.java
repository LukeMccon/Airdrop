package com.airdropmc.packages;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageItemIsolationTest {

	@Test
	void setItemsRemainsPublic() throws NoSuchMethodException {
		Method setItems = Package.class.getDeclaredMethod("setItems", List.class);

		assertTrue(Modifier.isPublic(setItems.getModifiers()));
	}

	@Test
	void constructorClonesItemStacks() {
		ItemStack source = namedItem(Material.STONE, 2, "original");
		Package pkg = new Package("starter", 1.0, List.of(source));

		mutate(source);

		assertOriginal(pkg.getItems().getFirst(), Material.STONE, 2);
	}

	@Test
	void setItemsClonesItemStacks() {
		Package pkg = new Package("starter", 1.0, List.of());
		ItemStack source = namedItem(Material.DIRT, 3, "original");

		pkg.setItems(List.of(source));
		mutate(source);

		assertOriginal(pkg.getItems().getFirst(), Material.DIRT, 3);
	}

	@Test
	void getItemsReturnsClonedItemStacks() {
		Package pkg = new Package("starter", 1.0, List.of(
				namedItem(Material.DIAMOND, 4, "original")));

		mutate(pkg.getItems().getFirst());

		assertOriginal(pkg.getItems().getFirst(), Material.DIAMOND, 4);
	}

	@Test
	void nullAndEmptyInputsRemainEmptyAndNullEntriesArePreserved() {
		Package pkg = new Package("starter", 1.0, null);

		assertTrue(pkg.getItems().isEmpty());

		pkg.setItems(List.of());
		assertTrue(pkg.getItems().isEmpty());

		pkg.setItems(java.util.Arrays.asList(null, new ItemStack(Material.STONE, 1)));
		assertEquals(2, pkg.getItems().size());
		assertNull(pkg.getItems().getFirst());
	}

	private static ItemStack namedItem(Material material, int amount, String name) {
		ItemStack item = new ItemStack(material, amount);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(name);
		item.setItemMeta(meta);
		return item;
	}

	private static void mutate(ItemStack item) {
		item.setAmount(9);
		item.setType(Material.GOLD_BLOCK);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName("mutated");
		item.setItemMeta(meta);
	}

	private static void assertOriginal(ItemStack item, Material material, int amount) {
		assertEquals(material, item.getType());
		assertEquals(amount, item.getAmount());
		assertEquals("original", item.getItemMeta().getDisplayName());
	}
}
