package com.airdropmc.packages;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.airdropmc.helpers.ChatTheme;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Represents a package within the airdrop
 * Which includes the name, price, and items
 */
public class Package {

	private List<ItemStack> items;
	private double price;
	private String name;

	public Package(String name, double price, List<ItemStack> items) {
		this.name = name;
		this.price = validatePrice(price);
		this.setItems(items);
	}

	public static boolean isValidPrice(double price) {
		return Double.isFinite(price) && Double.compare(price, 0.0) >= 0;
	}

	private static double validatePrice(double price) {
		if (!isValidPrice(price)) {
			throw new IllegalArgumentException("Package price must be finite and non-negative");
		}
		return price;
	}

	public double getPrice() {
		return this.price;
	}

	public String getName() {
		return this.name;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (ItemStack item : this.items) {
			sb.append(formatItemStack(item));
		}
		return sb.toString();
	}

	/**
	 * Formats an ItemStack for human-readable display.
	 * Shows custom name in quotes with material in parentheses if present,
	 * enchantments with roman numeral levels, and lore lines.
	 */
	private String formatItemStack(ItemStack item) {
		StringBuilder sb = new StringBuilder();
		String text = ChatTheme.text().toString();
		String accent = ChatTheme.accent().toString();

		ItemMeta meta = item.getItemMeta();
		String materialName = formatMaterialName(item.getType().name());

		// Item name line
		sb.append(text).append("  ");
		if (meta != null && meta.hasDisplayName()) {
			sb.append(accent).append("\"").append(meta.getDisplayName()).append("\"")
					.append(text).append(" (").append(materialName).append(")");
		} else {
			sb.append(materialName);
		}
		sb.append(" x").append(item.getAmount()).append("\n");

		// Enchantments
		if (meta != null && meta.hasEnchants()) {
			sb.append(text).append("    ");
			List<String> enchantNames = new ArrayList<>();
			for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
				String enchantName = formatEnchantmentName(entry.getKey());
				String level = toRomanNumeral(entry.getValue());
				enchantNames.add(enchantName + " " + level);
			}
			sb.append(accent).append(String.join(text + ", " + accent, enchantNames)).append("\n");
		}

		// Lore
		if (meta != null && meta.hasLore()) {
			List<String> lore = meta.getLore();
			if (lore != null) {
				for (String line : lore) {
					sb.append(text).append("    ").append(line).append("\n");
				}
			}
		}

		return sb.toString();
	}

	/**
	 * Converts UPPER_SNAKE_CASE material names to Title Case.
	 * Example: DIAMOND_SWORD → Diamond Sword
	 */
	private String formatMaterialName(String name) {
		String[] words = name.toLowerCase(Locale.ROOT).split("_");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < words.length; i++) {
			if (i > 0) sb.append(" ");
			if (!words[i].isEmpty()) {
				sb.append(Character.toUpperCase(words[i].charAt(0)))
						.append(words[i].substring(1));
			}
		}
		return sb.toString();
	}

	/**
	 * Formats enchantment key to human-readable name.
	 * Example: minecraft:sharpness → Sharpness
	 */
	private String formatEnchantmentName(Enchantment enchantment) {
		String key = enchantment.getKey().getKey();
		return formatMaterialName(key);
	}

	/**
	 * Converts an integer to a Roman numeral.
	 */
	private String toRomanNumeral(int number) {
		if (number <= 0 || number > 255) return String.valueOf(number);
		String[] romanNumerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		if (number <= 10) return romanNumerals[number];
		// For levels > 10, just use the number
		return String.valueOf(number);
	}

	public List<ItemStack> getItems() {
		return cloneItems(this.items);
	}

	public void setItems(List<ItemStack> items) {
		if (items != null && !items.isEmpty()) {
			this.items = cloneItems(items);
		} else {
			this.items = new ArrayList<>();
		}
	}

	private static List<ItemStack> cloneItems(List<ItemStack> items) {
		List<ItemStack> clonedItems = new ArrayList<>(items.size());
		for (ItemStack item : items) {
			clonedItems.add(item != null ? item.clone() : null);
		}
		return clonedItems;
	}

}
