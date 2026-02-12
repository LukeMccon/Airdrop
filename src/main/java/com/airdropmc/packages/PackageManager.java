package com.airdropmc.packages;

import java.util.*;

import com.airdropmc.helpers.AirdropLogger;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.Airdrop;
import com.airdropmc.PackagesConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
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
		return getFileConfig().getConfigurationSection(PACKAGES);
	}

	/**
	 * Ensures the packages section exists before mutating config.
	 */
	private static ConfigurationSection getOrCreatePackagesSection() {
		FileConfiguration fileConfig = getFileConfig();
		if (fileConfig == null) {
			return null;
		}
		ConfigurationSection section = fileConfig.getConfigurationSection(PACKAGES);
		if (section == null) {
			section = fileConfig.createSection(PACKAGES);
		}
		return section;
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
		packages.clear();
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
					AirdropLogger.warning(
							ChatHandler.get(MessageKey.SYSTEM_PACKAGE_PRICE_MISSING, Map.of("name", name)));
				}
				if (!Package.isValidPrice(price)) {
					AirdropLogger.warning(ChatHandler.get(MessageKey.SYSTEM_PACKAGE_PRICE_INVALID,
							Map.of("name", name, "price", String.valueOf(price))));
					price = 0.0;
				}
				List<ItemStack> limitedItems = limitToBarrelCapacity(items, name);
				PackageManager.packages.put(name, new Package(name, price, limitedItems));
			}
		}
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
		List<ItemStack> limitedItems = limitToBarrelCapacity(items, packageName);
		pkg.setItems(limitedItems);

		ConfigurationSection config = getOrCreatePackagesSection();
		FileConfiguration fileConfig = getFileConfig();
		PackagesConfig packagesConfig = Airdrop.getPackagesConfiguration();
		if (config == null || fileConfig == null || packagesConfig == null) {
			throw new IllegalStateException("Packages configuration is unavailable");
		}

		config.set(packageName + ".items", new ArrayList<>(limitedItems));

		fileConfig.set(PACKAGES, config);
		packagesConfig.saveConfig();
		PackageManager.reload();
	}

	/**
	 * Create a new package
	 *
	 * @param pkg to create
	 */
	public static void createPackage(Package pkg) throws DuplicatePackageException {
		if (PackageManager.has(pkg.getName())) {
			throw new DuplicatePackageException(pkg.getName());
		}
		if (!Package.isValidPrice(pkg.getPrice())) {
			throw new IllegalArgumentException("Package price must be finite and non-negative");
		}

		List<ItemStack> limitedItems = limitToBarrelCapacity(pkg.getItems(), pkg.getName());
		pkg.setItems(limitedItems);

		ConfigurationSection config = getOrCreatePackagesSection();
		FileConfiguration fileConfig = getFileConfig();
		PackagesConfig packagesConfig = Airdrop.getPackagesConfiguration();
		if (config == null || fileConfig == null || packagesConfig == null) {
			throw new IllegalStateException("Packages configuration is unavailable");
		}

		config.set(pkg.getName() + ".price", pkg.getPrice());
		config.set(pkg.getName() + ".items", new ArrayList<>(limitedItems));
		fileConfig.set(PACKAGES, config);
		packagesConfig.saveConfig();
		PackageManager.reload();
	}

	/**
	 * Delete a package given a name
	 *
	 * @param packageName name of the package to delete
	 * @throws PackageNotFoundException package couldn't be found
	 */
	public static void deletePackage(String packageName) throws PackageNotFoundException {
		ConfigurationSection config = getOrCreatePackagesSection();
		FileConfiguration fileConfig = getFileConfig();
		PackagesConfig packagesConfig = Airdrop.getPackagesConfiguration();
		if (config == null || fileConfig == null || packagesConfig == null) {
			throw new IllegalStateException("Packages configuration is unavailable");
		}

		// Make sure the package exists
		get(packageName);
		config.set(packageName, null);
		fileConfig.set(PACKAGES, config);
		packagesConfig.saveConfig();
		PackageManager.reload();
	}

	public static void clear() {
		packages.clear();
	}

}
