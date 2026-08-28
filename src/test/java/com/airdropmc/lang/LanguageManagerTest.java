package com.airdropmc.lang;

import com.airdropmc.Airdrop;
import com.airdropmc.helpers.ChatTheme;
import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LanguageManagerTest {

	@TempDir
	Path tempDir;

	@Test
	void preparationCopiesResourceMergesAndWritesDefaultsBeforePublication() throws Exception {
		Path languageFile = languageFile("en");
		Files.createDirectories(languageFile.getParent());
		Files.writeString(languageFile, "commands:\n  player-only: Custom message\n", StandardCharsets.UTF_8);
		LanguageManager manager = managerWithResources(Map.of(
				"lang/en.yml", "prefix: Resource prefix\ncommands:\n  player-only: Resource message\n"));

		LanguageManager.LanguageCandidate candidate = manager.prepareLanguage("en");

		assertNull(manager.getCurrentLanguage());
		assertEquals(MessageKey.COMMANDS_PLAYER_ONLY.getDefault(), manager.get(MessageKey.COMMANDS_PLAYER_ONLY));
		YamlConfiguration written = strictLoad(languageFile);
		assertEquals("Resource prefix", written.getString("prefix"));
		assertEquals("Custom message", written.getString("commands.player-only"));

		manager.publishLanguage(candidate);

		assertEquals("en", manager.getCurrentLanguage());
		assertEquals("Custom message", manager.get(MessageKey.COMMANDS_PLAYER_ONLY));
	}

	@Test
	void missingKeyWriteBackCanBeDisabledWhileDefaultsRemainAvailable() throws Exception {
		Path languageFile = languageFile("en");
		Files.createDirectories(languageFile.getParent());
		String original = "prefix: Custom prefix\n";
		Files.writeString(languageFile, original, StandardCharsets.UTF_8);
		LanguageManager manager = managerWithResources(Map.of(
				"lang/en.yml", "prefix: Resource prefix\ncommands:\n  player-only: Resource message\n"));

		LanguageManager.LanguageCandidate candidate = manager.prepareLanguage("en", false);

		assertEquals(original, Files.readString(languageFile, StandardCharsets.UTF_8));
		manager.publishLanguage(candidate);
		assertEquals("Resource message", manager.get(MessageKey.COMMANDS_PLAYER_ONLY));
	}

	@Test
	void malformedYamlLeavesPublishedLanguageUntouched() throws Exception {
		Path languageFile = languageFile("en");
		Files.createDirectories(languageFile.getParent());
		Files.writeString(languageFile, "commands:\n  player-only: Last known good\n", StandardCharsets.UTF_8);
		LanguageManager manager = managerWithResources(Map.of());
		manager.publishLanguage(manager.prepareLanguage("en"));

		Files.writeString(languageFile, "commands:\n  player-only: [unterminated\n", StandardCharsets.UTF_8);

		assertThrows(InvalidConfigurationException.class, () -> manager.prepareLanguage("en"));
		assertEquals("en", manager.getCurrentLanguage());
		assertEquals("Last known good", manager.get(MessageKey.COMMANDS_PLAYER_ONLY));
	}

	@Test
	void strictUtf8ReadFailureLeavesPublishedLanguageUntouched() throws Exception {
		Path languageFile = languageFile("en");
		Files.createDirectories(languageFile.getParent());
		Files.writeString(languageFile, "commands:\n  player-only: Last known good\n", StandardCharsets.UTF_8);
		LanguageManager manager = managerWithResources(Map.of());
		manager.publishLanguage(manager.prepareLanguage("en"));

		Files.write(languageFile, new byte[] { 'p', 'r', 'e', 'f', 'i', 'x', ':', ' ', (byte) 0xC3, 0x28 });

		assertThrows(IOException.class, () -> manager.prepareLanguage("en"));
		assertEquals("en", manager.getCurrentLanguage());
		assertEquals("Last known good", manager.get(MessageKey.COMMANDS_PLAYER_ONLY));
	}

	@Test
	void resourceCopyFailureLeavesPublishedLanguageUntouched() throws Exception {
		Path languageFile = languageFile("en");
		Files.createDirectories(languageFile.getParent());
		Files.writeString(languageFile, "commands:\n  player-only: Last known good\n", StandardCharsets.UTF_8);
		FailingLanguageManager manager = failingManagerWithResources(Map.of(
				"lang/en.yml", "prefix: Resource prefix\ncommands:\n  player-only: Resource message\n"));
		manager.publishLanguage(manager.prepareLanguage("en", false));
		Files.delete(languageFile);
		manager.failWrites = true;

		IOException failure = assertThrows(IOException.class, () -> manager.prepareLanguage("en"));

		assertEquals("simulated write failure", failure.getMessage());
		assertEquals("en", manager.getCurrentLanguage());
		assertEquals("Last known good", manager.get(MessageKey.COMMANDS_PLAYER_ONLY));
		assertFalse(Files.exists(languageFile));
	}

	@Test
	void missingKeyWriteFailureLeavesPublishedLanguageUntouched() throws Exception {
		Path languageFile = languageFile("en");
		Files.createDirectories(languageFile.getParent());
		Files.writeString(languageFile, "commands:\n  player-only: Last known good\n", StandardCharsets.UTF_8);
		FailingLanguageManager manager = failingManagerWithResources(Map.of(
				"lang/en.yml", "prefix: Resource prefix\ncommands:\n  player-only: Resource message\n"));
		manager.publishLanguage(manager.prepareLanguage("en", false));
		manager.failWrites = true;

		IOException failure = assertThrows(IOException.class, () -> manager.prepareLanguage("en", true));

		assertEquals("simulated write failure", failure.getMessage());
		assertEquals("en", manager.getCurrentLanguage());
		assertEquals("Last known good", manager.get(MessageKey.COMMANDS_PLAYER_ONLY));
		assertNull(strictLoad(languageFile).getString("prefix"));
	}

	@Test
	void invalidCodeStillNormalizesToEnglishWithoutPublishingEarly() throws Exception {
		LanguageManager manager = managerWithResources(Map.of(
				"lang/en.yml", "commands:\n  player-only: Crème brûlée\n"));

		LanguageManager.LanguageCandidate candidate = manager.prepareLanguage("../../EN");

		assertEquals("en", candidate.languageCode());
		assertNull(manager.getCurrentLanguage());
		assertTrue(Files.exists(languageFile("en")));
		assertFalse(Files.exists(tempDir.resolve("EN.yml")));
		manager.publishLanguage(candidate);
		assertEquals("Crème brûlée", manager.get(MessageKey.COMMANDS_PLAYER_ONLY));
	}

	@Test
	void candidateControlItemNamesComeFromDetachedConfiguration() throws Exception {
		Path languageFile = languageFile("en");
		Files.createDirectories(languageFile.getParent());
		Files.writeString(languageFile,
				"gui:\n"
						+ "  save: Candidate Save\n"
						+ "  cancel: '&cCandidate Cancel'\n"
						+ "  back: '{accent}Candidate Back'\n"
						+ "  help: ''\n",
				StandardCharsets.UTF_8);
		LanguageManager manager = managerWithResources(Map.of());

		LanguageManager.LanguageCandidate candidate = manager.prepareLanguage("en");

		assertEquals(Set.of(
				"Candidate Save",
				ChatColor.RED + "Candidate Cancel",
				ChatTheme.accent() + "Candidate Back",
				MessageKey.GUI_HELP.getDefault()), candidate.controlItemNames());
		assertEquals(MessageKey.GUI_SAVE.getDefault(), manager.get(MessageKey.GUI_SAVE));
		assertNull(manager.getCurrentLanguage());
	}

	@Test
	void publicationKeepsPlaceholderThemeAndColorFormattingUnchanged() throws Exception {
		Path languageFile = languageFile("en");
		Files.createDirectories(languageFile.getParent());
		Files.writeString(languageFile,
				"commands:\n  player-only: \"{primary}Hello {name} &lthere\"\n",
				StandardCharsets.UTF_8);
		LanguageManager manager = managerWithResources(Map.of());

		manager.publishLanguage(manager.prepareLanguage("en"));

		assertEquals(ChatTheme.primary() + "Hello Luke " + ChatColor.BOLD + "there",
				manager.get(MessageKey.COMMANDS_PLAYER_ONLY, Map.of("name", "Luke")));
	}

	private LanguageManager managerWithResources(Map<String, String> resources) {
		return new LanguageManager(pluginWithResources(resources));
	}

	private FailingLanguageManager failingManagerWithResources(Map<String, String> resources) {
		return new FailingLanguageManager(pluginWithResources(resources));
	}

	private Airdrop pluginWithResources(Map<String, String> textResources) {
		Map<String, byte[]> resources = new HashMap<>();
		textResources.forEach((name, contents) ->
				resources.put(name, contents.getBytes(StandardCharsets.UTF_8)));
		Airdrop plugin = mock(Airdrop.class);
		when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
		when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
		when(plugin.getResource(anyString())).thenAnswer(invocation -> {
			byte[] contents = resources.get(invocation.getArgument(0, String.class));
			return contents == null ? null : new ByteArrayInputStream(contents);
		});
		return plugin;
	}

	private Path languageFile(String languageCode) {
		return tempDir.resolve("lang").resolve(languageCode + ".yml");
	}

	private static YamlConfiguration strictLoad(Path file) throws IOException, InvalidConfigurationException {
		YamlConfiguration configuration = new YamlConfiguration();
		try (InputStream stream = Files.newInputStream(file)) {
			configuration.load(new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
		}
		return configuration;
	}

	private static final class FailingLanguageManager extends LanguageManager {
		private boolean failWrites;

		private FailingLanguageManager(Airdrop plugin) {
			super(plugin);
		}

		@Override
		void writeAtomically(Path target, byte[] contents, boolean replaceExisting) throws IOException {
			if (failWrites) {
				throw new IOException("simulated write failure");
			}
			super.writeAtomically(target, contents, replaceExisting);
		}
	}
}
