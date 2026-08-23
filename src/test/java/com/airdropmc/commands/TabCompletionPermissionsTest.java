package com.airdropmc.commands;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.AirdropTabCompleter;
import com.airdropmc.PackagesConfig;
import com.airdropmc.packages.PackageManager;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TabCompletionPermissionsTest {

	private ServerMock server;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		stubPackagesConfig();
		PackageManager.reload();
	}

	@AfterEach
	void tearDown() {
		PackageManager.clear();
		clearPackagesConfig();
		MockBukkit.unmock();
	}

	private void stubPackagesConfig() {
		YamlConfiguration config = new YamlConfiguration();
		config.set("packages.starter.price", 10.0);
		config.set("packages.starter.items", List.of());
		config.set("packages.broken.price", "ten");
		config.set("packages.broken.items", List.of());
		PackagesConfig packagesConfig = mock(PackagesConfig.class);
		org.mockito.Mockito.when(packagesConfig.getConfig()).thenReturn(config);
		try {
			Field field = Airdrop.class.getDeclaredField("packagesConfiguration");
			field.setAccessible(true);
			field.set(null, packagesConfig);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new IllegalStateException("Unable to stub packages configuration", e);
		}
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
	void airdropTabCompleter_hidesReloadForNonAdmin() {
		PlayerMock player = server.addPlayer();
		AirdropTabCompleter completer = new AirdropTabCompleter();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop", new String[]{""});

		assertFalse(results.contains("reload"));
	}

	@Test
	void airdropTabCompleter_suggestsConfiguredPackageNames_andNotPlaceholder() {
		PlayerMock player = server.addPlayer();
		AirdropTabCompleter completer = new AirdropTabCompleter();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop", new String[]{""});

		assertTrue(results.contains("starter"));
		assertFalse(results.contains("[packageName]"));
	}

	@Test
	void airdropTabCompleter_omitsPackagesWithInvalidPrices() {
		PlayerMock player = server.addPlayer();
		AirdropTabCompleter completer = new AirdropTabCompleter();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop", new String[]{""});

		assertFalse(results.contains("broken"));
	}

	@Test
	void airdropTabCompleter_showsReloadForAdmin() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		AirdropTabCompleter completer = new AirdropTabCompleter();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop", new String[]{""});

		assertTrue(results.contains("reload"));
	}

	@Test
	void packageTabCompletion_hidesAdminSubcommandsForNonAdmin() {
		PlayerMock player = server.addPlayer();
		PackageTabCompletion completer = new PackageTabCompletion();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop",
				new String[]{"package", ""});

		assertFalse(results.contains("create"));
		assertFalse(results.contains("delete"));
	}

	@Test
	void packageTabCompletion_showsAdminSubcommandsForAdmin() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		PackageTabCompletion completer = new PackageTabCompletion();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop",
				new String[]{"package", ""});

		assertTrue(results.contains("create"));
		assertTrue(results.contains("delete"));
	}

	@Test
	void packageTabCompletion_omitsPackagesWithInvalidPrices() {
		PlayerMock player = server.addPlayer();
		PackageTabCompletion completer = new PackageTabCompletion();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop",
				new String[]{"package", ""});

		assertFalse(results.contains("broken"));
	}

	@Test
	void packageTabCompletion_requiresAdminForCreateArguments() {
		PlayerMock player = server.addPlayer();
		PackageTabCompletion completer = new PackageTabCompletion();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop",
				new String[]{"package", "create", ""});

		assertEquals(List.of(), results);
	}

	@Test
	void packageTabCompletion_allowsCreateArgumentsForAdmin() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		PackageTabCompletion completer = new PackageTabCompletion();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop",
				new String[]{"package", "create", ""});

		assertEquals(List.of("[packageName]"), results);
	}

	@Test
	void packageTabCompletion_allowsCreatePriceForAdmin() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		PackageTabCompletion completer = new PackageTabCompletion();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop",
				new String[]{"package", "create", "starter", ""});

		assertEquals(List.of("[price]"), results);
	}
}
