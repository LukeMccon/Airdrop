package com.airdropmc.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PluginYmlPermissionsTest {

	@Test
	void generatedPluginYml_permissionDefaultsAreLockedDown() throws Exception {
		InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
		assertNotNull(stream, "Generated plugin.yml should be available on the test runtime classpath");

		String yamlText;
		try (stream) {
			yamlText = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}

		Yaml yaml = new Yaml();
		Map<?, ?> root = yaml.load(yamlText);
		assertNotNull(root);

		Map<?, ?> permissions = castMap(root.get("permissions"), "permissions");
		Map<?, ?> packageAll = castMap(permissions.get("airdrop.package.all"), "airdrop.package.all");
		Map<?, ?> packageWildcard = castMap(permissions.get("airdrop.package.*"), "airdrop.package.*");
		Map<?, ?> admin = castMap(permissions.get("airdrop.admin"), "airdrop.admin");

		assertEquals("false", String.valueOf(packageAll.get("default")));
		assertEquals("false", String.valueOf(packageWildcard.get("default")));
		assertEquals("op", String.valueOf(admin.get("default")));
	}

	private static Map<?, ?> castMap(Object value, String key) {
		assertNotNull(value, "Missing section in plugin.yml: " + key);
		if (!(value instanceof Map<?, ?> map)) {
			throw new IllegalStateException("Expected map section in plugin.yml: " + key);
		}
		return map;
	}
}
