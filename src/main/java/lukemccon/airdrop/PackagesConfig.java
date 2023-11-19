package lukemccon.airdrop;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import lukemccon.airdrop.helpers.ChatHandler;

import static lukemccon.airdrop.Airdrop.PLUGIN_NAME;

public class PackagesConfig {

	PackagesConfig() {

	}
	private static YamlConfiguration yaml;
	private static final String PACKAGES_FILE_NAME = "packages.yml";
	private static File f;
	private static boolean loaded = false;
	
	public static void loadConfig() {
		f = new File(Bukkit.getServer().getPluginManager().getPlugin(PLUGIN_NAME).getDataFolder(), PACKAGES_FILE_NAME);
		if(f.exists()) {
			yaml = YamlConfiguration.loadConfiguration(f);
			loaded = true;
		} else {
			f = new File(Bukkit.getServer().getPluginManager().getPlugin(PLUGIN_NAME).getDataFolder(), PACKAGES_FILE_NAME);
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

	public static void saveConfig(YamlConfiguration config) {
		f = new File(Bukkit.getServer().getPluginManager().getPlugin(PLUGIN_NAME).getDataFolder(), PACKAGES_FILE_NAME);
		try {
			config.save(f);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static YamlConfiguration getConfig() {
		if(!loaded) {
			loadConfig();
		}
		return yaml;
	}
	
	private static void writeDefaults() throws IOException {
		yaml.createSection("packages.starter");
		List<ItemStack> items = Arrays.asList(new ItemStack(Material.IRON_HELMET,1),
				new ItemStack(Material.IRON_CHESTPLATE, 1),
				new ItemStack(Material.IRON_LEGGINGS, 1),
				new ItemStack(Material.IRON_BOOTS, 1),
				new ItemStack(Material.BREAD, 2));
		yaml.set("packages.starter.items", items);
		yaml.set("packages.starter.price", 10.0);
		yaml.save(f);
	}

}
