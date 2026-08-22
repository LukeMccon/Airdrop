package com.airdropmc.packages;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.PackagesConfig;
import com.airdropmc.controllers.PackageController;
import com.airdropmc.exceptions.PackageNotFoundException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackagePersistenceFailureFeedbackTest {

	private ServerMock server;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();

		YamlConfiguration config = new YamlConfiguration();
		config.set("packages.starter.price", 10.0);
		config.set("packages.starter.items", List.of(new ItemStack(Material.STONE, 2)));

		PackagesConfig packagesConfig = mock(PackagesConfig.class);
		when(packagesConfig.getConfig()).thenReturn(config);
		when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(false);

		setAirdropStaticField("packagesConfiguration", packagesConfig);
		PackageManager.clear();
		assertTrue(PackageManager.reload());
	}

	@AfterEach
	void tearDown() throws Exception {
		PackageManager.clear();
		clearAirdropStaticFields();
		MockBukkit.unmock();
	}

	@Test
	void existingPackageSaveFailureKeepsEditorOpenAndReportsNoChanges() throws Exception {
		PlayerMock player = operator();
		PackageGui gui = new PackageGui(PackageManager.get("starter"));
		gui.openInventory(player);
		player.getOpenInventory().getTopInventory().setItem(0, new ItemStack(Material.DIRT, 3));

		gui.save(saveClick(player));

		assertEquals(InventoryType.CHEST, player.getOpenInventory().getType());
		assertFailureWithoutSuccess(player, "saved successfully");
		List<ItemStack> persistedItems = PackageManager.get("starter").getItems();
		assertEquals(1, persistedItems.size());
		assertEquals(Material.STONE, persistedItems.get(0).getType());
		assertEquals(2, persistedItems.get(0).getAmount());
		assertTrue(persistedItems.stream().noneMatch(item -> item.getType() == Material.DIRT));
	}

	@Test
	void createPackageSaveFailureKeepsEditorOpenAndReportsNoChanges() {
		PlayerMock player = operator();
		CreatePackageGui gui = new CreatePackageGui("newpkg", 3.0);
		gui.openInventory(player);

		gui.save(saveClick(player));

		assertEquals(InventoryType.CHEST, player.getOpenInventory().getType());
		assertFailureWithoutSuccess(player, "created successfully");
		assertThrows(PackageNotFoundException.class, () -> PackageManager.get("newpkg"));
	}

	@Test
	void deletePackageFailureReportsNoChangesAndKeepsPackageAvailable() throws Exception {
		PlayerMock player = operator();

		PackageController.deletePackageCommand(player, new String[]{"package", "delete", "starter"});

		assertFailureWithoutSuccess(player, "successfully deleted");
		assertDoesNotThrow(() -> PackageManager.get("starter"));
	}

	private PlayerMock operator() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		return player;
	}

	private InventoryClickEvent saveClick(PlayerMock player) {
		return new InventoryClickEvent(
				player.getOpenInventory(),
				InventoryType.SlotType.CONTAINER,
				player.getOpenInventory().getTopInventory().getSize() - 2,
				ClickType.LEFT,
				InventoryAction.PICKUP_ALL);
	}

	private void assertFailureWithoutSuccess(PlayerMock player, String successText) {
		List<String> messages = new ArrayList<>();
		Component message;
		while ((message = player.nextComponentMessage()) != null) {
			messages.add(PlainTextComponentSerializer.plainText().serialize(message));
		}

		assertTrue(messages.stream().anyMatch(value -> value.contains("No changes were made")),
				() -> "Expected persistence failure feedback but got: " + messages);
		assertTrue(messages.stream().noneMatch(value -> value.contains(successText)),
				() -> "Unexpected success feedback: " + messages);
	}

	private static void setAirdropStaticField(String fieldName, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}

	private static void clearAirdropStaticFields() throws Exception {
		for (Field field : Airdrop.class.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
				field.setAccessible(true);
				field.set(null, null);
			}
		}
	}
}
