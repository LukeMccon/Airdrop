package com.airdropmc.build;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilityMatrixTest {
	@Test
	void generatedPluginDescriptorTargetsExactSupportedPaperVersion() {
		Map<String, Object> plugin = loadPluginDescription();

		assertEquals("1.21.11", String.valueOf(plugin.get("api-version")));
	}

	@Test
	void readmePublishesTheExactAutomatedCompatibilityMatrix() throws IOException {
		String readme = Files.readString(Path.of("README.md"));
		String sourceVersion = System.getProperty("airdrop.sourceDevelopmentVersion");
		String extensionApiVersion = System.getProperty("airdrop.extensionApiVersion");

		assertNotNull(sourceVersion, "Gradle must expose the source development version to tests");
		assertNotNull(extensionApiVersion, "Gradle must expose the extension API version to tests");
		assertEquals("4.1.0-SNAPSHOT", sourceVersion);
		assertEquals("1.0.0", extensionApiVersion);
		assertTrue(
			readme.contains(
				"| Current source (`" + sourceVersion + "`) | `" + extensionApiVersion
						+ "` | `1.21.11` | `21` | unit + LightKeeper |"
			),
			"README must describe the exact compatibility matrix for the current source"
		);
		assertFalse(readme.contains("1.21.11+"), "Paper compatibility claims must be bounded");
	}

	@Test
	void buildExposesDependencyMatrixVerificationTaskThroughCheck() throws IOException {
		String build = Files.readString(Path.of("build.gradle.kts"));

		assertTrue(
			build.contains("tasks.register(\"verifyDependencyMatrix\")"),
			"Build must define the rerunnable dependency-matrix verification task"
		);
		assertTrue(
			build.contains("dependsOn(verifyDependencyMatrix)"),
			"The standard check lifecycle must run dependency-matrix verification"
		);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> loadPluginDescription() {
		InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
		assertNotNull(stream, "Generated plugin.yml should be available on the test runtime classpath");
		return new Yaml().load(stream);
	}
}
