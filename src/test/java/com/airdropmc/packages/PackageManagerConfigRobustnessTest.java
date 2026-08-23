package com.airdropmc.packages;

import com.airdropmc.Airdrop;
import com.airdropmc.PackagesConfig;
import com.airdropmc.exceptions.PackageNotFoundException;
import com.airdropmc.helpers.AirdropLogger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class PackageManagerConfigRobustnessTest {

	@BeforeEach
	void setUp() throws Exception {
		YamlConfiguration config = new YamlConfiguration();
		config.createSection("packages");
		config.set("packages.broken", "not-a-section");
		config.set("packages.starter.price", 10.0);
		config.set("packages.starter.items", java.util.List.of());

		setPackagesConfig(config);

		Field pluginInstanceField = Airdrop.class.getDeclaredField("pluginInstance");
		pluginInstanceField.setAccessible(true);
		pluginInstanceField.set(null, null);
	}

	@AfterEach
	void tearDown() throws Exception {
		PackageManager.clear();
		Field packagesConfigField = Airdrop.class.getDeclaredField("packagesConfiguration");
		packagesConfigField.setAccessible(true);
		packagesConfigField.set(null, null);

		Field pluginInstanceField = Airdrop.class.getDeclaredField("pluginInstance");
		pluginInstanceField.setAccessible(true);
		pluginInstanceField.set(null, null);
	}

	@Test
	void reload_ignoresNonSectionPackageEntries() {
		assertDoesNotThrow(PackageManager::reload);
		assertDoesNotThrow(() -> PackageManager.get("starter"));
		assertThrows(com.airdropmc.exceptions.PackageNotFoundException.class, () -> PackageManager.get("broken"));
	}

	@Test
	void reload_rejectsMissingAndInvalidRawPrices() throws Exception {
		YamlConfiguration config = new YamlConfiguration();
		addPackage(config, "missing", null);
		addPackage(config, "numeric-string", "10");
		addPackage(config, "boolean", true);
		addPackage(config, "nan", Double.NaN);
		addPackage(config, "infinity", Double.POSITIVE_INFINITY);
		addPackage(config, "negative", -1);
		addPackage(config, "overflow", new BigDecimal("1e10000"));
		setPackagesConfig(config);

		PackageManager.reload();

		Set<String> loadedPackages = PackageManager.getPackages();
		for (String packageName : List.of(
				"missing", "numeric-string", "boolean", "nan", "infinity", "negative", "overflow")) {
			assertFalse(loadedPackages.contains(packageName), packageName);
			assertThrows(PackageNotFoundException.class, () -> PackageManager.get(packageName), packageName);
		}
	}

	@Test
	void reload_logsPackageNameAndRawInvalidValue() throws Exception {
		YamlConfiguration config = new YamlConfiguration();
		addPackage(config, "missing", null);
		addPackage(config, "text-price", "ten");
		setPackagesConfig(config);

		try (MockedStatic<AirdropLogger> logger = mockStatic(AirdropLogger.class)) {
			PackageManager.reload();

			logger.verify(() -> AirdropLogger.warning(argThat(message ->
					message.contains("missing") && message.contains("<missing>"))), atLeastOnce());
			logger.verify(() -> AirdropLogger.warning(argThat(message ->
					message.contains("text-price") && message.contains("ten"))), atLeastOnce());
		}
	}

	@Test
	void reload_acceptsIntegerAndFloatingPointZero() throws Exception {
		YamlConfiguration config = new YamlConfiguration();
		addPackage(config, "integer-zero", 0);
		addPackage(config, "double-zero", 0.0);
		setPackagesConfig(config);

		PackageManager.reload();

		assertTrue(PackageManager.getPackages().containsAll(Set.of("integer-zero", "double-zero")));
		assertEquals(0.0, PackageManager.get("integer-zero").getPrice());
		assertEquals(0.0, PackageManager.get("double-zero").getPrice());
	}

	private static void addPackage(YamlConfiguration config, String packageName, Object price) {
		config.set("packages." + packageName + ".items", List.of());
		if (price != null) {
			config.set("packages." + packageName + ".price", price);
		}
	}

	private static void setPackagesConfig(YamlConfiguration config) throws Exception {
		PackagesConfig packagesConfig = mock(PackagesConfig.class);
		when(packagesConfig.getConfig()).thenReturn(config);

		Field packagesConfigField = Airdrop.class.getDeclaredField("packagesConfiguration");
		packagesConfigField.setAccessible(true);
		packagesConfigField.set(null, packagesConfig);
	}
}
