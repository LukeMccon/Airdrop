package me.gregoryw.airdrop;

import java.io.File;
import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

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
		yaml.set("packages.default.items.1", new ItemStack(Material.STICK, 3));
		yaml.set("packages.default.items.2", new ItemStack(Material.OAK_PLANKS, 32));
		yaml.save(f);
	}

}
