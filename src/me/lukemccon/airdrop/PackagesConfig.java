package me.lukemccon.airdrop;

import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import me.lukemccon.airdrop.helpers.ChatHandler;

public class PackagesConfig {
	
	private static YamlConfiguration yaml;
	private static File f;
	private static boolean loaded = false;
	
	public static void loadConfig() {
		f = new File(Bukkit.getServer().getPluginManager().getPlugin("Airdrop").getDataFolder(), "packages.yml");
		if(f.exists()) {
			yaml = YamlConfiguration.loadConfiguration(f);
			loaded = true;
		} else {
			f = new File(Bukkit.getServer().getPluginManager().getPlugin("Airdrop").getDataFolder(), "packages.yml");
			yaml = YamlConfiguration.loadConfiguration(f);
			try {
				yaml.save(f);
				writeDefaults();
				loaded = true;
				ChatHandler.logMessage("Config loaded successfully");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static YamlConfiguration getConfig() {
		if(!loaded) {
			loadConfig();
		}
		return yaml;
	}
	
	private static void writeDefaults() throws IOException {
		yaml.createSection("packages.default");
		yaml.createSection("packages.starter");
		yaml.set("packages.starter.items.1", new ItemStack(Material.IRON_HELMET,1));
		yaml.set("packages.starter.items.2", new ItemStack(Material.IRON_CHESTPLATE, 1));
		yaml.set("packages.starter.items.3", new ItemStack(Material.IRON_LEGGINGS, 1));
		yaml.set("packages.starter.items.4", new ItemStack(Material.IRON_BOOTS, 1));
		yaml.set("packages.starter.items.5", new ItemStack(Material.BREAD, 2));
		yaml.save(f);
	}

}
