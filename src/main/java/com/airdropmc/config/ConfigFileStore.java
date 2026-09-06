package com.airdropmc.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.YamlConstructor;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.io.IOException;
import java.io.StringReader;
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
		try {
			configuration.loadFromString(yaml);
		} catch (RuntimeException failure) {
			diagnosePackageItemFailure(yaml, failure);
			throw failure;
		}
		return configuration;
	}

	private static void diagnosePackageItemFailure(String yaml, RuntimeException originalFailure)
			throws InvalidConfigurationException {
		// Bukkit drops package/index context when a serialized YAML object cannot be constructed.
		// Inspect nodes only after a failed load; successful reads still use Bukkit's loader alone.
		try {
			YamlConstructor constructor = new YamlConstructor();
			Node root = new Yaml(constructor).compose(new StringReader(yaml));
			Node packages = childNode(root, "packages", constructor);
			if (!(packages instanceof MappingNode packageNodes)) {
				return;
			}
			constructor.flattenMapping(packageNodes);
			for (NodeTuple entry : packageNodes.getValue()) {
				if (!(entry.getKeyNode() instanceof ScalarNode name)) {
					continue;
				}
				Node items = childNode(entry.getValueNode(), "items", constructor);
				if (!(items instanceof SequenceNode itemNodes)) {
					continue;
				}
				for (int index = 0; index < itemNodes.getValue().size(); index++) {
					Node item = itemNodes.getValue().get(index);
					if (!(item instanceof MappingNode)) {
						continue;
					}
					try {
						constructor.construct(item);
					} catch (RuntimeException itemFailure) {
						throw new InvalidConfigurationException(
								"Package '" + name.getValue() + "' has invalid item at index " + index
										+ ": could not deserialize ItemStack", originalFailure);
					}
				}
			}
		} catch (RuntimeException diagnosticFailure) {
			originalFailure.addSuppressed(diagnosticFailure);
		}
	}

	private static Node childNode(Node parent, String key, YamlConstructor constructor) {
		if (parent instanceof MappingNode mapping) {
			constructor.flattenMapping(mapping);
			for (NodeTuple entry : mapping.getValue()) {
				if (entry.getKeyNode() instanceof ScalarNode scalar && key.equals(scalar.getValue())) {
					return entry.getValueNode();
				}
			}
		}
		return null;
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
