package com.airdropmc.commands;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.AirdropTabCompleter;
import com.airdropmc.packages.PackageManager;
import com.airdropmc.packages.PackageMaterializationException;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TabCompletionPermissionsTest {

	private ServerMock server;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		PackageManager.clear();
		publishPackages(validPackagesConfig());
		setReady(true);
	}

	@AfterEach
	void tearDown() throws Exception {
		PackageManager.clear();
		setReady(false);
		MockBukkit.unmock();
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
		player.setOp(true);
		AirdropTabCompleter completer = new AirdropTabCompleter();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop", new String[]{""});

		assertTrue(results.contains("starter"));
		assertFalse(results.contains("[packageName]"));
	}

	@Test
	void airdropTabCompleterShowsOnlyPackagesAndCommandsPlayerCanUse() {
		Player player = mock(Player.class);
		when(player.hasPermission("airdrop.package.starter")).thenReturn(true);
		AirdropTabCompleter completer = new AirdropTabCompleter();

		List<String> results = completer.onTabComplete(
				player, mock(Command.class), "airdrop", new String[]{""});

		assertEquals(List.of("package", "starter", "version"), results);
	}

	@Test
	void airdropTabCompleter_omitsPackagesFromRejectedCandidates() {
		YamlConfiguration invalidCandidate = validPackagesConfig();
		invalidCandidate.set("packages.broken.price", "ten");
		invalidCandidate.set("packages.broken.items", List.of());
		assertThrows(PackageMaterializationException.class, () -> publishPackages(invalidCandidate));
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		AirdropTabCompleter completer = new AirdropTabCompleter();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop", new String[]{""});

		assertTrue(results.contains("starter"));
		assertFalse(results.contains("broken"));
	}

	@Test
	void airdropTabCompleter_showsReloadForAdmin() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		AirdropTabCompleter completer = new AirdropTabCompleter();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop", new String[]{""});

		assertTrue(results.contains("reload"));
		assertTrue(results.contains("packages"));
	}

	@Test
	void topLevelCompletionFiltersPrefixesWithoutCaseDifferences() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		AirdropTabCompleter completer = new AirdropTabCompleter();
		Command command = mock(Command.class);

		assertEquals(List.of("reload"),
				completer.onTabComplete(player, command, "airdrop", new String[]{"r"}));
		assertEquals(List.of("reload"),
				completer.onTabComplete(player, command, "airdrop", new String[]{"R"}));
		assertEquals(List.of("reload"),
				completer.onTabComplete(player, command, "airdrop", new String[]{"ReL"}));
	}

	@Test
	void notReadyCompletionFiltersVersionByPrefix() throws Exception {
		PlayerMock player = server.addPlayer();
		AirdropTabCompleter completer = new AirdropTabCompleter();
		Command command = mock(Command.class);
		setReady(false);

		assertEquals(List.of("version"),
				completer.onTabComplete(player, command, "airdrop", new String[]{"VeR"}));
		assertEquals(List.of(),
				completer.onTabComplete(player, command, "airdrop", new String[]{"re"}));
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
	void nestedCompletionFiltersPrefixesWithoutCaseDifferences() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		PackageTabCompletion completer = new PackageTabCompletion();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop",
				new String[]{"package", "CrE"});

		assertEquals(List.of("create"), results);
	}

	@Test
	void adminCommandBlockSeesOnlyExecutableAdminCommands() {
		BlockCommandSender sender = mock(BlockCommandSender.class);
		when(sender.hasPermission("airdrop.admin")).thenReturn(true);
		Command command = mock(Command.class);

		List<String> topLevel = new AirdropTabCompleter().onTabComplete(
				sender, command, "airdrop", new String[]{""});
		List<String> nested = new PackageTabCompletion().onTabComplete(
				sender, command, "airdrop", new String[]{"package", ""});
		List<String> deleteTargets = new PackageTabCompletion().onTabComplete(
				sender, command, "airdrop", new String[]{"package", "delete", "ST"});
		List<String> createTargets = new PackageTabCompletion().onTabComplete(
				sender, command, "airdrop", new String[]{"package", "create", ""});

		assertEquals(List.of("package", "reload", "version"), topLevel);
		assertTrue(nested.contains("delete"));
		assertFalse(nested.contains("create"));
		assertEquals(List.of("starter"), deleteTargets);
		assertEquals(List.of(), createTargets);
	}

	@Test
	void packageTabCompletion_omitsPackagesFromRejectedCandidates() {
		YamlConfiguration invalidCandidate = validPackagesConfig();
		invalidCandidate.set("packages.broken.price", "ten");
		invalidCandidate.set("packages.broken.items", List.of());
		assertThrows(PackageMaterializationException.class, () -> publishPackages(invalidCandidate));
		PlayerMock player = server.addPlayer();
		PackageTabCompletion completer = new PackageTabCompletion();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop",
				new String[]{"package", ""});

		assertTrue(results.contains("starter"));
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
	void packageTabCompletionDoesNotSuggestArbitraryCreateName() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		PackageTabCompletion completer = new PackageTabCompletion();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop",
				new String[]{"package", "create", ""});

		assertEquals(List.of(), results);
	}

	@Test
	void packageTabCompletionDoesNotSuggestArbitraryCreatePrice() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		PackageTabCompletion completer = new PackageTabCompletion();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop",
				new String[]{"package", "create", "starter", ""});

		assertEquals(List.of(), results);
	}

	@Test
	void packageDeleteCompletionSuggestsRealPackagesByPrefix() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		PackageTabCompletion completer = new PackageTabCompletion();

		List<String> results = completer.onTabComplete(player, mock(Command.class), "airdrop",
				new String[]{"package", "delete", "pR"});

		assertEquals(List.of("Premium"), results);
	}

	@Test
	void topLevelCompletionPreservesLastKnownGoodDisplayCaseWhenReservedCandidateIsRejected() {
		YamlConfiguration invalidCandidate = configWithReservedPackages();
		assertThrows(PackageMaterializationException.class, () -> publishPackages(invalidCandidate));
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		AirdropTabCompleter completer = new AirdropTabCompleter();

		List<String> results = completer.onTabComplete(
				player, mock(Command.class), "airdrop", new String[]{""});

		assertEquals(1, results.stream().filter("Premium"::equals).count());
		assertFalse(results.contains("premium"));
		assertFalse(results.contains("all"));
		for (String commandName : List.of("package", "packages", "version", "reload")) {
			assertEquals(1, results.stream().filter(commandName::equals).count(), commandName);
		}
	}

	@Test
	void packageCompletionPreservesLastKnownGoodDisplayCaseWhenReservedCandidateIsRejected() {
		YamlConfiguration invalidCandidate = configWithReservedPackages();
		assertThrows(PackageMaterializationException.class, () -> publishPackages(invalidCandidate));
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		PackageTabCompletion completer = new PackageTabCompletion();

		List<String> results = completer.onTabComplete(
				player, mock(Command.class), "airdrop", new String[]{"package", ""});

		assertEquals(1, results.stream().filter("Premium"::equals).count());
		assertFalse(results.contains("premium"));
		for (String reserved : List.of("all", "package", "packages", "version", "reload")) {
			assertFalse(results.contains(reserved), reserved);
		}
		for (String subcommand : List.of("create", "delete")) {
			assertEquals(1, results.stream().filter(subcommand::equals).count(), subcommand);
		}
	}

	private YamlConfiguration validPackagesConfig() {
		YamlConfiguration config = new YamlConfiguration();
		config.set("packages.starter.price", 10.0);
		config.set("packages.starter.items", List.of());
		config.set("packages.Premium.price", 15.0);
		config.set("packages.Premium.items", List.of());
		return config;
	}

	private YamlConfiguration configWithReservedPackages() {
		YamlConfiguration config = validPackagesConfig();
		for (String reserved : List.of("all", "package", "packages", "version", "reload", "create", "delete")) {
			config.set("packages." + reserved + ".price", 1.0);
			config.set("packages." + reserved + ".items", List.of());
		}
		return config;
	}

	private void publishPackages(YamlConfiguration config) throws PackageMaterializationException {
		PackageManager.publishPackages(PackageManager.materializePackages(config));
	}

	private void setReady(boolean ready) throws Exception {
		Field field = Airdrop.class.getDeclaredField("ready");
		field.setAccessible(true);
		field.set(null, ready);
	}
}
