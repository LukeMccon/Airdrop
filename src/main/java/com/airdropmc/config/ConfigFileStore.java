package com.airdropmc.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Strict YAML file persistence used by the serialized configuration coordinator.
 */
final class ConfigFileStore {

	private final TextReader reader;
	private final TempWriter tempWriter;
	private final Replacer replacer;

	ConfigFileStore() {
		this(
				source -> Files.readString(source, StandardCharsets.UTF_8),
				(temporary, yaml) -> Files.writeString(temporary, yaml, StandardCharsets.UTF_8),
				(temporary, target, atomic) -> {
					if (atomic) {
						Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING);
					} else {
						Files.move(temporary, target, REPLACE_EXISTING);
					}
				});
	}

	ConfigFileStore(TextReader reader, TempWriter tempWriter, Replacer replacer) {
		this.reader = Objects.requireNonNull(reader, "reader");
		this.tempWriter = Objects.requireNonNull(tempWriter, "tempWriter");
		this.replacer = Objects.requireNonNull(replacer, "replacer");
	}

	YamlConfiguration read(Path source) throws IOException, InvalidConfigurationException {
		String yaml = reader.read(Objects.requireNonNull(source, "source"));
		YamlConfiguration configuration = new YamlConfiguration();
		configuration.loadFromString(yaml);
		return configuration;
	}

	void write(Path target, FileConfiguration candidate) throws IOException {
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(candidate, "candidate");

		String yaml = candidate.saveToString();
		Path absoluteTarget = target.toAbsolutePath();
		Path parent = absoluteTarget.getParent();
		if (parent == null) {
			throw new IllegalArgumentException("Configuration target must have a parent directory");
		}

		Path temporary = null;
		try {
			Files.createDirectories(parent);
			temporary = Files.createTempFile(parent, absoluteTarget.getFileName() + ".", ".tmp");
			tempWriter.write(temporary, yaml);
			try {
				replacer.replace(temporary, absoluteTarget, true);
			} catch (AtomicMoveNotSupportedException ignored) {
				replacer.replace(temporary, absoluteTarget, false);
			}
		} catch (IOException | RuntimeException failure) {
			cleanTemporaryFile(temporary, failure);
			throw failure;
		}
	}

	private void cleanTemporaryFile(Path temporary, Throwable failure) {
		if (temporary == null) {
			return;
		}
		try {
			Files.deleteIfExists(temporary);
		} catch (IOException | RuntimeException cleanupFailure) {
			if (cleanupFailure != failure) {
				failure.addSuppressed(cleanupFailure);
			}
		}
	}

	@FunctionalInterface
	interface TextReader {
		String read(Path source) throws IOException;
	}

	@FunctionalInterface
	interface TempWriter {
		void write(Path temporary, String yaml) throws IOException;
	}

	@FunctionalInterface
	interface Replacer {
		void replace(Path temporary, Path target, boolean atomic) throws IOException;
	}
}
