package com.airdropmc.config;

import com.airdropmc.Airdrop;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractConfigPersistenceTest {

	@TempDir
	Path tempDir;
	private Logger logger;
	private CapturingHandler logHandler;

	@BeforeEach
	void setUpLogger() {
		logger = Logger.getAnonymousLogger();
		logger.setLevel(Level.ALL);
		logger.setUseParentHandlers(false);
		logHandler = new CapturingHandler();
		logHandler.setLevel(Level.ALL);
		logger.addHandler(logHandler);
	}

	@AfterEach
	void tearDownLogger() {
		logger.removeHandler(logHandler);
		logHandler.close();
	}

	@Test
	void saveConfigReplacesTargetAndPublishesCandidate() throws IOException {
		Path target = tempDir.resolve("test.yml");
		Files.writeString(target, "value: old\n");
		TestConfig config = createConfig();
		YamlConfiguration candidate = new YamlConfiguration();
		candidate.set("value", "new");

		assertTrue(config.saveConfig(candidate));

		assertEquals("new", YamlConfiguration.loadConfiguration(target.toFile()).getString("value"));
		assertSame(candidate, config.getConfig());
		assertOnlyTargetRemains(target);
	}

	@Test
	void saveConfigCreatesMissingDataDirectory() throws IOException {
		Path dataFolder = tempDir.resolve("missing").resolve("plugin");
		Path target = dataFolder.resolve("test.yml");
		TestConfig config = createConfig(dataFolder);
		YamlConfiguration candidate = new YamlConfiguration();
		candidate.set("value", "new");
		assertFalse(Files.exists(dataFolder));

		assertTrue(config.saveConfig(candidate));

		assertTrue(Files.isDirectory(dataFolder));
		assertEquals("new", YamlConfiguration.loadConfiguration(target.toFile()).getString("value"));
		assertSame(candidate, config.getConfig());
		assertOnlyTargetRemains(target);
	}

	@Test
	void failedSavePreservesTargetAndLiveConfig() throws IOException {
		Path target = tempDir.resolve("test.yml");
		String originalContents = "value: old\n";
		Files.writeString(target, originalContents);
		TestConfig config = createConfig();
		FileConfiguration originalConfig = config.getConfig();
		IOException failure = new IOException("simulated save failure");
		FailingFileConfiguration failedCandidate = new FailingFileConfiguration(failure);

		assertFalse(config.saveConfig(failedCandidate));

		assertFalse(target.equals(failedCandidate.attemptedPath));
		assertEquals(target.getParent(), failedCandidate.attemptedPath.getParent());
		assertSame(originalConfig, config.getConfig());
		assertEquals(originalContents, Files.readString(target));
		assertOnlyTargetRemains(target);
		assertSingleSevereLog(failure);
	}

	@Test
	void runtimeSaveFailurePreservesTargetAndLiveConfig() throws IOException {
		Path target = tempDir.resolve("test.yml");
		String originalContents = "value: old\n";
		Files.writeString(target, originalContents);
		TestConfig config = createConfig();
		FileConfiguration originalConfig = config.getConfig();
		RuntimeException failure = new IllegalStateException("simulated runtime save failure");
		FileConfiguration failedCandidate = new RuntimeFailingFileConfiguration(failure);

		assertFalse(config.saveConfig(failedCandidate));

		assertSame(originalConfig, config.getConfig());
		assertEquals(originalContents, Files.readString(target));
		assertOnlyTargetRemains(target);
		assertSingleSevereLog(failure);
	}

	private TestConfig createConfig() {
		return createConfig(tempDir);
	}

	private TestConfig createConfig(Path dataFolder) {
		Airdrop plugin = mock(Airdrop.class);
		when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
		when(plugin.getLogger()).thenReturn(logger);
		return new TestConfig(plugin);
	}

	private void assertOnlyTargetRemains(Path target) throws IOException {
		try (Stream<Path> siblings = Files.list(target.getParent())) {
			assertEquals(Set.of(target), siblings.collect(Collectors.toSet()));
		}
	}

	private void assertSingleSevereLog(Throwable failure) {
		assertEquals(1, logHandler.records.size());
		LogRecord record = logHandler.records.getFirst();
		assertEquals(Level.SEVERE, record.getLevel());
		assertTrue(record.getMessage().contains("test.yml"));
		assertSame(failure, record.getThrown());
	}

	private static final class TestConfig extends AbstractConfig {

		private TestConfig(Airdrop plugin) {
			super(plugin, "test.yml");
		}

		@Override
		protected void onCreateDefaultConfig() {
			// Test configs are persisted explicitly.
		}
	}

	private static final class FailingFileConfiguration extends YamlConfiguration {

		private final IOException failure;
		private Path attemptedPath;

		private FailingFileConfiguration(IOException failure) {
			this.failure = failure;
		}

		@Override
		public void save(File file) throws IOException {
			attemptedPath = file.toPath();
			Files.writeString(attemptedPath, "partial");
			throw failure;
		}
	}

	private static final class RuntimeFailingFileConfiguration extends YamlConfiguration {

		private final RuntimeException failure;

		private RuntimeFailingFileConfiguration(RuntimeException failure) {
			this.failure = failure;
		}

		@Override
		public void save(File file) {
			throw failure;
		}
	}

	private static final class CapturingHandler extends Handler {

		private final List<LogRecord> records = new ArrayList<>();

		@Override
		public void publish(LogRecord record) {
			records.add(record);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}
	}
}
