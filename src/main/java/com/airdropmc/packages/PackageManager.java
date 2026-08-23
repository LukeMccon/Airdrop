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
import java.util.stream.Collectors;


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

	private static boolean hasConfiguredIdentity(FileConfiguration fileConfig, String canonicalName) {
		ConfigurationSection configuredPackages = fileConfig.getConfigurationSection(PACKAGES);
		if (configuredPackages == null) {
			return false;
		}

		for (String configuredName : configuredPackages.getKeys(false)) {
			PackageNamePolicy.Result validation = PackageNamePolicy.validate(configuredName);
			if (validation.accepted() && validation.canonicalName().equals(canonicalName)) {
				return true;
			}
		}
		return false;
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
		return packages.values().stream()
				.map(Package::getName)
				.collect(Collectors.toUnmodifiableSet());
	}

	/**
	 * Get a package given the package name
	 *
	 * @param packageName name of package
	 * @return the package
	 * @throws PackageNotFoundException if the package does not exist
	 */
	public static Package get(String packageName) throws PackageNotFoundException {
		PackageNamePolicy.Result validation = PackageNamePolicy.validate(packageName);
		Package pkg = validation.accepted() ? packages.get(validation.canonicalName()) : null;

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

		List<String> configuredNames = new ArrayList<>(config.getKeys(false));
		configuredNames.sort(Comparator.naturalOrder());
		Map<String, List<String>> namesByCanonical = new TreeMap<>();

		for (String name : configuredNames) {
			PackageNamePolicy.Result validation = PackageNamePolicy.validate(name);
			if (!validation.accepted()) {
				AirdropLogger.warning("Skipping package '" + name + "': " + validation.diagnostic(name));
				continue;
			}
			namesByCanonical.computeIfAbsent(validation.canonicalName(), ignored -> new ArrayList<>()).add(name);
		}

		for (Map.Entry<String, List<String>> entry : namesByCanonical.entrySet()) {
			List<String> exactNames = entry.getValue();
			if (exactNames.size() > 1) {
				AirdropLogger.warning("Skipping packages " + exactNames
						+ " because their names conflict without case differences as '" + entry.getKey() + "'");
				continue;
			}

			String name = exactNames.getFirst();
			ArrayList<ItemStack> items = new ArrayList<>();
			ConfigurationSection section = config.getConfigurationSection(name);

			if (section != null) {

				List<?> rawList = config.getList(name + ".items");
				if (rawList != null) {
					for (Object obj : rawList) {
						if (obj instanceof ItemStack itemStack) {
							items.add(itemStack);
						}
					}
				}

				Object rawPrice = config.get(name + ".price");
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
				PackageManager.packages.put(entry.getKey(), new Package(name, price, limitedItems));
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
		PackageNamePolicy.Result validation = PackageNamePolicy.validate(packageName);
		return validation.accepted() && packages.containsKey(validation.canonicalName());
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
		String storedName = pkg.getName();
		PackagesConfig packagesConfig = Airdrop.getPackagesConfiguration();
		FileConfiguration fileConfig = packagesConfig != null ? packagesConfig.getConfig() : null;
		if (fileConfig == null) {
			throw new IllegalStateException("Packages configuration is unavailable");
		}

		List<ItemStack> limitedItems = limitToBarrelCapacity(items, storedName);
		YamlConfiguration candidate = copyConfiguration(fileConfig);
		candidate.set(PACKAGES + "." + storedName + ".items", new ArrayList<>(limitedItems));

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
		String canonicalName = PackageNamePolicy.requireCanonical(pkg.getName());
		if (packages.containsKey(canonicalName)) {
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
		if (hasConfiguredIdentity(fileConfig, canonicalName)) {
			throw new DuplicatePackageException(pkg.getName());
		}

		List<ItemStack> limitedItems = limitToBarrelCapacity(pkg.getItems(), pkg.getName());
		YamlConfiguration candidate = copyConfiguration(fileConfig);
		candidate.set(PACKAGES + "." + pkg.getName() + ".price", pkg.getPrice());
		candidate.set(PACKAGES + "." + pkg.getName() + ".items", new ArrayList<>(limitedItems));

		if (!packagesConfig.saveConfig(candidate)) {
			return false;
		}

		Package committedPackage = new Package(pkg.getName(), pkg.getPrice(), limitedItems);
		packages.put(canonicalName, committedPackage);
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
		Package pkg = get(packageName);
		String storedName = pkg.getName();
		String canonicalName = PackageNamePolicy.requireCanonical(storedName);
		PackagesConfig packagesConfig = Airdrop.getPackagesConfiguration();
		FileConfiguration fileConfig = packagesConfig != null ? packagesConfig.getConfig() : null;
		if (fileConfig == null) {
			throw new IllegalStateException("Packages configuration is unavailable");
		}

		YamlConfiguration candidate = copyConfiguration(fileConfig);
		candidate.set(PACKAGES + "." + storedName, null);
		if (!packagesConfig.saveConfig(candidate)) {
			return false;
		}

		packages.remove(canonicalName);
		refreshPackagesGui();
		return true;
	}

	public static void clear() {
		packages.clear();
	}

}
