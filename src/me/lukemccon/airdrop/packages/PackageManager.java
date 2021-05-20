package me.lukemccon.airdrop.packages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import me.lukemccon.airdrop.PackagesConfig;
/**
 * Manages packages, keeps list of available packages and their contents
 * 
 * @author lukeMccon
 *
 */
public class PackageManager {

	public static HashMap<String, ArrayList<ItemStack>> packages = new HashMap<String, ArrayList<ItemStack>>();
	private static ConfigurationSection config = (ConfigurationSection) PackagesConfig.getConfig().get("packages");
	
	public static void reload() {
		// Force a reload from config
		PackagesConfig.loadConfig();
		config = (ConfigurationSection) PackagesConfig.getConfig().get("packages");
	}
	
	
	public static String list() {
		String printStr = "Avaliable Packages\n";
		printStr += "=============== \n";
				for (String s: getPackages()) {
					printStr += s + "\n";
				}
		return printStr;
	}
	
	public static Set<String> getPackages(){
		return config.getKeys(false);
	}
	
	public static Boolean has(String packageName) {
		return getPackages().contains(packageName);
	}
	
	public static String getInfo(String packageName) {
		return config.get(packageName).toString();
	}
}
