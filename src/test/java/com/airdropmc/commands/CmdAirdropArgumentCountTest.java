package com.airdropmc.commands;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.packages.PackageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CmdAirdropArgumentCountTest {

	private ServerMock server;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		ChatHandler.init(null);
		PackageManager.clear();
		YamlConfiguration config = new YamlConfiguration();
		config.set("packages.starter.price", 0.0);
		config.set("packages.starter.items", List.of());
		PackageManager.publishPackages(PackageManager.materializePackages(config));
		setReady(true);
	}

	@AfterEach
	void tearDown() throws Exception {
		PackageManager.clear();
		ChatHandler.init(null);
		setReady(false);
		MockBukkit.unmock();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidGenericCommandForms")
	void genericCommandFormsRejectInvalidArgumentCounts(String description, String[] args) {
		PlayerMock player = server.addPlayer();
		Command command = mock(Command.class);
		CmdAirdrop executor = new CmdAirdrop();

		assertFalse(executor.onCommand(player, command, "airdrop", args), description);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("validCommandForms")
	void exactCommandArgumentCountsDoNotFallThroughToUsage(String description, String[] args) throws Exception {
		PlayerMock player = server.addPlayer();
		Command command = mock(Command.class);
		CmdAirdrop executor = new CmdAirdrop();
		setReady(false);

		assertTrue(executor.onCommand(player, command, "airdrop", args), description);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidNestedCommandForms")
	void malformedNestedFormsUseTargetedFeedbackBeforeReadinessCheck(
			String description, String[] args, String expectedMessage) throws Exception {
		PlayerMock player = server.addPlayer();
		Command command = mock(Command.class);
		CmdAirdrop executor = new CmdAirdrop();
		setReady(false);

		assertTrue(executor.onCommand(player, command, "airdrop", args), description);

		String message = nextMessage(player);
		assertTrue(message.contains(expectedMessage), message);
		assertFalse(message.contains("still starting"), message);
	}

	private static Stream<Arguments> invalidGenericCommandForms() {
		return Stream.of(
				Arguments.of("missing command", new String[]{}),
				Arguments.of("version with extra argument", new String[]{"version", "extra"}),
				Arguments.of("packages with extra argument", new String[]{"packages", "extra"}),
				Arguments.of("reload with extra argument", new String[]{"reload", "extra"}),
				Arguments.of("direct drop with extra argument", new String[]{"starter", "extra"}),
				Arguments.of("package info with extra argument",
						new String[]{"package", "starter", "extra"}));
	}

	private static Stream<Arguments> validCommandForms() {
		return Stream.of(
				Arguments.of("version", new String[]{"version"}),
				Arguments.of("packages", new String[]{"packages"}),
				Arguments.of("reload", new String[]{"reload"}),
				Arguments.of("direct drop", new String[]{"starter"}),
				Arguments.of("package info", new String[]{"package", "starter"}),
				Arguments.of("package create", new String[]{"package", "create", "new_package", "10"}),
				Arguments.of("package delete", new String[]{"package", "delete", "starter"}));
	}

	private static Stream<Arguments> invalidNestedCommandForms() {
		return Stream.of(
				Arguments.of("package without a target", new String[]{"package"}, "specify a package name"),
				Arguments.of("create without name and price",
						new String[]{"package", "create"}, "requires a name and price"),
				Arguments.of("create without price",
						new String[]{"package", "create", "starter"}, "requires a name and price"),
				Arguments.of("create with an extra argument",
						new String[]{"package", "create", "starter", "10", "extra"},
						"requires a name and price"),
				Arguments.of("delete without a name",
						new String[]{"package", "delete"}, "specify a package name to delete"),
				Arguments.of("delete with an extra argument",
						new String[]{"package", "delete", "starter", "extra"},
						"specify a package name to delete"));
	}

	private String nextMessage(PlayerMock player) {
		Component message = player.nextComponentMessage();
		return PlainTextComponentSerializer.plainText().serialize(message);
	}

	private void setReady(boolean ready) throws Exception {
		Field field = Airdrop.class.getDeclaredField("ready");
		field.setAccessible(true);
		field.set(null, ready);
	}
}
