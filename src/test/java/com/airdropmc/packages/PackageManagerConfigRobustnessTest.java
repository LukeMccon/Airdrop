package com.airdropmc.packages;

import com.airdropmc.exceptions.PackageNotFoundException;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageManagerConfigRobustnessTest {

	@AfterEach
	void tearDown() {
		PackageManager.clear();
	}

	@Test
	void liveRegistry_isHeldAsOneVolatileSnapshotReference() throws Exception {
		Field registry = PackageManager.class.getDeclaredField("packages");

		assertTrue(Modifier.isVolatile(registry.getModifiers()));
		assertTrue(Map.class.isAssignableFrom(registry.getType()));
	}

	@Test
	void materializePackages_requiresPackagesSectionButAcceptsExplicitEmpty() throws Exception {
		PackageMaterializationException missing = assertThrows(PackageMaterializationException.class,
				() -> PackageManager.materializePackages(new YamlConfiguration()));
		assertTrue(missing.getMessage().contains("packages"));

		YamlConfiguration scalar = new YamlConfiguration();
		scalar.set("packages", "not-a-section");
		PackageMaterializationException scalarFailure = assertThrows(PackageMaterializationException.class,
				() -> PackageManager.materializePackages(scalar));
		assertTrue(scalarFailure.getMessage().contains("Root 'packages'"));
		assertTrue(scalarFailure.getMessage().contains("section"));

		YamlConfiguration explicitEmpty = new YamlConfiguration();
		explicitEmpty.loadFromString("packages: {}\n");
		Map<String, Package> materialized = PackageManager.materializePackages(explicitEmpty);

		assertTrue(materialized.isEmpty());
		assertThrows(UnsupportedOperationException.class,
				() -> materialized.put("later", new Package("later", 1.0, List.of())));
	}

	@Test
	void materializePackages_rejectsWholeCandidateOnNonSectionEntryAndPreservesLiveSnapshot()
			throws Exception {
		YamlConfiguration initial = configurationWithPackage("starter", 10.0);
		PackageManager.publishPackages(PackageManager.materializePackages(initial));
		Package livePackage = PackageManager.get("starter");

		YamlConfiguration candidate = configurationWithPackage("other", 2.0);
		candidate.set("packages.broken", "not-a-section");

		PackageMaterializationException failure = assertThrows(PackageMaterializationException.class,
				() -> PackageManager.materializePackages(candidate));

		assertTrue(failure.getMessage().contains("broken"));
		assertTrue(failure.getMessage().contains("section"));
		assertSame(livePackage, PackageManager.get("STARTER"));
		assertThrows(PackageNotFoundException.class, () -> PackageManager.get("other"));
	}

	@Test
	void materializePackages_rejectsWholeCandidateOnInvalidOrReservedName() {
		for (String invalidName : List.of(
				"all", "*", "package", "packages", "version", "reload", "bad name")) {
			YamlConfiguration candidate = configurationWithPackage("valid_name", 1.0);
			addPackage(candidate, invalidName, 2.0);

			PackageMaterializationException failure = assertThrows(PackageMaterializationException.class,
					() -> PackageManager.materializePackages(candidate), invalidName);
			assertTrue(failure.getMessage().contains(invalidName), failure::getMessage);
		}
	}

	@Test
	void materializePackages_rejectsWholeCandidateOnCaseInsensitiveCollision() {
		YamlConfiguration candidate = configurationWithPackage("Starter", 1.0);
		addPackage(candidate, "starter", 2.0);
		addPackage(candidate, "other", 3.0);

		PackageMaterializationException failure = assertThrows(PackageMaterializationException.class,
				() -> PackageManager.materializePackages(candidate));

		assertTrue(failure.getMessage().contains("Starter"));
		assertTrue(failure.getMessage().contains("starter"));
		assertTrue(failure.getMessage().contains("conflict"));
	}

	@Test
	void materializePackages_rejectsWholeCandidateOnEveryInvalidRawPrice() {
		List<Object> invalidPrices = List.of(
				"10",
				true,
				Double.NaN,
				Double.POSITIVE_INFINITY,
				Double.NEGATIVE_INFINITY,
				-1,
				new BigDecimal("1e10000"));

		YamlConfiguration missingPrice = new YamlConfiguration();
		missingPrice.createSection("packages.missing");
		missingPrice.set("packages.missing.items", List.of());
		PackageMaterializationException missingFailure = assertThrows(PackageMaterializationException.class,
				() -> PackageManager.materializePackages(missingPrice));
		assertTrue(missingFailure.getMessage().contains("missing"));
		assertTrue(missingFailure.getMessage().contains("<missing>"));

		for (int index = 0; index < invalidPrices.size(); index++) {
			String packageName = "invalid" + index;
			YamlConfiguration candidate = configurationWithPackage("valid", 1.0);
			addPackage(candidate, packageName, invalidPrices.get(index));

			PackageMaterializationException failure = assertThrows(PackageMaterializationException.class,
					() -> PackageManager.materializePackages(candidate), packageName);
			assertTrue(failure.getMessage().contains(packageName), failure::getMessage);
			assertTrue(failure.getMessage().contains("price"), failure::getMessage);
		}
	}

	@Test
	void materializePackages_acceptsNumericFiniteNonNegativePrices() throws Exception {
		YamlConfiguration candidate = new YamlConfiguration();
		candidate.createSection("packages");
		addPackage(candidate, "integer-zero", 0);
		addPackage(candidate, "double-zero", 0.0);
		addPackage(candidate, "positive", 12.5f);

		Map<String, Package> materialized = PackageManager.materializePackages(candidate);

		assertEquals(Set.of("integer-zero", "double-zero", "positive"), materialized.keySet());
		assertEquals(0.0, materialized.get("integer-zero").getPrice());
		assertEquals(0.0, materialized.get("double-zero").getPrice());
		assertEquals(12.5, materialized.get("positive").getPrice());
	}

	@Test
	void materializeAndPublish_detachConfigurationCandidateAndLiveSnapshot() throws Exception {
		ItemStack sourceItem = new ItemStack(Material.DIRT, 2);
		YamlConfiguration candidate = configurationWithPackage("Starter", 10.0);
		candidate.set("packages.Starter.items", List.of(sourceItem));

		Map<String, Package> materialized = PackageManager.materializePackages(candidate);
		ItemStack materializedItem = materialized.get("starter").getItems().getFirst();
		assertNotSame(sourceItem, materializedItem);

		PackageManager.publishPackages(materialized);
		Field registry = PackageManager.class.getDeclaredField("packages");
		registry.setAccessible(true);
		assertSame(materialized, registry.get(null));
		Package livePackage = PackageManager.get("STARTER");
		assertSame(materialized.get("starter"), livePackage);

		sourceItem.setAmount(7);
		((ItemStack) candidate.getList("packages.Starter.items").getFirst()).setAmount(9);

		assertEquals(Material.DIRT, livePackage.getItems().getFirst().getType());
		assertEquals(2, livePackage.getItems().getFirst().getAmount());
		assertTrue(PackageManager.has("sTaRtEr"));
		assertFalse(PackageManager.has("missing"));
		assertEquals(Set.of("Starter"), PackageManager.getPackages());
	}

	private static YamlConfiguration configurationWithPackage(String packageName, Object price) {
		YamlConfiguration config = new YamlConfiguration();
		config.createSection("packages");
		addPackage(config, packageName, price);
		return config;
	}

	private static void addPackage(YamlConfiguration config, String packageName, Object price) {
		config.createSection("packages." + packageName);
		config.set("packages." + packageName + ".items", List.of());
		if (price != null) {
			config.set("packages." + packageName + ".price", price);
		}
	}
}
