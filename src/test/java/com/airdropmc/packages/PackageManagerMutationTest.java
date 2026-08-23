package com.airdropmc.packages;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.PackagesConfig;
import com.airdropmc.exceptions.DuplicatePackageException;
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
import java.util.concurrent.atomic.AtomicReference;

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
	private ServerMock server;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
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
		MockBukkit.unmock();
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
	void updatePackageInventory_successDetachesPersistedAndLiveItemsFromCallers() throws Exception {
		assertTrue(PackageManager.reload());
		reset(plugin, packagesConfig);
		when(packagesConfig.getConfig()).thenReturn(config);
		AtomicReference<FileConfiguration> savedCandidate = new AtomicReference<>();
		AtomicReference<String> persistedYaml = new AtomicReference<>();
		when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenAnswer(invocation -> {
			FileConfiguration candidate = invocation.getArgument(0);
			savedCandidate.set(candidate);
			persistedYaml.set(candidate.saveToString());
			return true;
		});
		ItemStack source = new ItemStack(Material.DIRT, 2);

		assertTrue(PackageManager.updatePackageInventory("starter", List.of(source)));

		source.setAmount(7);
		PackageManager.get("starter").getItems().getFirst().setAmount(9);

		assertEquals(persistedYaml.get(), savedCandidate.get().saveToString());
		assertEquals(2, ((ItemStack) savedCandidate.get()
				.getList("packages.starter.items").getFirst()).getAmount());
		assertEquals(2, PackageManager.get("starter").getItems().getFirst().getAmount());
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
	void createPackage_successPublishesDetachedCommittedPackage() throws Exception {
		assertTrue(PackageManager.reload());
		reset(plugin, packagesConfig);
		when(packagesConfig.getConfig()).thenReturn(config);
		AtomicReference<FileConfiguration> savedCandidate = new AtomicReference<>();
		AtomicReference<String> persistedYaml = new AtomicReference<>();
		when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenAnswer(invocation -> {
			FileConfiguration candidate = invocation.getArgument(0);
			savedCandidate.set(candidate);
			persistedYaml.set(candidate.saveToString());
			return true;
		});
		ItemStack source = new ItemStack(Material.STONE, 2);
		Package callerPackage = new Package("newpkg", 3.0, List.of(source));

		assertTrue(PackageManager.createPackage(callerPackage));

		source.setAmount(7);
		callerPackage.setItems(List.of(new ItemStack(Material.GOLD_BLOCK, 5)));

		Package committedPackage = PackageManager.get("newpkg");
		assertNotSame(callerPackage, committedPackage);
		assertEquals(Material.STONE, committedPackage.getItems().getFirst().getType());
		assertEquals(2, committedPackage.getItems().getFirst().getAmount());
		assertEquals(persistedYaml.get(), savedCandidate.get().saveToString());
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

	@Test
	void registryLookupAndDuplicateDetectionIgnoreCase() throws Exception {
		assertTrue(PackageManager.reload());

		assertSame(PackageManager.get("starter"), PackageManager.get("STARTER"));
		assertTrue(PackageManager.has("StArTeR"));
		assertThrows(DuplicatePackageException.class, () -> PackageManager.createPackage(
				new Package("STARTER", 3.0, List.of())));
	}

	@Test
	void differentlyCasedUpdateUsesStoredYamlKey() throws Exception {
		config.set("packages.Starter.price", 10.0);
		config.set("packages.Starter.items", List.of());
		config.set("packages.starter", null);
		assertTrue(PackageManager.reload());
		when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(true);

		assertTrue(PackageManager.updatePackageInventory(
				"STARTER", List.of(new ItemStack(Material.DIRT))));

		ArgumentCaptor<FileConfiguration> candidate = ArgumentCaptor.forClass(FileConfiguration.class);
		verify(packagesConfig).saveConfig(candidate.capture());
		assertTrue(candidate.getValue().isSet("packages.Starter.items"));
		assertFalse(candidate.getValue().isSet("packages.STARTER.items"));
	}

	@Test
	void differentlyCasedDeleteRemovesStoredYamlKey() throws Exception {
		config.set("packages.Starter.price", 10.0);
		config.set("packages.Starter.items", List.of());
		config.set("packages.starter", null);
		assertTrue(PackageManager.reload());
		when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(true);

		assertTrue(PackageManager.deletePackage("STARTER"));

		ArgumentCaptor<FileConfiguration> candidate = ArgumentCaptor.forClass(FileConfiguration.class);
		verify(packagesConfig).saveConfig(candidate.capture());
		assertFalse(candidate.getValue().isSet("packages.Starter"));
	}

	@Test
	void createPackageRejectsInvalidNameBeforePersistence() {
		assertThrows(IllegalArgumentException.class, () -> PackageManager.createPackage(
				new Package("reload", 3.0, List.of())));
		verify(packagesConfig, never()).saveConfig(any(FileConfiguration.class));
	}

	private static void setStaticField(String fieldName, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}
}
