package com.airdropmc.api;

import java.util.Objects;

/**
 * Immutable version values captured when the service provider is created.
 *
 * @param pluginVersion Airdrop plugin version
 * @param extensionApiVersion supported extension API version signal
 * @param paperApiVersion exact Paper API compatibility declared by the plugin
 * @param javaVersion Java runtime contract
 */
public record AirdropVersions(
		String pluginVersion,
		String extensionApiVersion,
		String paperApiVersion,
		String javaVersion) {

	/** Validates and stores one complete version snapshot. */
	public AirdropVersions {
		pluginVersion = requireText(pluginVersion, "pluginVersion");
		extensionApiVersion = requireText(extensionApiVersion, "extensionApiVersion");
		paperApiVersion = requireText(paperApiVersion, "paperApiVersion");
		javaVersion = requireText(javaVersion, "javaVersion");
	}

	private static String requireText(String value, String name) {
		String required = Objects.requireNonNull(value, name);
		if (required.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return required;
	}
}
