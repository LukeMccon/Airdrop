package com.airdropmc.internal.api;

import com.airdropmc.api.AirdropVersions;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/** Reads the build-generated compatibility contract from the plugin artifact. */
@ApiStatus.Internal
public final class AirdropVersionMetadata {
	private static final String RESOURCE = "/airdrop-api.properties";
	private static final AirdropVersions BUILD_VERSIONS = read();

	private AirdropVersionMetadata() {
	}

	/** Returns the immutable build-time compatibility values. */
	public static AirdropVersions versions() {
		return BUILD_VERSIONS;
	}

	private static AirdropVersions read() {
		Properties properties = new Properties();
		try (InputStream input = AirdropVersionMetadata.class.getResourceAsStream(RESOURCE)) {
			if (input == null) {
				throw new IllegalStateException("Missing generated resource " + RESOURCE);
			}
			properties.load(input);
		} catch (IOException exception) {
			throw new UncheckedIOException("Could not read generated resource " + RESOURCE, exception);
		}
		return new AirdropVersions(
				require(properties, "plugin-version"),
				require(properties, "extension-api-version"),
				require(properties, "paper-compatibility-version"),
				require(properties, "java-version"));
	}

	private static String require(Properties properties, String key) {
		String value = properties.getProperty(key);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Generated resource " + RESOURCE
					+ " is missing " + key);
		}
		return value;
	}
}
