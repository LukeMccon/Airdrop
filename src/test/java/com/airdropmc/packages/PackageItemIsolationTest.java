package com.airdropmc.packages;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageItemIsolationTest {

	@Test
	void constructorClonesItemStacks() {
		ItemStack source = new ItemStack(Material.STONE, 2);
		Package pkg = new Package("starter", 1.0, List.of(source));

		source.setAmount(7);

		assertEquals(2, pkg.getItems().getFirst().getAmount());
	}

	@Test
	void setItemsClonesItemStacks() {
		Package pkg = new Package("starter", 1.0, List.of());
		ItemStack source = new ItemStack(Material.DIRT, 3);

		pkg.setItems(List.of(source));
		source.setAmount(8);

		assertEquals(3, pkg.getItems().getFirst().getAmount());
	}

	@Test
	void getItemsReturnsClonedItemStacks() {
		Package pkg = new Package("starter", 1.0,
				List.of(new ItemStack(Material.DIAMOND, 4)));

		pkg.getItems().getFirst().setAmount(9);

		assertEquals(4, pkg.getItems().getFirst().getAmount());
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
}
