package com.airdropmc.packages;

import com.airdropmc.exceptions.DuplicatePackageException;
import com.airdropmc.exceptions.PackageNotFoundException;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageManagerMutationTest {

	@AfterEach
	void tearDown() {
		PackageManager.clear();
	}

	@Test
	void createPackageCandidate_isDetachedNormalizedAndDoesNotPublish() throws Exception {
		YamlConfiguration source = emptyConfiguration();
		String sourceYaml = source.saveToString();
		ItemStack callerItem = new ItemStack(Material.STONE, 2);
		Package callerPackage = new Package("NewPkg", 3.0, List.of(callerItem));

		YamlConfiguration candidate = PackageManager.createPackageCandidate(source, callerPackage);

		callerItem.setAmount(7);
		callerPackage.setItems(List.of(new ItemStack(Material.GOLD_BLOCK, 5)));
		assertEquals(sourceYaml, source.saveToString());
		assertThrows(PackageNotFoundException.class, () -> PackageManager.get("newpkg"));

		Map<String, Package> materialized = PackageManager.materializePackages(candidate);
		Package created = materialized.get("newpkg");
		assertEquals("NewPkg", created.getName());
		assertEquals(3.0, created.getPrice());
		assertEquals(Material.STONE, created.getItems().getFirst().getType());
		assertEquals(2, created.getItems().getFirst().getAmount());
		assertNotSame(callerItem, candidate.getList("packages.NewPkg.items").getFirst());
	}

	@Test
	void createPackageCandidate_rejectsCaseInsensitiveDuplicateAndInvalidName() throws Exception {
		YamlConfiguration source = configurationWithStarter("Starter");

		assertThrows(DuplicatePackageException.class,
				() -> PackageManager.createPackageCandidate(source,
						new Package("sTaRtEr", 3.0, List.of())));
		assertThrows(IllegalArgumentException.class,
				() -> PackageManager.createPackageCandidate(source,
						new Package("reload", 3.0, List.of())));
	}

	@Test
	void updatePackageInventoryCandidate_usesStoredYamlCaseAndDoesNotMutateSource() throws Exception {
		YamlConfiguration source = configurationWithStarter("Starter");
		String sourceYaml = source.saveToString();
		ItemStack callerItem = new ItemStack(Material.DIRT, 2);

		YamlConfiguration candidate = PackageManager.updatePackageInventoryCandidate(
				source, "STARTER", List.of(callerItem));

		callerItem.setAmount(9);
		assertEquals(sourceYaml, source.saveToString());
		assertTrue(candidate.isSet("packages.Starter.items"));
		assertFalse(candidate.isSet("packages.STARTER.items"));
		assertFalse(candidate.isSet("packages.starter.items"));
		Package updated = PackageManager.materializePackages(candidate).get("starter");
		assertEquals(Material.DIRT, updated.getItems().getFirst().getType());
		assertEquals(2, updated.getItems().getFirst().getAmount());
	}

	@Test
	void updatePackageInventoryCandidate_rejectsMissingPackageWithoutPublishing() throws Exception {
		YamlConfiguration source = configurationWithStarter("starter");
		PackageManager.publishPackages(PackageManager.materializePackages(source));
		Package livePackage = PackageManager.get("starter");

		assertThrows(PackageNotFoundException.class,
				() -> PackageManager.updatePackageInventoryCandidate(
						source, "missing", List.of(new ItemStack(Material.DIRT))));

		assertSame(livePackage, PackageManager.get("starter"));
		assertTrue(PackageManager.get("starter").getItems().isEmpty());
	}

	@Test
	void deletePackageCandidate_usesStoredYamlCaseAndKeepsExplicitEmptySection() throws Exception {
		YamlConfiguration source = configurationWithStarter("Starter");
		String sourceYaml = source.saveToString();

		YamlConfiguration candidate = PackageManager.deletePackageCandidate(source, "STARTER");

		assertEquals(sourceYaml, source.saveToString());
		assertFalse(candidate.isSet("packages.Starter"));
		assertTrue(candidate.isConfigurationSection("packages"));
		assertTrue(PackageManager.materializePackages(candidate).isEmpty());
	}

	@Test
	void deletePackageCandidate_rejectsMissingPackageWithoutPublishing() throws Exception {
		YamlConfiguration source = configurationWithStarter("starter");
		PackageManager.publishPackages(PackageManager.materializePackages(source));
		Package livePackage = PackageManager.get("starter");

		assertThrows(PackageNotFoundException.class,
				() -> PackageManager.deletePackageCandidate(source, "missing"));

		assertSame(livePackage, PackageManager.get("starter"));
	}

	@Test
	void failedCandidateMaterializationPreservesPublishedSnapshot() throws Exception {
		YamlConfiguration source = configurationWithStarter("starter");
		PackageManager.publishPackages(PackageManager.materializePackages(source));
		Package livePackage = PackageManager.get("starter");
		YamlConfiguration invalidCandidate = configurationWithStarter("starter");
		invalidCandidate.set("packages.broken", "not-a-section");

		assertThrows(PackageMaterializationException.class,
				() -> PackageManager.updatePackageInventoryCandidate(
						invalidCandidate, "starter", List.of(new ItemStack(Material.DIRT))));

		assertSame(livePackage, PackageManager.get("starter"));
		assertTrue(livePackage.getItems().isEmpty());
	}

	@Test
	void publishPackages_isTheOnlyMutationThatReplacesLiveSnapshot() throws Exception {
		YamlConfiguration source = configurationWithStarter("starter");
		PackageManager.publishPackages(PackageManager.materializePackages(source));
		Package original = PackageManager.get("starter");
		YamlConfiguration candidate = PackageManager.createPackageCandidate(
				source, new Package("other", 4.0, List.of(new ItemStack(Material.DIAMOND))));
		Map<String, Package> candidateSnapshot = PackageManager.materializePackages(candidate);

		assertSame(original, PackageManager.get("starter"));
		assertThrows(PackageNotFoundException.class, () -> PackageManager.get("other"));

		PackageManager.publishPackages(candidateSnapshot);

		assertNotSame(original, PackageManager.get("starter"));
		assertDoesNotThrow(() -> PackageManager.get("OTHER"));
		assertSame(candidateSnapshot.get("other"), PackageManager.get("other"));
		assertEquals(Material.DIAMOND, PackageManager.get("other").getItems().getFirst().getType());
	}

	private static YamlConfiguration emptyConfiguration() {
		YamlConfiguration config = new YamlConfiguration();
		config.createSection("packages");
		return config;
	}

	private static YamlConfiguration configurationWithStarter(String storedName) {
		YamlConfiguration config = emptyConfiguration();
		config.createSection("packages." + storedName);
		config.set("packages." + storedName + ".price", 10.0);
		config.set("packages." + storedName + ".items", List.of());
		return config;
	}
}
