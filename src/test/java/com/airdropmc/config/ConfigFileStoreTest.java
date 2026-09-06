package com.airdropmc.config;

import be.seeseemelk.mockbukkit.MockBukkit;
import com.airdropmc.Config;
import com.airdropmc.PackagesConfig;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFileStoreTest {

	@TempDir
	Path tempDir;

	@Test
	void readParsesValidYamlStrictly() throws IOException, InvalidConfigurationException {
		Path source = tempDir.resolve("config.yml");
		Files.writeString(source, "drop:\n  height: 42\n", StandardCharsets.UTF_8);

		YamlConfiguration configuration = new ConfigFileStore().read(source);

		assertEquals(42, configuration.getInt("drop.height"));
	}

	@Test
	void readRejectsMalformedYamlWithoutChangingPublishedCandidate() throws IOException {
		Path source = tempDir.resolve("config.yml");
		Files.writeString(source, "drop: [unterminated\n", StandardCharsets.UTF_8);
		YamlConfiguration publishedCandidate = configuration("value", "old");
		Config published = new Config(publishedCandidate);

		assertThrows(InvalidConfigurationException.class, () -> new ConfigFileStore().read(source));

		assertSame(publishedCandidate, published.getConfig());
	}

	@Test
	void readReportsNonzeroIndexForIncompatibleNestedItemMetadata() throws Exception {
		assertMalformedSecondItemIsDiagnosed(false);
	}

	@Test
	void readReportsNonzeroIndexForNullSerializedMappingKey() throws Exception {
		assertMalformedSecondItemIsDiagnosed(true);
	}

	private void assertMalformedSecondItemIsDiagnosed(boolean nullMappingKey) throws Exception {
		MockBukkit.mock();
		try {
			ItemStack first = new ItemStack(Material.STONE, 3);
			ItemStack second = new ItemStack(Material.DIAMOND);
			Map<String, Object> firstItem = new LinkedHashMap<>(first.serialize());
			Map<String, Object> secondItem = new LinkedHashMap<>(second.serialize());
			// MockBukkit cannot deserialize ItemMeta.setVersion, even for plain stacks.
			firstItem.remove("meta");
			secondItem.remove("meta");
			firstItem.put("==", "org.bukkit.inventory.ItemStack");
			secondItem.put("==", "org.bukkit.inventory.ItemStack");
			YamlConfiguration candidate = new YamlConfiguration();
			candidate.set("packages.paid.price", 10.0);
			candidate.set("packages.paid.items", List.of(firstItem, secondItem));
			Path source = tempDir.resolve("packages.yml");
			Files.writeString(source, candidate.saveToString(), StandardCharsets.UTF_8);
			ConfigFileStore store = new ConfigFileStore();
			assertEquals(List.of(first, second), store.read(source).getList("packages.paid.items"));

			Map<String, Object> malformedItem = new LinkedHashMap<>(secondItem);
			if (nullMappingKey) {
				malformedItem.put(null, "invalid");
			} else {
				malformedItem.put("meta", Map.of("==", "incompatible.ItemMeta"));
			}
			candidate.set("packages.paid.items", List.of(firstItem, malformedItem));
			String rejectedYaml = candidate.saveToString();
			Files.writeString(source, rejectedYaml, StandardCharsets.UTF_8);

			InvalidConfigurationException failure = assertThrows(
					InvalidConfigurationException.class, () -> store.read(source));
			assertTrue(failure.getMessage().contains("Package 'paid'"), failure::toString);
			assertTrue(failure.getMessage().contains("index 1"), failure::toString);
			assertEquals(rejectedYaml, Files.readString(source, StandardCharsets.UTF_8));
		} finally {
			MockBukkit.unmock();
		}
	}

	@Test
	void readPropagatesInjectedIOExceptionAndMissingFile() {
		IOException failure = new IOException("simulated read failure");
		ConfigFileStore store = new ConfigFileStore(
				source -> {
					throw failure;
				},
				ConfigFileStoreTest::writeUtf8,
				ConfigFileStoreTest::replace);
		YamlConfiguration publishedCandidate = configuration("value", "old");
		Config published = new Config(publishedCandidate);

		IOException actual = assertThrows(IOException.class, () -> store.read(tempDir.resolve("config.yml")));

		assertSame(failure, actual);
		assertSame(publishedCandidate, published.getConfig());
		assertThrows(NoSuchFileException.class,
				() -> new ConfigFileStore().read(tempDir.resolve("missing.yml")));
	}

	@Test
	void writeAtomicallyReplacesTarget() throws IOException, InvalidConfigurationException {
		Path target = writeOldTarget();
		List<Boolean> replaceModes = new ArrayList<>();
		ConfigFileStore store = storeWith(
				ConfigFileStoreTest::writeUtf8,
				(temporary, destination, atomic) -> {
					replaceModes.add(atomic);
					replace(temporary, destination, atomic);
				});

		store.write(target, configuration("value", "new"));

		assertFalse(replaceModes.isEmpty());
		assertTrue(replaceModes.getFirst());
		assertEquals("new", new ConfigFileStore().read(target).getString("value"));
		assertOnlyTargetRemains(target);
	}

	@Test
	void writeCreatesMissingParent() throws IOException, InvalidConfigurationException {
		Path target = tempDir.resolve("missing").resolve("plugin").resolve("config.yml");
		assertFalse(Files.exists(target.getParent()));

		new ConfigFileStore().write(target, configuration("value", "new"));

		assertTrue(Files.isDirectory(target.getParent()));
		assertEquals("new", new ConfigFileStore().read(target).getString("value"));
		assertOnlyTargetRemains(target);
	}

	@Test
	void tempWriteFailurePreservesTargetAndCleansTemp() throws IOException {
		Path target = writeOldTarget();
		IOException failure = new IOException("simulated temp write failure");
		AtomicInteger replaceCalls = new AtomicInteger();
		ConfigFileStore store = storeWith(
				(temporary, yaml) -> {
					writeUtf8(temporary, "partial");
					throw failure;
				},
				(temporary, destination, atomic) -> replaceCalls.incrementAndGet());
		YamlConfiguration publishedCandidate = configuration("value", "old");
		Config published = new Config(publishedCandidate);

		IOException actual = assertThrows(IOException.class,
				() -> store.write(target, configuration("value", "new")));

		assertSame(failure, actual);
		assertEquals(0, replaceCalls.get());
		assertOldTargetUnchanged(target);
		assertOnlyTargetRemains(target);
		assertSame(publishedCandidate, published.getConfig());
	}

	@Test
	void atomicUnsupportedFallsBackOnce() throws IOException, InvalidConfigurationException {
		Path target = writeOldTarget();
		List<Boolean> replaceModes = new ArrayList<>();
		ConfigFileStore store = storeWith(
				ConfigFileStoreTest::writeUtf8,
				(temporary, destination, atomic) -> {
					replaceModes.add(atomic);
					if (atomic) {
						throw unsupportedAtomicMove(temporary, destination);
					}
					replace(temporary, destination, false);
				});

		store.write(target, configuration("value", "new"));

		assertEquals(List.of(true, false), replaceModes);
		assertEquals("new", new ConfigFileStore().read(target).getString("value"));
		assertOnlyTargetRemains(target);
	}

	@Test
	void genericAtomicMoveFailureDoesNotFallback() throws IOException {
		Path target = writeOldTarget();
		IOException failure = new IOException("simulated atomic move failure");
		List<Boolean> replaceModes = new ArrayList<>();
		ConfigFileStore store = storeWith(
				ConfigFileStoreTest::writeUtf8,
				(temporary, destination, atomic) -> {
					replaceModes.add(atomic);
					throw failure;
				});
		YamlConfiguration publishedCandidate = configuration("value", "old");
		Config published = new Config(publishedCandidate);

		IOException actual = assertThrows(IOException.class,
				() -> store.write(target, configuration("value", "new")));

		assertSame(failure, actual);
		assertEquals(List.of(true), replaceModes);
		assertOldTargetUnchanged(target);
		assertOnlyTargetRemains(target);
		assertSame(publishedCandidate, published.getConfig());
	}

	@Test
	void fallbackMoveFailurePreservesTarget() throws IOException {
		Path target = writeOldTarget();
		IOException fallbackFailure = new IOException("simulated fallback move failure");
		List<Boolean> replaceModes = new ArrayList<>();
		ConfigFileStore store = storeWith(
				ConfigFileStoreTest::writeUtf8,
				(temporary, destination, atomic) -> {
					replaceModes.add(atomic);
					if (atomic) {
						throw unsupportedAtomicMove(temporary, destination);
					}
					throw fallbackFailure;
				});
		YamlConfiguration publishedCandidate = configuration("value", "old");
		Config published = new Config(publishedCandidate);

		IOException actual = assertThrows(IOException.class,
				() -> store.write(target, configuration("value", "new")));

		assertSame(fallbackFailure, actual);
		assertEquals(List.of(true, false), replaceModes);
		assertOldTargetUnchanged(target);
		assertOnlyTargetRemains(target);
		assertSame(publishedCandidate, published.getConfig());
	}

	@Test
	void cleanupFailureIsSuppressed() throws IOException {
		Path target = writeOldTarget();
		IOException failure = new IOException("simulated temp write failure");
		ConfigFileStore store = storeWith(
				(temporary, yaml) -> {
					Files.delete(temporary);
					Files.createDirectory(temporary);
					writeUtf8(temporary.resolve("partial"), "partial");
					throw failure;
				},
				ConfigFileStoreTest::replace);
		YamlConfiguration publishedCandidate = configuration("value", "old");
		Config published = new Config(publishedCandidate);

		IOException actual = assertThrows(IOException.class,
				() -> store.write(target, configuration("value", "new")));

		assertSame(failure, actual);
		assertEquals(1, actual.getSuppressed().length);
		assertInstanceOf(DirectoryNotEmptyException.class, actual.getSuppressed()[0]);
		assertOldTargetUnchanged(target);
		assertSame(publishedCandidate, published.getConfig());
		try (Stream<Path> siblings = Files.list(target.getParent())) {
			List<Path> remaining = siblings.filter(path -> !path.equals(target)).toList();
			assertEquals(1, remaining.size());
			assertTrue(Files.isDirectory(remaining.getFirst()));
		}
	}

	@Test
	void runtimeFailureAfterTempCreationAlsoCleans() throws IOException {
		Path target = writeOldTarget();
		RuntimeException failure = new IllegalStateException("simulated runtime write failure");
		ConfigFileStore store = storeWith(
				(temporary, yaml) -> {
					writeUtf8(temporary, "partial");
					throw failure;
				},
				ConfigFileStoreTest::replace);
		YamlConfiguration publishedCandidate = configuration("value", "old");
		Config published = new Config(publishedCandidate);

		RuntimeException actual = assertThrows(RuntimeException.class,
				() -> store.write(target, configuration("value", "new")));

		assertSame(failure, actual);
		assertOldTargetUnchanged(target);
		assertOnlyTargetRemains(target);
		assertSame(publishedCandidate, published.getConfig());
	}

	@Test
	void wrappersHoldOnlyExplicitCandidates() throws IOException {
		YamlConfiguration mainCandidate = configuration("language", "en");
		YamlConfiguration packagesCandidate = configuration("packages.starter.price", 10.0);

		Config config = new Config(mainCandidate);
		PackagesConfig packagesConfig = new PackagesConfig(packagesCandidate);

		assertSame(mainCandidate, config.getConfig());
		assertSame(packagesCandidate, packagesConfig.getConfig());
		assertSame(mainCandidate, config.getConfig());
		assertSame(packagesCandidate, packagesConfig.getConfig());
		try (Stream<Path> entries = Files.list(tempDir)) {
			assertEquals(0, entries.count());
		}
	}

	private ConfigFileStore storeWith(ConfigFileStore.TempWriter writer, ConfigFileStore.Replacer replacer) {
		return new ConfigFileStore(ConfigFileStoreTest::readUtf8, writer, replacer);
	}

	private Path writeOldTarget() throws IOException {
		Path target = tempDir.resolve("config.yml");
		writeUtf8(target, "value: old\n");
		return target;
	}

	private void assertOldTargetUnchanged(Path target) throws IOException {
		assertEquals("value: old\n", readUtf8(target));
	}

	private void assertOnlyTargetRemains(Path target) throws IOException {
		try (Stream<Path> siblings = Files.list(target.getParent())) {
			assertEquals(Set.of(target), siblings.collect(Collectors.toSet()));
		}
	}

	private static YamlConfiguration configuration(String path, Object value) {
		YamlConfiguration configuration = new YamlConfiguration();
		configuration.set(path, value);
		return configuration;
	}

	private static String readUtf8(Path source) throws IOException {
		return Files.readString(source, StandardCharsets.UTF_8);
	}

	private static void writeUtf8(Path target, String contents) throws IOException {
		Files.writeString(target, contents, StandardCharsets.UTF_8);
	}

	private static void replace(Path temporary, Path target, boolean atomic) throws IOException {
		if (atomic) {
			Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING);
		} else {
			Files.move(temporary, target, REPLACE_EXISTING);
		}
	}

	private static AtomicMoveNotSupportedException unsupportedAtomicMove(Path source, Path target) {
		return new AtomicMoveNotSupportedException(source.toString(), target.toString(), "simulated unsupported move");
	}
}
