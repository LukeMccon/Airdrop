package com.airdropmc.commands;

import com.airdropmc.Airdrop;
import com.airdropmc.api.AirdropStatus;
import com.airdropmc.api.AirdropVersions;
import com.airdropmc.api.EconomyState;
import com.airdropmc.api.ReadinessState;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.LanguageManager;
import com.airdropmc.lang.MessageKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatusCommandTest {

	private static final AirdropVersions VERSIONS = new AirdropVersions(
			"4.1.0-test", "1.0.0-test", "1.21.11", "21");

	private ServerMock server;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		ChatHandler.init(null);
		setReady(false);
	}

	@AfterEach
	void tearDown() throws Exception {
		ChatHandler.init(null);
		setReady(false);
		MockBukkit.unmock();
	}

	@Test
	void statusOutputIncludesEveryOperationalSignalAndCanonicalDocs() {
		PlayerMock operator = server.addPlayer();
		operator.setOp(true);
		AirdropStatus status = status(
				ReadinessState.READY,
				EconomyState.ACTIVE,
				"Vault Economy",
				OptionalInt.of(5),
				OptionalInt.of(12),
				Optional.of("CONFIGURATION"),
				Optional.of("Retained the previous valid configuration"));

		StatusCommand.sendStatus(operator, VERSIONS, status);

		String message = nextMessage(operator);
		for (String required : List.of(
				"Plugin: 4.1.0-test",
				"Extension API: 1.0.0-test",
				"Paper compatibility: 1.21.11",
				"Java compatibility: 21",
				"Readiness: READY",
				"Economy: ACTIVE",
				"Vault Economy",
				"Packages: 4",
				"revision 9",
				"Pending requests: 2",
				"Falling drops: 1 / 5",
				"Landed drops: 3 / 12",
				"CONFIGURATION",
				"Retained the previous valid configuration",
				"https://modrinth.com/plugin/airdrop")) {
			assertTrue(message.contains(required), required + " missing from: " + message);
		}
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("operationalStates")
	void statusRendersLifecycleAndEconomyStates(
			String description,
			ReadinessState readiness,
			EconomyState economy,
			String provider) {
		PlayerMock operator = server.addPlayer();
		operator.setOp(true);
		AirdropStatus status = status(
				readiness,
				economy,
				provider,
				OptionalInt.empty(),
				OptionalInt.empty(),
				Optional.empty(),
				Optional.empty());

		StatusCommand.sendStatus(operator, VERSIONS, status);

		String message = nextMessage(operator);
		assertTrue(message.contains("Readiness: " + readiness), description + ": " + message);
		assertTrue(message.contains("Economy: " + economy), description + ": " + message);
		assertTrue(message.contains("not published"), description + ": " + message);
		assertTrue(message.contains("Diagnostic: none"), description + ": " + message);
	}

	@Test
	void ordinaryPlayerGetsTheStandardAdminPermissionMessageBeforeReadinessGate() {
		PlayerMock player = server.addPlayer();

		assertTrue(new CmdAirdrop().onCommand(
				player, mock(Command.class), "airdrop", new String[]{"status"}));

		String message = nextMessage(player);
		assertTrue(message.contains("airdrop.admin"), message);
		assertFalse(message.contains("still starting"), message);
	}

	@Test
	void adminCanQueryRegisteredStartingServiceBeforeReadinessGate() throws Exception {
		Airdrop plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(plugin.getDataFolder().toPath());
		Files.writeString(
				plugin.getDataFolder().toPath().resolve("config.yml"),
				"language: en\neconomy:\n  enabled: false\n",
				StandardCharsets.UTF_8);
		Files.writeString(
				plugin.getDataFolder().toPath().resolve("packages.yml"),
				"packages: {}\n",
				StandardCharsets.UTF_8);
		server.getPluginManager().enablePlugin(plugin);
		PlayerMock operator = server.addPlayer();
		operator.setOp(true);

		assertTrue(new CmdAirdrop().onCommand(
				operator, mock(Command.class), "airdrop", new String[]{"status"}));

		String message = nextMessage(operator);
		assertTrue(message.contains("Readiness: STARTING"), message);
		assertTrue(message.contains("not published"), message);
		assertFalse(message.contains("still starting"), message);
		server.getPluginManager().disablePlugin(plugin);
	}

	@Test
	void consoleCanRenderStatus() {
		ConsoleCommandSender console = mock(ConsoleCommandSender.class);

		StatusCommand.sendStatus(
				console,
				VERSIONS,
				status(ReadinessState.STARTING, EconomyState.STARTING, null,
						OptionalInt.empty(), OptionalInt.empty(), Optional.empty(), Optional.empty()));

		verify(console).sendMessage(anyString());
	}

	@Test
	void localizedStatusUsesStableNamedPlaceholders() {
		LanguageManager language = mock(LanguageManager.class);
		when(language.get(eq(MessageKey.SYSTEM_STATUS_INFO), anyMap())).thenReturn("status details");
		when(language.get(MessageKey.SYSTEM_STATUS_VALUE_NONE)).thenReturn("aucun");
		when(language.get(MessageKey.SYSTEM_STATUS_VALUE_NOT_PUBLISHED)).thenReturn("non publié");
		ChatHandler.init(language);

		StatusCommand.sendStatus(
				server.addPlayer(),
				VERSIONS,
				status(ReadinessState.READY, EconomyState.DISABLED, null,
						OptionalInt.empty(), OptionalInt.empty(), Optional.empty(), Optional.empty()));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> placeholders = ArgumentCaptor.forClass(Map.class);
		verify(language).get(eq(MessageKey.SYSTEM_STATUS_INFO), placeholders.capture());
		assertEquals(Set.of(
				"plugin_version",
				"extension_api_version",
				"paper_version",
				"java_version",
				"readiness",
				"economy",
				"economy_provider",
				"package_count",
				"package_revision",
				"pending_count",
				"falling_count",
				"max_falling",
				"landed_count",
				"max_landed",
				"diagnostic_category",
				"diagnostic",
				"docs_url"), placeholders.getValue().keySet());
		assertEquals("aucun", placeholders.getValue().get("economy_provider"));
		assertEquals("aucun", placeholders.getValue().get("diagnostic"));
		assertEquals("non publié", placeholders.getValue().get("max_falling"));
		assertEquals("non publié", placeholders.getValue().get("max_landed"));
	}

	private static Stream<Arguments> operationalStates() {
		return Stream.of(
				Arguments.of("starting", ReadinessState.STARTING, EconomyState.STARTING, null),
				Arguments.of("ready economy disabled", ReadinessState.READY, EconomyState.DISABLED, null),
				Arguments.of("ready provider unavailable", ReadinessState.READY, EconomyState.UNAVAILABLE, null),
				Arguments.of("failed", ReadinessState.FAILED, EconomyState.UNAVAILABLE, null),
				Arguments.of("stopping", ReadinessState.STOPPING, EconomyState.DISABLED, null));
	}

	private static AirdropStatus status(
			ReadinessState readiness,
			EconomyState economy,
			String provider,
			OptionalInt maxFalling,
			OptionalInt maxLanded,
			Optional<String> diagnosticCategory,
			Optional<String> diagnostic) {
		return new AirdropStatus(
				readiness,
				economy,
				provider,
				9L,
				4,
				2,
				1,
				3,
				maxFalling,
				maxLanded,
				diagnosticCategory,
				diagnostic,
				List.of());
	}

	private String nextMessage(PlayerMock player) {
		Component message = player.nextComponentMessage();
		assertNotNull(message);
		return PlainTextComponentSerializer.plainText().serialize(message);
	}

	private void setReady(boolean ready) throws Exception {
		Field field = Airdrop.class.getDeclaredField("ready");
		field.setAccessible(true);
		field.set(null, ready);
	}
}
