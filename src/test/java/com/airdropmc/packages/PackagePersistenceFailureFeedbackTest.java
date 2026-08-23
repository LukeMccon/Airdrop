package com.airdropmc.packages;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.PackagesConfig;
import com.airdropmc.controllers.PackageController;
import com.airdropmc.exceptions.PackageNotFoundException;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.LanguageManager;
import com.airdropmc.lang.MessageKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackagePersistenceFailureFeedbackTest {

	private ServerMock server;
	private PackagesConfig packagesConfig;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();

		YamlConfiguration config = new YamlConfiguration();
		config.set("packages.starter.price", 10.0);
		config.set("packages.starter.items", List.of(new ItemStack(Material.STONE, 2)));

		packagesConfig = mock(PackagesConfig.class);
		when(packagesConfig.getConfig()).thenReturn(config);
		when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(false);

		setAirdropStaticField("packagesConfiguration", packagesConfig);
		MockPlugin eventPlugin = MockBukkit.createMockPlugin("AirdropPersistenceHarness");
		Airdrop plugin = mock(Airdrop.class);
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.getPluginLoader()).thenReturn(eventPlugin.getPluginLoader());
		when(plugin.getName()).thenReturn("Airdrop");
		when(plugin.getServer()).thenReturn(server);
		setAirdropStaticField("pluginInstance", plugin);
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
	void existingPackageSaveFailureKeepsEditorOpenAndSuccessfulRetryClosesIt() throws Exception {
		PlayerMock player = operator();
		PackageGui gui = new PackageGui(PackageManager.get("starter"));
		gui.openInventory(player);
		Inventory editor = player.getOpenInventory().getTopInventory();
		editor.setItem(0, new ItemStack(Material.DIRT, 3));
		player.getInventory().setItem(0, new ItemStack(Material.GOLD_INGOT, 4));
		InventoryClickEvent saveEvent = saveClick(player);

		gui.save(saveEvent);

		assertSame(editor, player.getOpenInventory().getTopInventory());
		assertFailureWithoutSuccess(player, "saved successfully");
		ItemStack changedPlayerItem = player.getInventory().getItem(0);
		assertNotNull(changedPlayerItem);
		assertEquals(Material.GOLD_INGOT, changedPlayerItem.getType());
		assertEquals(4, changedPlayerItem.getAmount());
		List<ItemStack> persistedItems = PackageManager.get("starter").getItems();
		assertEquals(1, persistedItems.size());
		assertEquals(Material.STONE, persistedItems.get(0).getType());
		assertEquals(2, persistedItems.get(0).getAmount());
		assertTrue(persistedItems.stream().noneMatch(item -> item.getType() == Material.DIRT));

		when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(true);
		gui.save(saveEvent);

		assertSame(editor, player.getOpenInventory().getTopInventory());
		server.getScheduler().performOneTick();

		assertNotEquals(InventoryType.CHEST, player.getOpenInventory().getType());
		assertEquals(new ItemStack(Material.GOLD_INGOT, 4), player.getInventory().getItem(0));
		assertNextMessageContains(player, "saved successfully");
	}

	@Test
	void createPackageSaveFailureKeepsEditorOpenAndReportsNoChanges() {
		PlayerMock player = operator();
		CreatePackageGui gui = new CreatePackageGui("newpkg", 3.0);
		gui.openInventory(player);
		Inventory editor = player.getOpenInventory().getTopInventory();

		gui.save(saveClick(player));

		assertSame(editor, player.getOpenInventory().getTopInventory());
		assertFailureWithoutSuccess(player, "created successfully");
		assertThrows(PackageNotFoundException.class, () -> PackageManager.get("newpkg"));
	}

	@Test
	void existingPackageSavePersistsOnlyEditableSlots() throws Exception {
		when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(true);
		PlayerMock player = operator();
		PackageGui gui = new PackageGui(PackageManager.get("starter"));
		assertTrue(gui.openInventory(player));
		Inventory editor = player.getOpenInventory().getTopInventory();
		fillEditableSlots(editor);
		editor.setItem(PackageManager.MAX_PACKAGE_ITEM_STACKS, new ItemStack(Material.DIAMOND));
		editor.setItem(editor.getSize() - 1, new ItemStack(Material.EMERALD));
		ItemStack firstEditorItem = editor.getItem(0);

		gui.save(saveClick(player));
		server.getScheduler().performOneTick();

		List<ItemStack> saved = PackageManager.get("starter").getItems();
		assertEquals(PackageManager.MAX_PACKAGE_ITEM_STACKS, saved.size());
		assertTrue(saved.stream().allMatch(item -> item.getType() == Material.DIRT));
		assertNotSame(firstEditorItem, saved.get(0));
	}

	@Test
	void createdPackageSavePersistsOnlyEditableSlots() throws Exception {
		when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(true);
		PlayerMock player = operator();
		CreatePackageGui gui = new CreatePackageGui("newpkg", 3.0);
		assertTrue(gui.openInventory(player));
		Inventory editor = player.getOpenInventory().getTopInventory();
		fillEditableSlots(editor);
		editor.setItem(PackageManager.MAX_PACKAGE_ITEM_STACKS, new ItemStack(Material.DIAMOND));
		editor.setItem(editor.getSize() - 1, new ItemStack(Material.EMERALD));

		gui.save(saveClick(player));
		server.getScheduler().performOneTick();

		List<ItemStack> saved = PackageManager.get("newpkg").getItems();
		assertEquals(PackageManager.MAX_PACKAGE_ITEM_STACKS, saved.size());
		assertTrue(saved.stream().allMatch(item -> item.getType() == Material.DIRT));
	}

	@Test
	void deletePackageFailureReportsNoChangesAndKeepsPackageAvailable() throws Exception {
		PlayerMock player = operator();

		PackageController.deletePackageCommand(player, new String[]{"package", "delete", "starter"});

		assertFailureWithoutSuccess(player, "successfully deleted");
		assertDoesNotThrow(() -> PackageManager.get("starter"));
	}

	@Test
	void directlyConstructedInvalidCreateGuiCannotPersistPackage() {
		when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(true);
		PlayerMock player = operator();
		CreatePackageGui gui = new CreatePackageGui("reload", 3.0);
		gui.openInventory(player);
		Inventory editor = player.getOpenInventory().getTopInventory();
		LanguageManager language = mock(LanguageManager.class);
		when(language.get(MessageKey.PREFIX)).thenReturn("[Airdrop]");
		when(language.get(MessageKey.PACKAGES_NAME_INVALID)).thenReturn("legacy character message");
		when(language.get(MessageKey.PACKAGES_NAME_RESERVED)).thenReturn("package name is reserved");
		ChatHandler.init(language);

		try {
			gui.save(saveClick(player));

			assertSame(editor, player.getOpenInventory().getTopInventory());
			assertThrows(PackageNotFoundException.class, () -> PackageManager.get("reload"));
			Component message = player.nextComponentMessage();
			assertNotNull(message);
			assertTrue(PlainTextComponentSerializer.plainText().serialize(message)
					.toLowerCase(Locale.ROOT).contains("reserved"));
		} finally {
			ChatHandler.init(null);
		}
	}

	private PlayerMock operator() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		return player;
	}

	private void fillEditableSlots(Inventory editor) {
		for (int slot = 0; slot < PackageManager.MAX_PACKAGE_ITEM_STACKS; slot++) {
			editor.setItem(slot, new ItemStack(Material.DIRT, 1));
		}
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

	private void assertNextMessageContains(PlayerMock player, String expectedText) {
		Component message = player.nextComponentMessage();
		assertNotNull(message);
		String plainMessage = PlainTextComponentSerializer.plainText().serialize(message);
		assertTrue(plainMessage.contains(expectedText), () -> "Unexpected message: " + plainMessage);
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
