package com.airdropmc.commands;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.AirdropTabCompleter;
import com.airdropmc.PackagesConfig;
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
	}

	@AfterEach
	void tearDown() {
		clearPackagesConfig();
		MockBukkit.unmock();
	}

	private void stubPackagesConfig() {
		YamlConfiguration config = new YamlConfiguration();
		config.createSection("packages.starter");
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
		assertFalse(results.contains("debug"));
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
	void airdropTabCompleter_showsReloadForAdmin() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		AirdropTabCompleter completer = new AirdropTabCompleter();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop", new String[]{""});

		assertTrue(results.contains("reload"));
		assertTrue(results.contains("debug"));
	}

	@Test
	void airdropTabCompleter_hidesDebugArgumentsForNonAdmin() {
		PlayerMock player = server.addPlayer();
		AirdropTabCompleter completer = new AirdropTabCompleter();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop",
				new String[]{"debug", ""});

		assertEquals(List.of(), results);
	}

	@Test
	void airdropTabCompleter_showsDebugArgumentsForAdmin() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		AirdropTabCompleter completer = new AirdropTabCompleter();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop",
				new String[]{"debug", ""});

		assertEquals(List.of("on", "off", "toggle"), results);
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
