package lukemccon.airdrop.packages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
		String printStr = "Available Packages\n";
		printStr += "=============== \n" + ChatColor.AQUA;
				for (String s: getPackages()) {
					printStr += s + "\n";
				}
		return printStr;
	}
	
	public static Set<String> getPackages() {
		return config.getKeys(false);
	}

	public static Package get(String key) throws PackageNotFoundException {
		Package pkg = packages.get(key);

		if (pkg == null) {
			throw new PackageNotFoundException(key);
		}
		return pkg;
	}
	
	public static void populatePackages() {
		
		for (String pkg : getPackages()) {
			ArrayList<ItemStack> items = new ArrayList<ItemStack>();
			Set<String> section = null;
			try {
				section = ((ConfigurationSection) config.get(pkg + ".items")).getKeys(false);
			} catch (NullPointerException e) {

			}

			if (section != null && !section.isEmpty()) {
				for (String item : section) {
					items.add(config.getItemStack(pkg + ".items." + item));
				}
				String name = pkg;
				Double price = 0.0;

				try {
					price = config.getDouble(pkg + ".price");
				} catch (Exception e) {
					System.out.println("Could not find price for package: " + name);
				}

				PackageManager.packages.put(name, new Package(name, price, items ));

			}


		}
	}
	
	public static Boolean has(String packageName) {
		return getPackages().contains(packageName);
	}

	public static ArrayList<ItemStack> getItems(String packageName) throws PackageNotFoundException {
		ArrayList<ItemStack> items = null;

		try {
			Package foundPackage = PackageManager.packages.get(packageName);
			items = foundPackage.getItems();
		} catch (Exception e) {
			throw new PackageNotFoundException(packageName);
		}

		return items;
	}
	
	public static String getInfo(String packageName) throws PackageNotFoundException {
		Package p = PackageManager.get(packageName);
		return p.toString();
	}

	public static int getNumberofPackages() {
		return packages.size();
	}
	
}
