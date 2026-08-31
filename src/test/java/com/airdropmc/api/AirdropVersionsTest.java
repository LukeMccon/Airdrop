package com.airdropmc.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AirdropVersionsTest {

	@Test
	void generatedMetadataPublishesEveryVersionSignalFromTheBuild() throws IOException {
		Properties metadata = loadMetadata();

		assertEquals("5.0.0-SNAPSHOT", metadata.getProperty("plugin-version"));
		assertEquals("1.0.0", metadata.getProperty("extension-api-version"));
		assertEquals("1.21.11", metadata.getProperty("paper-compatibility-version"));
		assertEquals("21", metadata.getProperty("java-version"));
		assertNotEquals(
				metadata.getProperty("extension-api-version"),
				metadata.getProperty("paper-compatibility-version"),
				"Bukkit's api-version must never masquerade as the Airdrop extension API version");
	}

	@Test
	void snapshotKeepsPluginExtensionPaperAndJavaVersionsDistinct() {
		AirdropVersions versions = new AirdropVersions(
				"5.0.0-SNAPSHOT", "1.0.0", "1.21.11", "21");

		assertEquals("5.0.0-SNAPSHOT", versions.pluginVersion());
		assertEquals("1.0.0", versions.extensionApiVersion());
		assertEquals("1.21.11", versions.paperApiVersion());
		assertEquals("21", versions.javaVersion());
	}

	private Properties loadMetadata() throws IOException {
		Properties properties = new Properties();
		try (InputStream input = getClass().getClassLoader()
				.getResourceAsStream("airdrop-api.properties")) {
			assertNotNull(input, "The build must generate airdrop-api.properties");
			properties.load(input);
		}
		return properties;
	}
}
