package com.airdropmc.packages;

import com.airdropmc.Airdrop;
import com.airdropmc.PackagesConfig;
import com.airdropmc.exceptions.PackageNotFoundException;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackageManagerMutationTest {

	private YamlConfiguration config;
	private PackagesConfig packagesConfig;
	private Airdrop plugin;

	@BeforeEach
	void setUp() throws Exception {
		config = spy(new YamlConfiguration());
		config.createSection("packages");
		config.set("packages.starter.price", 10.0);
		config.set("packages.starter.items", List.of());

		packagesConfig = mock(PackagesConfig.class);
		when(packagesConfig.getConfig()).thenReturn(config);

		plugin = mock(Airdrop.class);
		when(plugin.isEnabled()).thenReturn(true);

		setStaticField("packagesConfiguration", packagesConfig);
		setStaticField("pluginInstance", plugin);
		setStaticField("packagesGui", null);
		PackageManager.clear();
	}

	@AfterEach
	void tearDown() throws Exception {
		PackageManager.clear();
		setStaticField("packagesGui", null);
		setStaticField("packagesConfiguration", null);
		setStaticField("pluginInstance", null);
	}

	@Test
	void updatePackageInventory_doesNotTriggerFullReloadOrGuiRebuild() throws Exception {
		assertTrue(PackageManager.reload());
		reset(plugin, packagesConfig);
		clearInvocations(config);
		when(packagesConfig.getConfig()).thenReturn(config);
		when(plugin.isEnabled()).thenReturn(true);

		PackageManager.updatePackageInventory("starter", List.of(new ItemStack(Material.DIRT, 1)));

		assertEquals(1, PackageManager.get("starter").getItems().size());
		verify(packagesConfig).saveConfig();
		verify(packagesConfig, never()).reloadConfig();
		verify(plugin, never()).setupPackageGuis();
		verify(config, never()).set(eq("packages"), any());
	}

	@Test
	void createPackage_doesNotTriggerFullReloadOrGuiRebuild() throws Exception {
		assertTrue(PackageManager.reload());
		reset(plugin, packagesConfig);
		when(packagesConfig.getConfig()).thenReturn(config);
		when(plugin.isEnabled()).thenReturn(true);

		PackageManager.createPackage(new Package("newpkg", 3.0, List.of(new ItemStack(Material.STONE, 1))));

		assertDoesNotThrow(() -> PackageManager.get("newpkg"));
		verify(packagesConfig).saveConfig();
		verify(packagesConfig, never()).reloadConfig();
		verify(plugin, never()).setupPackageGuis();
	}

	@Test
	void deletePackage_doesNotTriggerFullReloadOrGuiRebuild() throws Exception {
		assertTrue(PackageManager.reload());
		reset(plugin, packagesConfig);
		clearInvocations(config);
		when(packagesConfig.getConfig()).thenReturn(config);
		when(plugin.isEnabled()).thenReturn(true);

		PackageManager.deletePackage("starter");

		assertThrows(PackageNotFoundException.class, () -> PackageManager.get("starter"));
		verify(packagesConfig).saveConfig();
		verify(packagesConfig, never()).reloadConfig();
		verify(plugin, never()).setupPackageGuis();
		verify(config, never()).set(eq("packages"), any());
	}

	private static void setStaticField(String fieldName, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}
}
