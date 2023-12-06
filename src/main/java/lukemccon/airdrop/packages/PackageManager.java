package lukemccon.airdrop.packages;

import java.util.*;
import java.util.stream.Collectors;

import lukemccon.airdrop.Airdrop;
import lukemccon.airdrop.helpers.ChatHandler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.Configuration;
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
	
	public static final String PACKAGES = "packages";

	PackageManager(){
		
	}

	private static Map<String, Package> packages = new HashMap<>();
	private static final YamlConfiguration fileConfig = PackagesConfig.getConfig();
	private static ConfigurationSection config = (ConfigurationSection) fileConfig.get(PACKAGES);

	/**
	 * Syncs the package manager with the packages.yml file
	 */
	public static void reload() {
		// Force a reload from config
		PackagesConfig.loadConfig();
		config = (ConfigurationSection) PackagesConfig.getConfig().get(PACKAGES);
		PackageManager.populatePackages();
		Airdrop.getPluginInstance().setupPackageGuis();
	}

	/**
	 * Get all packages as a set from the config
	 * @return set of package names
	 */
	public static Set<String> getPackages() {
		return config.getKeys(false);
	}

	/**
	 * Get a package given the package name
	 * @param packageName name of package
	 * @return the package
	 * @throws PackageNotFoundException if the package does not exist
	 */
	public static Package get(String packageName) throws PackageNotFoundException {
		Package pkg = packages.get(packageName);

		if (pkg == null) {
			throw new PackageNotFoundException(packageName);
		}
		return pkg;
	}

	/**
	 * Initializes or updates the package manager with configuration from the config file
	 */
	private static void populatePackages() {
		
		for (String pkg : getPackages()) {
			ArrayList<ItemStack> items;
			ConfigurationSection section = (ConfigurationSection) config.get(pkg);

			if (section != null) {

				items = new ArrayList<>( (List<ItemStack>) config.getList(pkg + ".items"));

				String name = pkg;
				double price = 0.0;

				try {
					price = config.getDouble(pkg + ".price");
				} catch (Exception e) {
					ChatHandler.getLogger().warning("Could not find price for package: " + name);
				}
				PackageManager.packages.put(name, new Package(name, price, items ));
			}
		}
	}
	
	public static Boolean has(String packageName) {
		return getPackages().contains(packageName);
	}

	public static List<ItemStack> getItems(String packageName) throws PackageNotFoundException {
		ArrayList<ItemStack> items;

		try {
			Package foundPackage = PackageManager.packages.get(packageName);
			items = (ArrayList<ItemStack>) foundPackage.getItems();
		} catch (Exception e) {
			throw new PackageNotFoundException(packageName);
		}

		return items;
	}

	/**
	 * Gives information about a package as a string
	 * @param packageName of package to lookup
	 * @return information as a string
	 * @throws PackageNotFoundException if the package does not exist
	 */
	public static String getInfo(String packageName) throws PackageNotFoundException {
		Package p = PackageManager.get(packageName);
		return p.toString();
	}

	/**
	 * Given a package and a list of items, update the packages items
	 * @param packageName name of package to lookup
	 * @param items to update
	 * @throws PackageNotFoundException if the package doesn't exist
	 */
	public static void updatePackageInventory(String packageName, List<ItemStack> items) throws PackageNotFoundException {

		Package pkg;
		pkg = PackageManager.get(packageName);
		pkg.setItems(items);
		config.set(packageName + ".items", items.stream().filter(Objects::nonNull).filter(itemstack -> !PackageGui.isControlItemStack(itemstack)).toArray());

		fileConfig.set(PACKAGES, config);
		PackagesConfig.saveConfig(fileConfig);
		PackageManager.reload();
	}

	/**
	 * Create a new package
	 * @param pkg to create
	 */
	public static void createPackage(Package pkg) {
		config.set(pkg.getName() + ".price", pkg.getPrice());
		config.set(pkg.getName() + ".items", pkg.getItems().stream().filter(Objects::nonNull).filter(itemstack -> !PackageGui.isControlItemStack(itemstack)).toArray());
		fileConfig.set(PACKAGES, config);
		PackagesConfig.saveConfig(fileConfig);
		PackageManager.reload();
	}

	/**
	 * Delete a package given a name
	 * @param packageName name of the package to delete
	 * @throws PackageNotFoundException package couldn't be found
	 */
	public static void deletePackage (String packageName) throws PackageNotFoundException {

		// Make sure the package exists
		get(packageName);
		config.set(packageName, null);
		fileConfig.set(PACKAGES, config);
		PackagesConfig.saveConfig(fileConfig);
		PackageManager.reload();
	}

}
