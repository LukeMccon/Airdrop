package com.airdropmc;

import java.util.Arrays;
import java.util.List;

import com.airdropmc.config.AbstractConfig;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.MessageKey;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Handles the packages.yml configuration file.
 */
public class PackagesConfig extends AbstractConfig {

	private static final String PACKAGES_FILE_NAME = "packages.yml";

	public PackagesConfig(Airdrop plugin) {
		super(plugin, PACKAGES_FILE_NAME);
	}

	@Override
	protected void onCreateDefaultConfig() {
		// Create new file with defaults
		setConfig(new org.bukkit.configuration.file.YamlConfiguration());
		writeDefaults();
		saveConfig();
		ChatHandler.logMessage(ChatHandler.get(MessageKey.SYSTEM_CONFIG_LOADED));
	}

	private void writeDefaults() {
		getConfig().createSection("packages.starter");
		List<ItemStack> items = Arrays.asList(
				new ItemStack(Material.IRON_HELMET, 1),
				new ItemStack(Material.IRON_CHESTPLATE, 1),
				new ItemStack(Material.IRON_LEGGINGS, 1),
				new ItemStack(Material.IRON_BOOTS, 1),
				new ItemStack(Material.BREAD, 2));
		getConfig().set("packages.starter.items", items);
		getConfig().set("packages.starter.price", 10.0);
	}
}
