package com.airdropmc.packages;

import com.airdropmc.exceptions.DuplicatePackageException;
import com.airdropmc.exceptions.PackageCapacityException;
import com.airdropmc.exceptions.PackageNotFoundException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Manages the live package registry and pure package configuration mutations.
 *
 * @author lukeMccon
 */
public class PackageManager {

	public static final String PACKAGES_SECTION = "packages";
	public static final int MAX_PACKAGES = 27;
	public static final int MAX_PACKAGE_ITEM_STACKS = 27;

	private static volatile Map<String, Package> packages = Map.of();

	PackageManager() {
	}

	/**
	 * Purely materializes a complete, detached package snapshot from a configuration.
	 * The input and live registry are never mutated. Any invalid package rejects
	 * the complete candidate.
	 *
	 * @param candidate configuration to materialize
	 * @return immutable map keyed by canonical package name
	 * @throws PackageMaterializationException if the packages section or any package is invalid
	 */
	public static Map<String, Package> materializePackages(FileConfiguration candidate)
			throws PackageMaterializationException {
		if (candidate == null) {
			throw new PackageMaterializationException("Packages configuration is unavailable");
		}

		if (!candidate.isSet(PACKAGES_SECTION)) {
			throw new PackageMaterializationException("Missing required 'packages' section");
		}
		ConfigurationSection configuredPackages = candidate.getConfigurationSection(PACKAGES_SECTION);
		if (configuredPackages == null) {
			throw new PackageMaterializationException("Root 'packages' value must be a configuration section");
		}

		List<String> configuredNames = new ArrayList<>(configuredPackages.getKeys(false));
		if (configuredNames.size() > MAX_PACKAGES) {
			throw new PackageMaterializationException(
					"Configured " + configuredNames.size() + " packages, but the limit is " + MAX_PACKAGES);
		}
		configuredNames.sort(Comparator.naturalOrder());
		Map<String, List<String>> namesByCanonical = validateConfiguredNames(configuredNames);
		Map<String, Package> materialized = new LinkedHashMap<>();

		for (Map.Entry<String, List<String>> entry : namesByCanonical.entrySet()) {
			String configuredName = entry.getValue().getFirst();
			ConfigurationSection packageSection = configuredPackages.getConfigurationSection(configuredName);
			if (packageSection == null) {
				throw new PackageMaterializationException(
						"Package '" + configuredName + "' must be a configuration section");
			}

			Object rawPrice = packageSection.isSet("price") ? packageSection.get("price") : null;
			if (!(rawPrice instanceof Number number)) {
				throw invalidPrice(configuredName, rawPrice);
			}

			double price;
			try {
				price = number.doubleValue();
			} catch (RuntimeException exception) {
				throw new PackageMaterializationException(
						invalidPriceMessage(configuredName, rawPrice), exception);
			}
			if (!Package.isValidPrice(price)) {
				throw invalidPrice(configuredName, rawPrice);
			}

			List<ItemStack> items = readPackageItems(packageSection, configuredName);
			List<ItemStack> deliverableItems = limitToBarrelCapacity(items);
			if (price > 0.0 && deliverableItems.isEmpty()) {
				throw new PackageMaterializationException(
						"Package '" + configuredName
								+ "' has a positive price but no deliverable item stacks");
			}
			materialized.put(entry.getKey(),
					new Package(configuredName, price, deliverableItems));
		}

		return Collections.unmodifiableMap(materialized);
	}

	/**
	 * Compatibility overload retained for integrations compiled against the former
	 * display-name filtering API. Visible control labels are intentionally ignored.
	 */
	@Deprecated(forRemoval = false)
	public static Map<String, Package> materializePackages(
			FileConfiguration candidate,
			Set<String> controlItemNames) throws PackageMaterializationException {
		Objects.requireNonNull(controlItemNames, "Control item names are required");
		return materializePackages(candidate);
	}

	private static Map<String, List<String>> validateConfiguredNames(List<String> configuredNames)
			throws PackageMaterializationException {
		Map<String, List<String>> namesByCanonical = new TreeMap<>();
		for (String configuredName : configuredNames) {
			PackageNamePolicy.Result validation = PackageNamePolicy.validate(configuredName);
			if (!validation.accepted()) {
				throw new PackageMaterializationException(
						"Invalid package name '" + configuredName + "': "
								+ validation.diagnostic(configuredName));
			}
			namesByCanonical.computeIfAbsent(validation.canonicalName(), ignored -> new ArrayList<>())
					.add(configuredName);
		}

		for (Map.Entry<String, List<String>> entry : namesByCanonical.entrySet()) {
			if (entry.getValue().size() > 1) {
				throw new PackageMaterializationException("Package names " + entry.getValue()
						+ " conflict case-insensitively as '" + entry.getKey() + "'");
			}
		}
		return namesByCanonical;
	}

	private static List<ItemStack> readPackageItems(
			ConfigurationSection packageSection,
			String packageName)
			throws PackageMaterializationException {
		Object configuredItems = packageSection.get("items");
		if (!(configuredItems instanceof List<?> rawItems)) {
			throw new PackageMaterializationException(
					"Package '" + packageName + "' items must be a list");
		}

		List<ItemStack> items = new ArrayList<>(rawItems.size());
		for (int index = 0; index < rawItems.size(); index++) {
			Object rawItem = rawItems.get(index);
			if (!(rawItem instanceof ItemStack itemStack)) {
				String rawType = rawItem == null ? "null" : rawItem.getClass().getName();
				throw new PackageMaterializationException(
						"Package '" + packageName + "' has invalid item at index " + index
								+ ": expected ItemStack but found " + rawType);
			}
			items.add(itemStack);
		}
		return items;
	}

	private static PackageMaterializationException invalidPrice(String packageName, Object rawPrice) {
		return new PackageMaterializationException(invalidPriceMessage(packageName, rawPrice));
	}

	private static String invalidPriceMessage(String packageName, Object rawPrice) {
		String invalidValue = rawPrice == null ? "<missing>" : String.valueOf(rawPrice);
		return "Package '" + packageName + "' has invalid price: " + invalidValue;
	}

	/**
	 * Publishes an already detached immutable registry snapshot with one volatile assignment.
	 *
	 * @param candidatePackages packages keyed by canonical package name
	 */
	public static void publishPackages(Map<String, Package> candidatePackages) {
		Map<String, Package> candidate = Objects.requireNonNull(
				candidatePackages, "Package snapshot is required");
		if (candidate.size() > MAX_PACKAGES) {
			throw new IllegalArgumentException(
					"Cannot publish " + candidate.size() + " packages; the limit is " + MAX_PACKAGES);
		}
		packages = candidate;
	}

	/**
	 * Creates a detached configuration candidate containing a normalized package.
	 *
	 * @param source source configuration
	 * @param pkg package to add
	 * @return detached configuration containing the new package
	 * @throws PackageMaterializationException if the source configuration is invalid
	 * @throws DuplicatePackageException if the package name already exists
	 * @throws PackageCapacityException if adding the package would exceed the package limit
	 */
	public static YamlConfiguration createPackageCandidate(FileConfiguration source, Package pkg)
			throws PackageMaterializationException, DuplicatePackageException, PackageCapacityException {
		Map<String, Package> currentPackages = materializePackages(source);
		if (pkg == null) {
			throw new IllegalArgumentException("Package is required");
		}

		String canonicalName = PackageNamePolicy.requireCanonical(pkg.getName());
		if (currentPackages.containsKey(canonicalName)) {
			throw new DuplicatePackageException(pkg.getName());
		}
		if (currentPackages.size() >= MAX_PACKAGES) {
			throw new PackageCapacityException(currentPackages.size() + 1, MAX_PACKAGES);
		}
		if (!Package.isValidPrice(pkg.getPrice())) {
			throw new IllegalArgumentException("Package price must be finite and non-negative");
		}

		List<ItemStack> normalizedItems = limitToBarrelCapacity(pkg.getItems());
		YamlConfiguration candidate = copyConfiguration(source);
		candidate.set(PACKAGES_SECTION + "." + pkg.getName() + ".price", pkg.getPrice());
		candidate.set(PACKAGES_SECTION + "." + pkg.getName() + ".items", new ArrayList<>(normalizedItems));
		return candidate;
	}

	/**
	 * Compatibility overload retained for integrations compiled against the former
	 * display-name filtering API. Visible control labels are intentionally ignored.
	 */
	@Deprecated(forRemoval = false)
	public static YamlConfiguration createPackageCandidate(
			FileConfiguration source,
			Package pkg,
			Set<String> controlItemNames)
			throws PackageMaterializationException, DuplicatePackageException, PackageCapacityException {
		Objects.requireNonNull(controlItemNames, "Control item names are required");
		return createPackageCandidate(source, pkg);
	}

	/**
	 * Creates a detached configuration candidate with one package inventory replaced.
	 *
	 * @param source source configuration
	 * @param packageName package to update
	 * @param items replacement inventory
	 * @return detached configuration containing the replacement inventory
	 * @throws PackageMaterializationException if the source configuration is invalid
	 * @throws PackageNotFoundException if the package does not exist
	 */
	public static YamlConfiguration updatePackageInventoryCandidate(
			FileConfiguration source, String packageName, List<ItemStack> items)
			throws PackageMaterializationException, PackageNotFoundException {
		Map<String, Package> currentPackages = materializePackages(source);
		Package pkg = findPackage(currentPackages, packageName);
		List<ItemStack> normalizedItems = limitToBarrelCapacity(items);

		YamlConfiguration candidate = copyConfiguration(source);
		candidate.set(PACKAGES_SECTION + "." + pkg.getName() + ".items", new ArrayList<>(normalizedItems));
		return candidate;
	}

	/**
	 * Compatibility overload retained for integrations compiled against the former
	 * display-name filtering API. Visible control labels are intentionally ignored.
	 */
	@Deprecated(forRemoval = false)
	public static YamlConfiguration updatePackageInventoryCandidate(
			FileConfiguration source,
			String packageName,
			List<ItemStack> items,
			Set<String> controlItemNames)
			throws PackageMaterializationException, PackageNotFoundException {
		Objects.requireNonNull(controlItemNames, "Control item names are required");
		return updatePackageInventoryCandidate(source, packageName, items);
	}

	/**
	 * Creates a detached configuration candidate with one package removed.
	 *
	 * @param source source configuration
	 * @param packageName package to remove
	 * @return detached configuration without the package
	 * @throws PackageMaterializationException if the source configuration is invalid
	 * @throws PackageNotFoundException if the package does not exist
	 */
	public static YamlConfiguration deletePackageCandidate(FileConfiguration source, String packageName)
			throws PackageMaterializationException, PackageNotFoundException {
		Map<String, Package> currentPackages = materializePackages(source);
		Package pkg = findPackage(currentPackages, packageName);

		YamlConfiguration candidate = copyConfiguration(source);
		candidate.set(PACKAGES_SECTION + "." + pkg.getName(), null);
		if (candidate.getConfigurationSection(PACKAGES_SECTION) == null) {
			candidate.createSection(PACKAGES_SECTION);
		}
		return candidate;
	}

	/**
	 * Compatibility overload retained for integrations compiled against the former
	 * display-name filtering API. Visible control labels are intentionally ignored.
	 */
	@Deprecated(forRemoval = false)
	public static YamlConfiguration deletePackageCandidate(
			FileConfiguration source,
			String packageName,
			Set<String> controlItemNames)
			throws PackageMaterializationException, PackageNotFoundException {
		Objects.requireNonNull(controlItemNames, "Control item names are required");
		return deletePackageCandidate(source, packageName);
	}

	private static Package findPackage(Map<String, Package> snapshot, String packageName)
			throws PackageNotFoundException {
		PackageNamePolicy.Result validation = PackageNamePolicy.validate(packageName);
		Package pkg = validation.accepted() ? snapshot.get(validation.canonicalName()) : null;
		if (pkg == null) {
			throw new PackageNotFoundException(packageName);
		}
		return pkg;
	}

	private static YamlConfiguration copyConfiguration(FileConfiguration source) {
		YamlConfiguration candidate = new YamlConfiguration();
		try {
			candidate.loadFromString(source.saveToString());
		} catch (InvalidConfigurationException exception) {
			throw new IllegalStateException(
					"Could not copy packages configuration generated in memory", exception);
		}
		return candidate;
	}

	/**
	 * Gets all package names from the current registry snapshot.
	 */
	public static Set<String> getPackages() {
		Map<String, Package> snapshot = packages;
		return snapshot.values().stream()
				.map(Package::getName)
				.collect(Collectors.toUnmodifiableSet());
	}

	public static int getPackageCount() {
		return packages.size();
	}

	static List<Package> getPackagesForDisplay() {
		Map<String, Package> snapshot = packages;
		return snapshot.values().stream()
				.sorted(Comparator.comparing(Package::getName, String.CASE_INSENSITIVE_ORDER)
						.thenComparing(Package::getName))
				.toList();
	}

	/**
	 * Gets a package by name using case-insensitive package identity.
	 *
	 * @param packageName package name to resolve
	 * @return matching package
	 * @throws PackageNotFoundException if no package matches the requested name
	 */
	public static Package get(String packageName) throws PackageNotFoundException {
		return findPackage(packages, packageName);
	}

	public static int getFilteredItemCount(List<ItemStack> items) {
		return sanitizePackageItems(items).size();
	}

	/**
	 * Removes null and air stacks and detaches every retained package item.
	 */
	public static List<ItemStack> sanitizePackageItems(List<ItemStack> items) {
		if (items == null || items.isEmpty()) {
			return new ArrayList<>();
		}

		return items.stream()
				.filter(Objects::nonNull)
				.filter(itemStack -> !itemStack.getType().isAir())
				.map(ItemStack::clone)
				.collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
	}

	private static List<ItemStack> limitToBarrelCapacity(List<ItemStack> items) {
		List<ItemStack> sanitizedItems = sanitizePackageItems(items);
		if (sanitizedItems.size() <= MAX_PACKAGE_ITEM_STACKS) {
			return sanitizedItems;
		}
		return new ArrayList<>(sanitizedItems.subList(0, MAX_PACKAGE_ITEM_STACKS));
	}

	/**
	 * Looks up whether a package exists using case-insensitive package identity.
	 */
	public static boolean has(String packageName) {
		PackageNamePolicy.Result validation = PackageNamePolicy.validate(packageName);
		Map<String, Package> snapshot = packages;
		return validation.accepted() && snapshot.containsKey(validation.canonicalName());
	}

	/**
	 * Gets a package's diagnostic description by case-insensitive name.
	 *
	 * @param packageName package name to resolve
	 * @return package description
	 * @throws PackageNotFoundException if no package matches the requested name
	 */
	public static String getInfo(String packageName) throws PackageNotFoundException {
		return get(packageName).toString();
	}

	public static void clear() {
		packages = Map.of();
	}
}
