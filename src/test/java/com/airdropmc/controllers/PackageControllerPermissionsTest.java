package com.airdropmc.controllers;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.LanguageManager;
import com.airdropmc.lang.MessageKey;
import org.bukkit.event.inventory.InventoryType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackageControllerPermissionsTest {

	private ServerMock server;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
	}

	@AfterEach
	void tearDown() {
		clearPackagesConfig();
		MockBukkit.unmock();
	}

	private void clearPackagesConfig() {
		try {
			Field field = Airdrop.class.getDeclaredField("packagesConfiguration");
			field.setAccessible(true);
			field.set(null, null);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new IllegalStateException("Unable to clear packages configuration", e);
		}
	}

	@Test
	void createPackageCommand_requiresAdmin() {
		PlayerMock player = server.addPlayer();

		PackageController.createPackageCommand(player, new String[]{"package", "create", "starter", "10.0"});

		Component message = player.nextComponentMessage();
		assertNotNull(message);
		String plain = PlainTextComponentSerializer.plainText().serialize(message);
		assertTrue(plain.contains("airdrop.admin"));
		assertNotEquals(InventoryType.CHEST, player.getOpenInventory().getType());
	}

	@Test
	void createPackageCommand_rejectsInvalidPriceValues() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);

		PackageController.createPackageCommand(player, new String[]{"package", "create", "starter", "NaN"});

		Component message = player.nextComponentMessage();
		assertNotNull(message);
		String plain = PlainTextComponentSerializer.plainText().serialize(message);
		assertTrue(plain.toLowerCase().contains("finite non-negative"));
		assertNotEquals(InventoryType.CHEST, player.getOpenInventory().getType());
	}

	@Test
	void createPackageCommand_rejectsInvalidPackageNameCharacters() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);

		PackageController.createPackageCommand(player, new String[]{"package", "create", "test.items", "10.0"});

		Component message = player.nextComponentMessage();
		assertNotNull(message);
		String plain = PlainTextComponentSerializer.plainText().serialize(message);
		assertTrue(plain.toLowerCase().contains("letters, numbers"));
		assertNotEquals(InventoryType.CHEST, player.getOpenInventory().getType());
	}

	@Test
	void createPackageCommandRejectsReservedNamesBeforeOpeningGui() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);

		for (String name : List.of(
				"all", "*", "package", "packages", "version", "reload", "create", "DELETE", "ReLoAd")) {
			PackageController.createPackageCommand(player,
					new String[]{"package", "create", name, "10.0"});

			assertNotEquals(InventoryType.CHEST, player.getOpenInventory().getType(), name);
			Component message = player.nextComponentMessage();
			assertNotNull(message, name);
			assertTrue(PlainTextComponentSerializer.plainText().serialize(message)
					.toLowerCase(Locale.ROOT).contains("reserved"), name);
		}
	}

	@Test
	void bothPublicCreateOverloadsReuseManagerNamePolicy() {
		assertThrows(IllegalArgumentException.class,
				() -> PackageController.createPackage("all", 1.0));
		assertThrows(IllegalArgumentException.class,
				() -> PackageController.createPackage("reload", 1.0, List.of()));
		assertThrows(IllegalArgumentException.class,
				() -> PackageController.createPackage("create", 1.0));
		assertThrows(IllegalArgumentException.class,
				() -> PackageController.createPackage("delete", 1.0, List.of()));
	}

	@Test
	void reservedNameUsesDedicatedMessageWithLegacyLanguageConfiguration() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		LanguageManager language = mock(LanguageManager.class);
		when(language.get(MessageKey.PREFIX)).thenReturn("[Airdrop]");
		when(language.get(MessageKey.PACKAGES_NAME_INVALID)).thenReturn("legacy character message");
		when(language.get(MessageKey.PACKAGES_NAME_RESERVED)).thenReturn("package name is reserved");
		ChatHandler.init(language);

		try {
			PackageController.createPackageCommand(player,
					new String[]{"package", "create", "reload", "10.0"});

			Component message = player.nextComponentMessage();
			assertNotNull(message);
			String text = PlainTextComponentSerializer.plainText().serialize(message);
			assertTrue(text.contains("package name is reserved"), text);
		} finally {
			ChatHandler.init(null);
		}
	}
}
