package com.airdropmc.lang;

import com.airdropmc.Airdrop;
import com.airdropmc.helpers.ChatTheme;
import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.logging.Level;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class LanguageManager {
	private static final String DEFAULT_LANGUAGE = "en";
	private static final Pattern SAFE_LANGUAGE_CODE_PATTERN = Pattern.compile("^[a-z]{2}(?:-[A-Z]{2})?$");

	private final Airdrop plugin;
	private volatile YamlConfiguration langConfig;
	private volatile String currentLanguage;
	private volatile Set<String> publishedControlItemNames;

	public LanguageManager(Airdrop plugin) {
		this.plugin = plugin;
		publishedControlItemNames = controlItemNames(null);
	}

	/**
	 * Compatibility wrapper for callers that still perform language loading in one step.
	 * Transactional reloads should prepare on their configuration worker and publish only
	 * after all configuration candidates have succeeded.
	 */
	public void loadLanguage(String langCode) {
		try {
			publishLanguage(prepareLanguage(langCode));
		} catch (IOException | InvalidConfigurationException failure) {
			plugin.getLogger().log(Level.WARNING, "Failed to load language", failure);
		}
	}

	/**
	 * Performs all language file I/O and parsing without changing the live language state.
	 */
	public LanguageCandidate prepareLanguage(String langCode)
			throws IOException, InvalidConfigurationException {
		return prepareLanguage(langCode, true);
	}

	/**
	 * Performs all language file I/O and parsing without changing the live language state.
	 * When {@code writeMissingKeys} is true, resource defaults missing from the configured
	 * file are persisted before this method returns.
	 */
	public LanguageCandidate prepareLanguage(String langCode, boolean writeMissingKeys)
			throws IOException, InvalidConfigurationException {
		String safeLangCode = normalizeLanguageCode(langCode);
		String fileName = safeLangCode + ".yml";
		Path langFolder = plugin.getDataFolder().toPath().resolve("lang");
		Path langFile = langFolder.resolve(fileName);
		byte[] defaultResource = readResource("lang/" + fileName);

		YamlConfiguration defaultConfig = defaultResource == null
				? null
				: parseConfiguration(defaultResource);

		Files.createDirectories(langFolder);
		boolean languageFileExists = pathExists(langFile);
		if (!languageFileExists && defaultResource != null) {
			writeAtomically(langFile, defaultResource, false);
			languageFileExists = true;
		}

		YamlConfiguration candidateConfig = languageFileExists
				? parseConfiguration(langFile)
				: new YamlConfiguration();

		if (defaultConfig != null) {
			candidateConfig.setDefaults(defaultConfig);
			boolean updated = mergeMissingDefaults(candidateConfig, defaultConfig);
			if (updated && writeMissingKeys) {
				writeAtomically(langFile,
						candidateConfig.saveToString().getBytes(StandardCharsets.UTF_8), true);
			}
		}

		return new LanguageCandidate(
				safeLangCode,
				candidateConfig,
				controlItemNames(candidateConfig));
	}

	/**
	 * Publishes an already prepared language candidate. This method intentionally performs
	 * no parsing, resource access, or file I/O.
	 */
	public void publishLanguage(LanguageCandidate candidate) {
		currentLanguage = candidate.languageCode;
		langConfig = candidate.configuration;
		publishedControlItemNames = candidate.controlItemNames;
	}

	public Set<String> getControlItemNames() {
		return publishedControlItemNames;
	}

	public void reload() {
		if (currentLanguage == null || currentLanguage.isBlank()) {
			loadLanguage(DEFAULT_LANGUAGE);
			return;
		}
		loadLanguage(currentLanguage);
	}

	private byte[] readResource(String resourcePath) throws IOException {
		try (InputStream resource = plugin.getResource(resourcePath)) {
			return resource == null ? null : resource.readAllBytes();
		}
	}

	private boolean pathExists(Path path) throws IOException {
		try {
			Files.readAttributes(path, BasicFileAttributes.class);
			return true;
		} catch (NoSuchFileException missing) {
			return false;
		}
	}

	private YamlConfiguration parseConfiguration(Path path)
			throws IOException, InvalidConfigurationException {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return parseConfiguration(reader);
		}
	}

	private YamlConfiguration parseConfiguration(byte[] contents)
			throws IOException, InvalidConfigurationException {
		try (Reader reader = new InputStreamReader(
				new ByteArrayInputStream(contents), StandardCharsets.UTF_8.newDecoder())) {
			return parseConfiguration(reader);
		}
	}

	private YamlConfiguration parseConfiguration(Reader reader)
			throws IOException, InvalidConfigurationException {
		YamlConfiguration configuration = new YamlConfiguration();
		configuration.load(reader);
		return configuration;
	}

	private boolean mergeMissingDefaults(YamlConfiguration configuration, YamlConfiguration defaults) {
		boolean updated = false;
		for (String key : defaults.getKeys(true)) {
			if (!configuration.isSet(key)) {
				configuration.set(key, defaults.get(key));
				updated = true;
			}
		}
		return updated;
	}

	void writeAtomically(Path target, byte[] contents, boolean replaceExisting) throws IOException {
		Path temporaryFile = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".tmp");
		try {
			Files.write(temporaryFile, contents);
			try {
				if (replaceExisting) {
					Files.move(temporaryFile, target, ATOMIC_MOVE, REPLACE_EXISTING);
				} else {
					Files.move(temporaryFile, target, ATOMIC_MOVE);
				}
			} catch (AtomicMoveNotSupportedException unsupported) {
				if (replaceExisting) {
					Files.move(temporaryFile, target, REPLACE_EXISTING);
				} else {
					Files.move(temporaryFile, target);
				}
			}
		} finally {
			Files.deleteIfExists(temporaryFile);
		}
	}

	private String normalizeLanguageCode(String langCode) {
		if (langCode == null || langCode.isBlank()) {
			return DEFAULT_LANGUAGE;
		}
		String candidate = langCode.trim();
		if (!SAFE_LANGUAGE_CODE_PATTERN.matcher(candidate).matches()) {
			plugin.getLogger().warning(
					"Invalid language code '" + candidate + "'. Falling back to '" + DEFAULT_LANGUAGE + "'.");
			return DEFAULT_LANGUAGE;
		}
		return candidate;
	}

	public String get(MessageKey key) {
		return format(getRaw(key), null);
	}

	public String get(MessageKey key, Map<String, String> placeholders) {
		return format(getRaw(key), placeholders);
	}

	private String getRaw(MessageKey key) {
		return getRaw(langConfig, key);
	}

	private String getRaw(YamlConfiguration configuration, MessageKey key) {
		if (configuration == null) {
			return key.getDefault();
		}
		String message = configuration.getString(key.getKey());
		if (message == null || message.isBlank()) {
			return key.getDefault();
		}
		return message;
	}

	private Set<String> controlItemNames(YamlConfiguration configuration) {
		Set<String> names = new LinkedHashSet<>();
		for (MessageKey key : new MessageKey[] {
				MessageKey.GUI_SAVE,
				MessageKey.GUI_CANCEL,
				MessageKey.GUI_BACK,
				MessageKey.GUI_HELP }) {
			names.add(format(getRaw(configuration, key), null));
		}
		return Set.copyOf(names);
	}

	private String format(String message, Map<String, String> placeholders) {
		if (message == null) {
			return "";
		}
		String formatted = replacePlaceholders(message, placeholders);
		formatted = replaceThemePlaceholders(formatted);
		return ChatColor.translateAlternateColorCodes('&', formatted);
	}

	private String replacePlaceholders(String message, Map<String, String> placeholders) {
		if (placeholders == null || placeholders.isEmpty()) {
			return message;
		}
		String formatted = message;
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			if (entry.getValue() == null) {
				continue;
			}
			formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return formatted;
	}

	private String replaceThemePlaceholders(String message) {
		Map<String, String> theme = new HashMap<>();
		theme.put("primary", ChatTheme.primary().toString());
		theme.put("text", ChatTheme.text().toString());
		theme.put("accent", ChatTheme.accent().toString());
		theme.put("success", ChatTheme.success().toString());
		theme.put("warning", ChatTheme.warning().toString());
		theme.put("error", ChatTheme.error().toString());
		theme.put("error-detail", ChatTheme.errorDetail().toString());

		String formatted = message;
		for (Map.Entry<String, String> entry : theme.entrySet()) {
			formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return formatted;
	}

	public String getCurrentLanguage() {
		return currentLanguage;
	}

	public static final class LanguageCandidate {
		private final String languageCode;
		private final YamlConfiguration configuration;
		private final Set<String> controlItemNames;

		private LanguageCandidate(
				String languageCode,
				YamlConfiguration configuration,
				Set<String> controlItemNames) {
			this.languageCode = languageCode;
			this.configuration = configuration;
			this.controlItemNames = Set.copyOf(controlItemNames);
		}

		public String languageCode() {
			return languageCode;
		}

		public Set<String> controlItemNames() {
			return controlItemNames;
		}
	}
}
