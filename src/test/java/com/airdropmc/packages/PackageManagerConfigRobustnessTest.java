package com.airdropmc.packages;

import com.airdropmc.Airdrop;
import com.airdropmc.PackagesConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackageManagerConfigRobustnessTest {

	@BeforeEach
	void setUp() throws Exception {
		YamlConfiguration config = new YamlConfiguration();
		config.createSection("packages");
		config.set("packages.broken", "not-a-section");
		config.set("packages.starter.price", 10.0);
		config.set("packages.starter.items", java.util.List.of());

		PackagesConfig packagesConfig = mock(PackagesConfig.class);
		when(packagesConfig.getConfig()).thenReturn(config);

		Field packagesConfigField = Airdrop.class.getDeclaredField("packagesConfiguration");
		packagesConfigField.setAccessible(true);
		packagesConfigField.set(null, packagesConfig);

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
}
