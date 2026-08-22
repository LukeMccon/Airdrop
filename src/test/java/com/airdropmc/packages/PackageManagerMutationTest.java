package com.airdropmc.packages;

import com.airdropmc.Airdrop;
import com.airdropmc.PackagesConfig;
import com.airdropmc.exceptions.PackageNotFoundException;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
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
		when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(true);
		when(plugin.isEnabled()).thenReturn(true);

		boolean updated = PackageManager.updatePackageInventory("starter",
				List.of(new ItemStack(Material.DIRT, 1)));

		assertTrue(updated);
		assertEquals(1, PackageManager.get("starter").getItems().size());
		ArgumentCaptor<FileConfiguration> candidateCaptor = ArgumentCaptor.forClass(FileConfiguration.class);
		verify(packagesConfig).saveConfig(candidateCaptor.capture());
		assertNotSame(config, candidateCaptor.getValue());
		assertEquals(Material.DIRT,
				((ItemStack) candidateCaptor.getValue().getList("packages.starter.items").get(0)).getType());
		verify(packagesConfig, never()).reloadConfig();
		verify(plugin, never()).setupPackageGuis();
		verify(config, never()).set(any(String.class), any());
	}

	@Test
	void createPackage_doesNotTriggerFullReloadOrGuiRebuild() throws Exception {
		assertTrue(PackageManager.reload());
		reset(plugin, packagesConfig);
		clearInvocations(config);
		when(packagesConfig.getConfig()).thenReturn(config);
		when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(true);
		when(plugin.isEnabled()).thenReturn(true);
		PackagesGui packagesGui = mock(PackagesGui.class);
		setStaticField("packagesGui", packagesGui);

		boolean created = PackageManager.createPackage(
				new Package("newpkg", 3.0, List.of(new ItemStack(Material.STONE, 1))));

		assertTrue(created);
		assertDoesNotThrow(() -> PackageManager.get("newpkg"));
		ArgumentCaptor<FileConfiguration> candidateCaptor = ArgumentCaptor.forClass(FileConfiguration.class);
		verify(packagesConfig).saveConfig(candidateCaptor.capture());
		FileConfiguration candidate = candidateCaptor.getValue();
		assertNotSame(config, candidate);
		assertEquals(3.0, candidate.getDouble("packages.newpkg.price"));
		assertEquals(Material.STONE,
				((ItemStack) candidate.getList("packages.newpkg.items").get(0)).getType());
		verify(packagesConfig, never()).reloadConfig();
		verify(plugin, never()).setupPackageGuis();
		verify(config, never()).set(any(String.class), any());
		verify(packagesGui, times(1)).initializeItems();
	}

	@Test
	void deletePackage_doesNotTriggerFullReloadOrGuiRebuild() throws Exception {
		assertTrue(PackageManager.reload());
		reset(plugin, packagesConfig);
		clearInvocations(config);
		when(packagesConfig.getConfig()).thenReturn(config);
		when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(true);
		when(plugin.isEnabled()).thenReturn(true);
		PackagesGui packagesGui = mock(PackagesGui.class);
		setStaticField("packagesGui", packagesGui);

		boolean deleted = PackageManager.deletePackage("starter");

		assertTrue(deleted);
		assertThrows(PackageNotFoundException.class, () -> PackageManager.get("starter"));
		ArgumentCaptor<FileConfiguration> candidateCaptor = ArgumentCaptor.forClass(FileConfiguration.class);
		verify(packagesConfig).saveConfig(candidateCaptor.capture());
		assertNotSame(config, candidateCaptor.getValue());
		assertFalse(candidateCaptor.getValue().isSet("packages.starter"));
		verify(packagesConfig, never()).reloadConfig();
		verify(plugin, never()).setupPackageGuis();
		verify(config, never()).set(any(String.class), any());
		verify(packagesGui, times(1)).initializeItems();
	}

	@Test
	void updatePackageInventory_failedCandidateSaveLeavesLiveStateUnchanged() throws Exception {
		assertTrue(PackageManager.reload());
		reset(plugin, packagesConfig);
		clearInvocations(config);
		when(packagesConfig.getConfig()).thenReturn(config);
		when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(false);
		Package original = PackageManager.get("starter");
		String originalYaml = config.saveToString();
		clearInvocations(config);

		boolean updated = PackageManager.updatePackageInventory("starter",
				List.of(new ItemStack(Material.DIRT, 1)));

		assertFalse(updated);
		assertSame(original, PackageManager.get("starter"));
		assertTrue(PackageManager.get("starter").getItems().isEmpty());
		assertEquals(originalYaml, config.saveToString());
		verify(config, never()).set(any(String.class), any());
	}

	@Test
	void createPackage_failedCandidateSaveLeavesLiveStateUnchanged() throws Exception {
		assertTrue(PackageManager.reload());
		reset(plugin, packagesConfig);
		clearInvocations(config);
		when(packagesConfig.getConfig()).thenReturn(config);
		when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(false);
		PackagesGui packagesGui = mock(PackagesGui.class);
		setStaticField("packagesGui", packagesGui);
		Package original = PackageManager.get("starter");
		String originalYaml = config.saveToString();
		clearInvocations(config);

		boolean created = PackageManager.createPackage(
				new Package("newpkg", 3.0, List.of(new ItemStack(Material.STONE, 1))));

		assertFalse(created);
		assertThrows(PackageNotFoundException.class, () -> PackageManager.get("newpkg"));
		assertSame(original, PackageManager.get("starter"));
		assertEquals(originalYaml, config.saveToString());
		verify(config, never()).set(any(String.class), any());
		verify(packagesGui, never()).initializeItems();
	}

	@Test
	void deletePackage_failedCandidateSaveLeavesLiveStateUnchanged() throws Exception {
		assertTrue(PackageManager.reload());
		reset(plugin, packagesConfig);
		clearInvocations(config);
		when(packagesConfig.getConfig()).thenReturn(config);
		when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(false);
		PackagesGui packagesGui = mock(PackagesGui.class);
		setStaticField("packagesGui", packagesGui);
		Package original = PackageManager.get("starter");
		String originalYaml = config.saveToString();
		clearInvocations(config);

		boolean deleted = PackageManager.deletePackage("starter");

		assertFalse(deleted);
		assertSame(original, PackageManager.get("starter"));
		assertEquals(originalYaml, config.saveToString());
		verify(config, never()).set(any(String.class), any());
		verify(packagesGui, never()).initializeItems();
	}

	private static void setStaticField(String fieldName, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}
}
