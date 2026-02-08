package com.airdropmc.packages;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagePriceValidationTest {

	@Test
	void isValidPrice_acceptsFiniteNonNegativeValues() {
		assertTrue(Package.isValidPrice(0.0));
		assertTrue(Package.isValidPrice(12.5));
	}

	@Test
	void isValidPrice_rejectsNegativeAndNonFiniteValues() {
		assertFalse(Package.isValidPrice(-0.01));
		assertFalse(Package.isValidPrice(Double.NaN));
		assertFalse(Package.isValidPrice(Double.POSITIVE_INFINITY));
		assertFalse(Package.isValidPrice(Double.NEGATIVE_INFINITY));
	}

	@Test
	void constructor_rejectsInvalidPriceValues() {
		List<ItemStack> items = List.of(new ItemStack(Material.DIRT, 1));

		assertThrows(IllegalArgumentException.class, () -> new Package("badneg", -1.0, items));
		assertThrows(IllegalArgumentException.class, () -> new Package("badnan", Double.NaN, items));
		assertThrows(IllegalArgumentException.class, () -> new Package("badinf", Double.POSITIVE_INFINITY, items));
	}

	@Test
	void constructor_acceptsValidPriceValues() {
		List<ItemStack> items = List.of(new ItemStack(Material.DIRT, 1));

		assertDoesNotThrow(() -> new Package("ok", 0.0, items));
		assertDoesNotThrow(() -> new Package("ok2", 42.0, items));
	}
}
