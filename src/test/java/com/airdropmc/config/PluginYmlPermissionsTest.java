package com.airdropmc.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginYmlPermissionsTest {

	@Test
	void generatedPluginYml_permissionDefaultsAreLockedDown() throws Exception {
		Map<?, ?> root = loadPluginYml();

		Map<?, ?> permissions = castMap(root.get("permissions"), "permissions");
		Map<?, ?> packageAll = castMap(permissions.get("airdrop.package.all"), "airdrop.package.all");
		Map<?, ?> packageWildcard = castMap(permissions.get("airdrop.package.*"), "airdrop.package.*");
		Map<?, ?> cooldownBypass = castMap(permissions.get("airdrop.cooldown.bypass"), "airdrop.cooldown.bypass");
		Map<?, ?> admin = castMap(permissions.get("airdrop.admin"), "airdrop.admin");
		Map<?, ?> adminChildren = castMap(admin.get("children"), "airdrop.admin.children");

		assertEquals("false", String.valueOf(packageAll.get("default")));
		assertEquals("false", String.valueOf(packageWildcard.get("default")));
		assertEquals("op", String.valueOf(cooldownBypass.get("default")));
		assertEquals("op", String.valueOf(admin.get("default")));
		assertEquals("true", String.valueOf(adminChildren.get("airdrop.cooldown.bypass")));
		assertNull(permissions.get("airdrop.limits.bypass"));
	}

	@Test
	void generatedPluginYml_supportsVaultUnlockedWithoutTreasury() throws Exception {
		Map<?, ?> root = loadPluginYml();

		Object softDepend = root.get("softdepend");
		assertTrue(softDepend instanceof Iterable<?>);
		String dependencies = String.valueOf(softDepend);
		assertTrue(dependencies.contains("Vault"));
		assertFalse(dependencies.contains("Treasury"));
	}

	@Test
	void generatedPluginYml_matchesProjectVersion() throws Exception {
		Map<?, ?> root = loadPluginYml();
		String projectVersion = System.getProperty("airdrop.projectVersion");
		assertNotNull(projectVersion, "Gradle should provide the project version to tests");

		assertEquals(projectVersion, String.valueOf(root.get("version")));
	}

	@Test
	void generatedPluginYml_targetsOldestSupportedPaperVersion() throws Exception {
		Map<?, ?> root = loadPluginYml();

		assertEquals("1.21.11", String.valueOf(root.get("api-version")));
	}

	private Map<?, ?> loadPluginYml() throws Exception {
		InputStream stream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
		assertNotNull(stream, "Generated plugin.yml should be available on the test runtime classpath");

		Map<?, ?> root;
		try (stream) {
			root = new Yaml().load(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
		}
		assertNotNull(root);
		return root;
	}

	private static Map<?, ?> castMap(Object value, String key) {
		assertNotNull(value, "Missing section in plugin.yml: " + key);
		if (!(value instanceof Map<?, ?> map)) {
			throw new IllegalStateException("Expected map section in plugin.yml: " + key);
		}
		return map;
	}
}
