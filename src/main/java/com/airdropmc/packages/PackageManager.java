package com.airdropmc.packages;

import java.util.*;

import com.airdropmc.helpers.AirdropLogger;
import com.airdropmc.Airdrop;
import com.airdropmc.PackagesConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import com.airdropmc.exceptions.DuplicatePackageException;
import com.airdropmc.exceptions.PackageNotFoundException;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Manages packages, keeps list of available packages and their contents
 *
 * @author lukeMccon
 *
 */
public class PackageManager {

	public static final String PACKAGES = "packages";
	public static final int MAX_PACKAGE_ITEM_STACKS = 27;

	PackageManager() {

	}

	private static final Map<String, Package> packages = new ConcurrentHashMap<>();

	/**
	 * Gets the packages configuration file
	 */
	private static FileConfiguration getFileConfig() {
		PackagesConfig packagesConfig = Airdrop.getPackagesConfiguration();
		if (packagesConfig == null) {
			return null;
		}
		return packagesConfig.getConfig();
	}

	/**
	 * Gets the packages section from config
	 */
	private static ConfigurationSection getPackagesSection() {
		FileConfiguration fileConfig = getFileConfig();
		if (fileConfig == null) {
			return null;
		}
		return fileConfig.getConfigurationSection(PACKAGES);
	}

	private static YamlConfiguration copyConfiguration(FileConfiguration fileConfig) {
		YamlConfiguration candidate = new YamlConfiguration();
		try {
			candidate.loadFromString(fileConfig.saveToString());
		} catch (InvalidConfigurationException ex) {
			throw new IllegalStateException("Could not copy packages configuration generated in memory", ex);
		}
		return candidate;
	}

	private static void refreshPackagesGui() {
		PackagesGui gui = Airdrop.getPackagesGui();
		if (gui != null) {
			gui.initializeItems();
		}
	}

	/**
	 * Syncs the package manager with the packages.yml file
	 */
	public static boolean reload() {
		PackagesConfig packagesConfig = Airdrop.getPackagesConfiguration();
		if (packagesConfig == null) {
			AirdropLogger.warning("Skipping package reload: packages configuration is unavailable");
			packages.clear();
			return false;
		}
		// Force a reload from config
		AirdropLogger.debug("Reloading packages from packages.yml");
		packagesConfig.reloadConfig();
		PackageManager.populatePackages();
		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin != null && plugin.isEnabled()) {
			plugin.setupPackageGuis();
		} else {
			AirdropLogger.warning("Skipping packages GUI setup: plugin instance is unavailable");
		}
		AirdropLogger.debug("Loaded " + packages.size() + " package(s)");
		return true;
	}

	/**
	 * Get all packages as a set from the config
	 *
	 * @return set of package names
	 */
	public static Set<String> getPackages() {
		return Set.copyOf(packages.keySet());
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
		packages.clear();
		ConfigurationSection config = getPackagesSection();
		if (config == null) {
			return;
		}

		for (String pkg : config.getKeys(false)) {
			ArrayList<ItemStack> items = new ArrayList<>();
			ConfigurationSection section = config.getConfigurationSection(pkg);

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
				Object rawPrice = config.get(pkg + ".price");
				if (!(rawPrice instanceof Number number)) {
					logInvalidPrice(name, rawPrice);
					continue;
				}
				double price = number.doubleValue();
				if (!Package.isValidPrice(price)) {
					logInvalidPrice(name, rawPrice);
					continue;
				}
				List<ItemStack> limitedItems = limitToBarrelCapacity(items, name);
				PackageManager.packages.put(name, new Package(name, price, limitedItems));
			}
		}
	}

	private static void logInvalidPrice(String packageName, Object rawPrice) {
		String invalidValue = rawPrice == null ? "<missing>" : String.valueOf(rawPrice);
		AirdropLogger.warning("Skipping package '" + packageName
				+ "' because its price is invalid: " + invalidValue);
	}

	public static int getFilteredItemCount(List<ItemStack> items) {
		return sanitizePackageItems(items).size();
	}

	public static List<ItemStack> sanitizePackageItems(List<ItemStack> items) {
		if (items == null || items.isEmpty()) {
			return new ArrayList<>();
		}

		return items.stream()
				.filter(Objects::nonNull)
				.filter(itemStack -> !itemStack.getType().isAir())
				.filter(itemStack -> !PackageGui.isControlItemStack(itemStack))
				.map(ItemStack::clone)
				.collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
	}

	private static List<ItemStack> limitToBarrelCapacity(List<ItemStack> items, String packageName) {
		List<ItemStack> sanitizedItems = sanitizePackageItems(items);
		if (sanitizedItems.size() <= MAX_PACKAGE_ITEM_STACKS) {
			return sanitizedItems;
		}

		AirdropLogger.warning("Package '" + packageName + "' has " + sanitizedItems.size()
				+ " item stacks, but only " + MAX_PACKAGE_ITEM_STACKS
				+ " fit in a barrel. Extra stacks will be ignored.");
		return new ArrayList<>(sanitizedItems.subList(0, MAX_PACKAGE_ITEM_STACKS));
	}

	/**
	 * Lookup if a package exists
	 *
	 * @param packageName package name
	 * @return package exists
	 */
	public static boolean has(String packageName) {
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
	public static boolean updatePackageInventory(String packageName, List<ItemStack> items)
			throws PackageNotFoundException {

		Package pkg = PackageManager.get(packageName);
		PackagesConfig packagesConfig = Airdrop.getPackagesConfiguration();
		FileConfiguration fileConfig = packagesConfig != null ? packagesConfig.getConfig() : null;
		if (fileConfig == null) {
			throw new IllegalStateException("Packages configuration is unavailable");
		}

		List<ItemStack> limitedItems = limitToBarrelCapacity(items, packageName);
		YamlConfiguration candidate = copyConfiguration(fileConfig);
		candidate.set(PACKAGES + "." + packageName + ".items", new ArrayList<>(limitedItems));

		if (!packagesConfig.saveConfig(candidate)) {
			return false;
		}

		pkg.setItems(limitedItems);
		return true;
	}

	/**
	 * Create a new package
	 *
	 * @param pkg to create
	 */
	public static boolean createPackage(Package pkg) throws DuplicatePackageException {
		if (PackageManager.has(pkg.getName())) {
			throw new DuplicatePackageException(pkg.getName());
		}
		if (!Package.isValidPrice(pkg.getPrice())) {
			throw new IllegalArgumentException("Package price must be finite and non-negative");
		}
		PackagesConfig packagesConfig = Airdrop.getPackagesConfiguration();
		FileConfiguration fileConfig = packagesConfig != null ? packagesConfig.getConfig() : null;
		if (fileConfig == null) {
			throw new IllegalStateException("Packages configuration is unavailable");
		}

		List<ItemStack> limitedItems = limitToBarrelCapacity(pkg.getItems(), pkg.getName());
		YamlConfiguration candidate = copyConfiguration(fileConfig);
		candidate.set(PACKAGES + "." + pkg.getName() + ".price", pkg.getPrice());
		candidate.set(PACKAGES + "." + pkg.getName() + ".items", new ArrayList<>(limitedItems));

		if (!packagesConfig.saveConfig(candidate)) {
			return false;
		}

		Package committedPackage = new Package(pkg.getName(), pkg.getPrice(), limitedItems);
		packages.put(pkg.getName(), committedPackage);
		refreshPackagesGui();
		return true;
	}

	/**
	 * Delete a package given a name
	 *
	 * @param packageName name of the package to delete
	 * @throws PackageNotFoundException package couldn't be found
	 */
	public static boolean deletePackage(String packageName) throws PackageNotFoundException {
		get(packageName);
		PackagesConfig packagesConfig = Airdrop.getPackagesConfiguration();
		FileConfiguration fileConfig = packagesConfig != null ? packagesConfig.getConfig() : null;
		if (fileConfig == null) {
			throw new IllegalStateException("Packages configuration is unavailable");
		}

		YamlConfiguration candidate = copyConfiguration(fileConfig);
		candidate.set(PACKAGES + "." + packageName, null);
		if (!packagesConfig.saveConfig(candidate)) {
			return false;
		}

		packages.remove(packageName);
		refreshPackagesGui();
		return true;
	}

	public static void clear() {
		packages.clear();
	}

}
