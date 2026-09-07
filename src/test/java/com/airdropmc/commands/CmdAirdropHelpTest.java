package com.airdropmc.commands;

import com.airdropmc.Airdrop;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.DropRejection;
import com.airdropmc.api.DropRejectionReason;
import com.airdropmc.api.DropRequestDescriptor;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.api.DropSource;
import com.airdropmc.api.PaymentStatus;
import com.airdropmc.controllers.DropController;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.internal.drop.DefaultDropHandle;
import com.airdropmc.lang.LanguageManager;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.packages.PackageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CmdAirdropHelpTest {

	private static final String LEGACY_VERSION_TEMPLATE = """
			{text}
			Airdrop Version: {accent}{version}{text}
			Spigot API Version: {accent}{api_version}""";

	@TempDir
	Path tempDir;

	private ServerMock server;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		ChatHandler.init(null);
		PackageManager.clear();
		setStatic("ready", false);
		setStatic("pluginVersion", null);
		setStatic("paperCompatibilityVersion", null);
	}

	@AfterEach
	void tearDown() throws Exception {
		PackageManager.clear();
		ChatHandler.init(null);
		setStatic("ready", false);
		setStatic("pluginVersion", null);
		setStatic("paperCompatibilityVersion", null);
		MockBukkit.unmock();
	}

	@Test
	void rootHelpIsHandledBeforeReadinessAndShowsOnlyPlayerCommands() {
		PlayerMock player = server.addPlayer();

		boolean handled = new CmdAirdrop().onCommand(
				player, mock(Command.class), "airdrop", new String[0]);

		assertTrue(handled);
		String help = drainMessages(player);
		assertTrue(help.contains("/airdrop <package>"), help);
		assertTrue(help.contains("/airdrop package <name>"), help);
		assertTrue(help.contains("/airdrop version"), help);
		assertFalse(help.contains("still starting"), help);
		assertFalse(help.contains("/airdrop packages"), help);
		assertFalse(help.contains("/airdrop package create"), help);
		assertFalse(help.contains("/airdrop package delete"), help);
		assertFalse(help.contains("/airdrop reload"), help);
		assertFalse(help.contains("/airdrop status"), help);
	}

	@Test
	void malformedInputShowsPermissionAwareAdminHelpBeforeReadiness() {
		PlayerMock operator = server.addPlayer();
		operator.setOp(true);

		boolean handled = new CmdAirdrop().onCommand(
				operator, mock(Command.class), "airdrop", new String[]{"reload", "extra"});

		assertTrue(handled);
		String help = drainMessages(operator);
		assertTrue(help.contains("/airdrop <package>"), help);
		assertTrue(help.contains("/airdrop packages"), help);
		assertTrue(help.contains("/airdrop package create"), help);
		assertTrue(help.contains("/airdrop package delete"), help);
		assertTrue(help.contains("/airdrop reload"), help);
		assertTrue(help.contains("/airdrop status"), help);
		assertFalse(help.contains("still starting"), help);
	}

	@Test
	void missingPackagePointsPlayersToRootCompletionInsteadOfTheAdminBrowser() throws Exception {
		setStatic("ready", true);
		PlayerMock player = server.addPlayer();
		var world = server.addSimpleWorld("help_world");
		player.teleport(new Location(world, 0, 100, 0));
		DropRequestDescriptor descriptor = new DropRequestDescriptor(
				UUID.randomUUID(), DropSource.PLAYER, player.getUniqueId(), "missing",
				player.getLocation());
		DefaultDropHandle handle = new DefaultDropHandle(descriptor);
		handle.completeNotSpawned(new DropOutcome.Rejected(
				descriptor,
				Optional.empty(),
				DropRejection.of(DropRejectionReason.UNKNOWN_PACKAGE, "missing"),
				PaymentStatus.REJECTED));

		try (MockedStatic<DropController> controller = mockStatic(DropController.class)) {
			controller.when(() -> DropController.requestPlayerDrop(
					player, "missing", DropRequestOptions.defaults())).thenReturn(handle);
			assertTrue(new CmdAirdrop().onCommand(
					player, mock(Command.class), "airdrop", new String[]{"missing"}));
		}

		String message = nextMessage(player);
		assertTrue(message.contains("press Tab"), message);
		assertTrue(message.contains("/airdrop package <name>"), message);
		assertFalse(message.contains("/airdrop packages"), message);
	}

	@Test
	void versionOutputLabelsEveryCompatibilitySignalAndCanonicalDocs() throws Exception {
		setStatic("pluginVersion", "4.1.0-test");
		setStatic("paperCompatibilityVersion", "1.21.11");
		PlayerMock player = server.addPlayer();

		assertTrue(new CmdAirdrop().onCommand(
				player, mock(Command.class), "airdrop", new String[]{"version"}));

		String message = nextMessage(player);
		assertTrue(message.contains("Plugin: 4.1.0-test"), message);
		assertTrue(message.contains("Extension API: 1.0.0"), message);
		assertTrue(message.contains("Paper compatibility: 1.21.11"), message);
		assertTrue(message.contains("Java compatibility: 21"), message);
		assertTrue(message.contains("https://modrinth.com/plugin/airdrop"), message);
	}

	@Test
	void versionOutputUsesStableNamedPlaceholders() throws Exception {
		setStatic("pluginVersion", "4.1.0-test");
		setStatic("paperCompatibilityVersion", "1.21.11");
		LanguageManager language = mock(LanguageManager.class);
		when(language.get(eq(MessageKey.SYSTEM_VERSION_INFO), anyMap())).thenReturn("version details");
		ChatHandler.init(language);

		new CmdAirdrop().onCommand(
				server.addPlayer(), mock(Command.class), "airdrop", new String[]{"version"});

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, String>> placeholders = ArgumentCaptor.forClass(Map.class);
		verify(language).get(eq(MessageKey.SYSTEM_VERSION_INFO), placeholders.capture());
		assertEquals(Set.of(
				"version",
				"api_version",
				"plugin_version",
				"extension_api_version",
				"paper_version",
				"java_version",
				"docs_url"), placeholders.getValue().keySet());
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	void upgradingStockVersionTemplateShowsCurrentCompatibilityDetails(boolean writeMissingKeys) throws Exception {
		setStatic("pluginVersion", "4.1.0-test");
		setStatic("paperCompatibilityVersion", "1.21.11");
		LanguageManager language = loadVersionTemplate(LEGACY_VERSION_TEMPLATE, writeMissingKeys);
		PlayerMock player = server.addPlayer();

		new CmdAirdrop().onCommand(player, mock(Command.class), "airdrop", new String[]{"version"});

		String message = nextMessage(player);
		assertTrue(message.contains("Plugin: 4.1.0-test"), message);
		assertTrue(message.contains("Extension API: 1.0.0"), message);
		assertTrue(message.contains("Paper compatibility: 1.21.11"), message);
		assertTrue(message.contains("Java compatibility: 21"), message);
		assertTrue(message.contains("https://modrinth.com/plugin/airdrop"), message);
		Path languageFile = tempDir.resolve("lang/en.yml");
		String savedTemplate = YamlConfiguration.loadConfiguration(languageFile.toFile())
				.getString("system.version-info");
		assertEquals(writeMissingKeys ? MessageKey.SYSTEM_VERSION_INFO.getDefault() : LEGACY_VERSION_TEMPLATE,
				savedTemplate);
		String savedContents = Files.readString(languageFile);
		language.publishLanguage(language.prepareLanguage("en", writeMissingKeys));
		assertEquals(savedContents, Files.readString(languageFile));
	}

	@Test
	void customizedVersionTemplateKeepsItsTextAndResolvesLegacyAndCurrentPlaceholders() throws Exception {
		setStatic("pluginVersion", "4.1.0-test");
		setStatic("paperCompatibilityVersion", "1.21.11");
		String template = "Server build {version}; Paper {api_version}; API {extension_api_version}"
				+ "; Current {plugin_version}/{paper_version}";
		loadVersionTemplate(template, true);
		PlayerMock player = server.addPlayer();

		new CmdAirdrop().onCommand(player, mock(Command.class), "airdrop", new String[]{"version"});

		assertEquals("Server build 4.1.0-test; Paper 1.21.11; API 1.0.0; Current 4.1.0-test/1.21.11",
				nextMessage(player));
		assertEquals(template, YamlConfiguration.loadConfiguration(tempDir.resolve("lang/en.yml").toFile())
				.getString("system.version-info"));
	}

	@Test
	void aCorrectlyNamedPaperVersionGetterIsAvailable() throws Exception {
		Method getter = Airdrop.class.getMethod("getPaperApiVersion");

		assertEquals(String.class, getter.getReturnType());
		assertTrue(Airdrop.class.getMethod("getPluginApiVersion").isAnnotationPresent(Deprecated.class));
	}

	@Test
	void localizedHelpEntriesShipInTheEnglishResource() throws Exception {
		try (InputStream input = getClass().getClassLoader().getResourceAsStream("lang/en.yml")) {
			assertNotNull(input);
			YamlConfiguration language = YamlConfiguration.loadConfiguration(
					new InputStreamReader(input, StandardCharsets.UTF_8));
			for (String key : List.of(
					"commands.help.header",
					"commands.help.drop",
					"commands.help.package",
					"commands.help.version",
					"commands.help.admin-create",
					"commands.help.admin-delete",
					"commands.help.admin-packages",
					"commands.help.admin-reload",
					"commands.help.admin-status")) {
				assertFalse(language.getString(key, "").isBlank(), key);
			}
		}
	}

	private LanguageManager loadVersionTemplate(String template, boolean writeMissingKeys) throws Exception {
		Path languageFile = tempDir.resolve("lang/en.yml");
		Files.createDirectories(languageFile.getParent());
		YamlConfiguration existing = new YamlConfiguration();
		existing.set("system.version-info", template);
		existing.save(languageFile.toFile());
		Airdrop plugin = mock(Airdrop.class);
		when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
		when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
		when(plugin.getResource("lang/en.yml"))
				.thenAnswer(ignored -> getClass().getClassLoader().getResourceAsStream("lang/en.yml"));
		LanguageManager language = new LanguageManager(plugin);
		language.publishLanguage(language.prepareLanguage("en", writeMissingKeys));
		ChatHandler.init(language);
		return language;
	}

	private String drainMessages(PlayerMock player) {
		List<String> messages = new ArrayList<>();
		Component message;
		while ((message = player.nextComponentMessage()) != null) {
			messages.add(PlainTextComponentSerializer.plainText().serialize(message));
		}
		return String.join("\n", messages);
	}

	private String nextMessage(PlayerMock player) {
		Component message = player.nextComponentMessage();
		assertNotNull(message);
		return PlainTextComponentSerializer.plainText().serialize(message);
	}

	private void setStatic(String fieldName, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}
}
