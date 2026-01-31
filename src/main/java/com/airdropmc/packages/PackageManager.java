package com.airdropmc.packages;

import java.util.*;

import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.Airdrop;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import com.airdropmc.exceptions.DuplicatePackageException;
import com.airdropmc.exceptions.PackageNotFoundException;


/**
 * Manages packages, keeps list of available packages and their contents
 *
 * @author lukeMccon
 *
 */
public class PackageManager {

	public static final String PACKAGES = "packages";

	PackageManager() {

	}

	private static Map<String, Package> packages = new HashMap<>();

	/**
	 * Gets the packages configuration file
	 */
	private static FileConfiguration getFileConfig() {
		return Airdrop.getPackagesConfiguration().getConfig();
	}

	/**
	 * Gets the packages section from config
	 */
	private static ConfigurationSection getPackagesSection() {
		return (ConfigurationSection) getFileConfig().get(PACKAGES);
	}

	/**
	 * Syncs the package manager with the packages.yml file
	 */
	public static void reload() {
		// Force a reload from config
		Airdrop.getPackagesConfiguration().reloadConfig();
		PackageManager.populatePackages();
		Airdrop.getPluginInstance().setupPackageGuis();
	}

	/**
	 * Get all packages as a set from the config
	 *
	 * @return set of package names
	 */
	public static Set<String> getPackages() {
		ConfigurationSection section = getPackagesSection();
		return section != null ? section.getKeys(false) : Collections.emptySet();
	}

	/**
	 * Get a package given the package name
	 *
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
	 * Initializes or updates the package manager with configuration from the config
	 * file
	 */
	private static void populatePackages() {
		ConfigurationSection config = getPackagesSection();
		if (config == null) {
			return;
		}

		for (String pkg : getPackages()) {
			ArrayList<ItemStack> items = new ArrayList<>();
			ConfigurationSection section = (ConfigurationSection) config.get(pkg);

			if (section != null) {

				List<?> rawList = config.getList(pkg + ".items");
				if (rawList != null) {
					for (Object obj : rawList) {
						if (obj instanceof ItemStack) {
							items.add((ItemStack) obj);
						}
					}
				}

				String name = pkg;
				double price = config.getDouble(pkg + ".price", 0.0);

				if (price == 0.0 && !config.isSet(pkg + ".price")) {
					ChatHandler.getLogger().warning(
							ChatHandler.get(MessageKey.SYSTEM_PACKAGE_PRICE_MISSING, Map.of("name", name)));
				}
				PackageManager.packages.put(name, new Package(name, price, items));
			}
		}
	}

	/**
	 * Lookup if a package exists
	 *
	 * @param packageName package name
	 * @return package exists
	 */
	public static Boolean has(String packageName) {
		return getPackages().contains(packageName);
	}

	/**
	 * Gives information about a package as a string
	 *
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
	 *
	 * @param packageName name of package to lookup
	 * @param items       to update
	 * @throws PackageNotFoundException if the package doesn't exist
	 */
	public static void updatePackageInventory(String packageName, List<ItemStack> items)
			throws PackageNotFoundException {

		Package pkg;
		pkg = PackageManager.get(packageName);
		pkg.setItems(items);

		ConfigurationSection config = getPackagesSection();
		FileConfiguration fileConfig = getFileConfig();

		config.set(packageName + ".items", items.stream().filter(Objects::nonNull)
				.filter(itemstack -> !PackageGui.isControlItemStack(itemstack)).toArray());

		fileConfig.set(PACKAGES, config);
		Airdrop.getPackagesConfiguration().saveConfig();
		PackageManager.reload();
	}

	/**
	 * Create a new package
	 *
	 * @param pkg to create
	 */
	public static void createPackage(Package pkg) throws DuplicatePackageException {
		ConfigurationSection config = getPackagesSection();
		FileConfiguration fileConfig = getFileConfig();

		config.set(pkg.getName() + ".price", pkg.getPrice());
		config.set(pkg.getName() + ".items", pkg.getItems().stream().filter(Objects::nonNull)
				.filter(itemstack -> !PackageGui.isControlItemStack(itemstack)).toArray());
		fileConfig.set(PACKAGES, config);
		Airdrop.getPackagesConfiguration().saveConfig();
		PackageManager.reload();
	}

	/**
	 * Delete a package given a name
	 *
	 * @param packageName name of the package to delete
	 * @throws PackageNotFoundException package couldn't be found
	 */
	public static void deletePackage(String packageName) throws PackageNotFoundException {
		ConfigurationSection config = getPackagesSection();
		FileConfiguration fileConfig = getFileConfig();

		// Make sure the package exists
		get(packageName);
		config.set(packageName, null);
		fileConfig.set(PACKAGES, config);
		Airdrop.getPackagesConfiguration().saveConfig();
		PackageManager.reload();
	}

}
