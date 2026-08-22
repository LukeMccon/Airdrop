package com.airdropmc.config;

import com.airdropmc.Airdrop;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
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
	void failedSavePreservesTargetAndLiveConfig() throws IOException {
		Path target = tempDir.resolve("test.yml");
		String originalContents = "value: old\n";
		Files.writeString(target, originalContents);
		TestConfig config = createConfig();
		FileConfiguration originalConfig = config.getConfig();
		FileConfiguration failedCandidate = new FailingFileConfiguration();

		assertFalse(config.saveConfig(failedCandidate));

		assertSame(originalConfig, config.getConfig());
		assertEquals(originalContents, Files.readString(target));
		assertOnlyTargetRemains(target);
	}

	private TestConfig createConfig() {
		Airdrop plugin = mock(Airdrop.class);
		when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
		when(plugin.getLogger()).thenReturn(Logger.getLogger(AbstractConfigPersistenceTest.class.getName()));
		return new TestConfig(plugin);
	}

	private void assertOnlyTargetRemains(Path target) throws IOException {
		try (Stream<Path> siblings = Files.list(tempDir)) {
			assertEquals(Set.of(target), siblings.collect(Collectors.toSet()));
		}
	}

	private static final class TestConfig extends AbstractConfig {

		private TestConfig(Airdrop plugin) {
			super(plugin, "test.yml");
		}

		@Override
		protected void onCreateDefaultConfig() {
			throw new AssertionError("Existing test config should not be recreated");
		}
	}

	private static final class FailingFileConfiguration extends YamlConfiguration {

		@Override
		public void save(File file) throws IOException {
			throw new IOException("simulated save failure");
		}
	}
}
