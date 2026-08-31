package com.airdropmc.api;

import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** An immutable package definition detached from Airdrop's live registry. */
public final class AirdropPackage {

	private final String name;
	private final BigDecimal price;
	private final List<ItemStack> items;

	/**
	 * Creates a package snapshot.
	 *
	 * <p>This constructor must be called on the primary server thread because
	 * it copies Bukkit {@link ItemStack} values.</p>
	 *
	 * @param name configured package name
	 * @param price non-negative exact price
	 * @param items package item stacks to copy
	 */
	public AirdropPackage(String name, BigDecimal price, List<ItemStack> items) {
		this.name = requireName(name);
		this.price = Objects.requireNonNull(price, "price");
		if (price.signum() < 0) {
			throw new IllegalArgumentException("price must be non-negative");
		}
		this.items = cloneItems(Objects.requireNonNull(items, "items"));
	}

	/**
	 * Returns the configured package name.
	 *
	 * @return configured package name
	 */
	public String name() {
		return name;
	}

	/**
	 * Returns the exact package price.
	 *
	 * @return exact non-negative package price
	 */
	public BigDecimal price() {
		return price;
	}

	/**
	 * Returns an unmodifiable list containing fresh copies of every item.
	 *
	 * <p>This method must be called on the primary server thread because it
	 * returns Bukkit {@link ItemStack} values.</p>
	 *
	 * @return unmodifiable detached item copies
	 */
	public List<ItemStack> items() {
		return Collections.unmodifiableList(cloneItems(items));
	}

	private static String requireName(String value) {
		String required = Objects.requireNonNull(value, "name");
		if (required.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		return required;
	}

	private static List<ItemStack> cloneItems(List<ItemStack> source) {
		List<ItemStack> copies = new ArrayList<>(source.size());
		for (ItemStack item : source) {
			copies.add(Objects.requireNonNull(item, "items must not contain null").clone());
		}
		return copies;
	}
}
