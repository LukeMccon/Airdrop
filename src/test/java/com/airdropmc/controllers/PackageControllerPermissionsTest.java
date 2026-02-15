package com.airdropmc.controllers;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import org.bukkit.event.inventory.InventoryType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
