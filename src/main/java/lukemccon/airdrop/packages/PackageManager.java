package lukemccon.airdrop.packages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import lukemccon.airdrop.exceptions.PackageNotFoundException;
import lukemccon.airdrop.PackagesConfig;
/**
 * Manages packages, keeps list of available packages and their contents
 * 
 * @author lukeMccon
 *
 */
public class PackageManager {
	
	
	PackageManager(){
		
	}

	public static HashMap<String, Package> packages = new HashMap<String, Package>();
	private static YamlConfiguration fileConfig = PackagesConfig.getConfig();
	private static ConfigurationSection config = (ConfigurationSection) fileConfig.get("packages");
	
	public static void reload() {
		// Force a reload from config
		PackagesConfig.loadConfig();
		config = (ConfigurationSection) PackagesConfig.getConfig().get("packages");
		PackageManager.populatePackages();
	}
	
	
	public static String list() {
		String printStr = "Avaliable Packages\n";
		printStr += "=============== \n";
				for (String s: getPackages()) {
					printStr += s + "\n";
				}
		return printStr;
	}
	
	public static Set<String> getPackages() {
		return config.getKeys(false);
	}

	public static Package get(String key) {
		return packages.get((Object) key);
	}
	
	public static void populatePackages() {
		
		for (String pkg : getPackages()) {
			ArrayList<ItemStack> items = new ArrayList<ItemStack>();
			for (String item : ((ConfigurationSection) config.get(pkg + ".items")).getKeys(false)) {
				items.add(config.getItemStack(pkg + ".items." + item));
			}
			String name = pkg;
			PackageManager.packages.put(name, new Package(name, 0.0, items ));
			
		}
	}
	
	public static Boolean has(String packageName) {
		return getPackages().contains(packageName);
	}
	
	public static String getInfo(String packageName) throws PackageNotFoundException {
		String packageInfoString = null;

		try {
			packageInfoString = PackageManager.packages.get(packageName).getItems().toString();
		} catch (Exception e) {
			throw new PackageNotFoundException(packageName);
		}
		return packageInfoString;

	}
}
