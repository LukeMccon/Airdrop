package com.airdropmc.ci;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildProvenanceTest {
	private static final Path BUILD_FILE = Path.of("build.gradle.kts");
	private static final Path SETTINGS_FILE = Path.of("settings.gradle.kts");
	private static final Path DEPENDABOT_FILE = Path.of(".github", "dependabot.yml");
	private static final Path WRAPPER_PROPERTIES = Path.of("gradle", "wrapper", "gradle-wrapper.properties");
	private static final Path WRAPPER_JAR = Path.of("gradle", "wrapper", "gradle-wrapper.jar");
	private static final String DISTRIBUTION_SHA256 =
			"8fad3d78296ca518113f3d29016617c7f9367dc005f932bd9d93bf45ba46072b";
	private static final String WRAPPER_JAR_SHA256 =
			"76805e32c009c0cf0dd5d206bddc9fb22ea42e84db904b764f3047de095493f3";

	@Test
	void rootRepositoriesExclusivelyRoutePaperAndVault() throws IOException {
		String build = Files.readString(BUILD_FILE);

		assertTrue(build.contains("exclusiveContent"));
		assertTrue(build.contains("includeGroup(\"io.papermc.paper\")"));
		assertTrue(build.contains("includeModule(\"com.mojang\", \"brigadier\")"));
		assertTrue(build.contains("includeModule(\"net.md-5\", \"bungeecord-chat\")"));
		assertTrue(build.contains("includeGroup(\"net.milkbowl.vault\")"));
		assertTrue(build.contains("https://repo.papermc.io/repository/maven-public/"));
		assertTrue(build.contains("https://repo.codemc.io/repository/creatorfromhell/"));
		assertFalse(build.contains("https://repo.codemc.io/repository/maven-public/"));
		assertFalse(build.contains("https://oss.sonatype.org/content/groups/public/"));
		assertFalse(build.contains("https://jitpack.io"));
	}

	@Test
	void pluginResolutionUsesOnlyThePluginPortal() throws IOException {
		String settings = Files.readString(SETTINGS_FILE);

		assertTrue(settings.contains("gradlePluginPortal()"));
		assertFalse(settings.contains("repo.papermc.io"));
	}

	@Test
	void archivesDeclareReproducibleOutputsAndPackagedLicense() throws IOException {
		String build = Files.readString(BUILD_FILE);

		assertTrue(build.contains("isPreserveFileTimestamps = false"));
		assertTrue(build.contains("isReproducibleFileOrder = true"));
		assertTrue(build.contains("LICENSE-Airdrop.txt"));
	}

	@Test
	void packagedRuntimeJarContainsTheProjectLicense() throws IOException {
		String runtimeJarProperty = System.getProperty("airdrop.runtimeJar");
		assertNotNull(runtimeJarProperty, "Gradle must pass the exact runtime JAR path to tests");
		Path runtimeJar = Path.of(runtimeJarProperty);
		assertTrue(Files.isRegularFile(runtimeJar), () -> "Missing runtime JAR: " + runtimeJar);

		try (JarFile archive = new JarFile(runtimeJar.toFile())) {
			var license = archive.getJarEntry("META-INF/LICENSE-Airdrop.txt");
			assertNotNull(license, "Runtime JAR must contain META-INF/LICENSE-Airdrop.txt");
			try (InputStream input = archive.getInputStream(license)) {
				assertArrayEquals(Files.readAllBytes(Path.of("LICENSE")), input.readAllBytes());
			}
		}
	}

	@Test
	void wrapperChecksumsArePinned() throws IOException, NoSuchAlgorithmException {
		Properties properties = new Properties();
		try (InputStream input = Files.newInputStream(WRAPPER_PROPERTIES)) {
			properties.load(input);
		}

		assertEquals(DISTRIBUTION_SHA256, properties.getProperty("distributionSha256Sum"));
		assertEquals(WRAPPER_JAR_SHA256, sha256(WRAPPER_JAR));
	}

	@Test
	void dependabotCoversGradleAndLightkeeperMaven() throws IOException {
		Map<?, ?> root = yamlMap(loadYaml(Files.readString(DEPENDABOT_FILE)));
		List<?> updates = yamlList(root.get("updates"));

		assertControlledUpdater(updates, "gradle", "/");
		assertControlledUpdater(updates, "maven", "/lightkeeper");
	}

	@Test
	void buildDefinesRuntimeDependencyAndReproducibilityGatesWithoutArtifactGlobs() throws IOException {
		String build = Files.readString(BUILD_FILE);

		assertTrue(build.contains("verifyRuntimeClasspathEmpty"));
		assertTrue(build.contains("verifyReproducibleRuntimeJar"));
		assertTrue(build.contains("releaseJar.flatMap { it.archiveFile }"));
		assertFalse(build.contains("build/libs/*.jar"));
		assertFalse(build.contains("fileTree(layout.buildDirectory.dir(\"libs\"))"));
	}

	private void assertControlledUpdater(List<?> updates, String ecosystem, String directory) {
		Map<?, ?> updater = updates.stream()
				.map(BuildProvenanceTest::yamlMap)
				.filter(candidate -> ecosystem.equals(candidate.get("package-ecosystem")))
				.filter(candidate -> directory.equals(candidate.get("directory")))
				.findFirst()
				.orElseThrow(() -> new AssertionError(
						"Missing Dependabot updater for " + ecosystem + " in " + directory
				));
		Map<?, ?> schedule = yamlMap(updater.get("schedule"));
		assertEquals("weekly", schedule.get("interval"));
		assertEquals(5, updater.get("open-pull-requests-limit"));
	}

	private static Object loadYaml(String source) {
		LoaderOptions loaderOptions = new LoaderOptions();
		loaderOptions.setAllowDuplicateKeys(false);
		return new Yaml(new SafeConstructor(loaderOptions)).load(source);
	}

	private static Map<?, ?> yamlMap(Object value) {
		assertTrue(value instanceof Map<?, ?>, "Expected a YAML map");
		return (Map<?, ?>) value;
	}

	private static List<?> yamlList(Object value) {
		assertTrue(value instanceof List<?>, "Expected a YAML list");
		return (List<?>) value;
	}

	private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
	}
}
