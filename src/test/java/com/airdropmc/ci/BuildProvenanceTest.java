package com.airdropmc.ci;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	private static final Path ROOT_VERIFICATION_METADATA =
			Path.of("gradle", "verification-metadata.xml");
	private static final Path CONSUMER_VERIFICATION_METADATA =
			Path.of("consumer-fixture", "gradle", "verification-metadata.xml");
	private static final Path CONSUMER_BUILD_FILE = Path.of("consumer-fixture", "build.gradle.kts");
	private static final Path CI_WORKFLOW = Path.of(".github", "workflows", "ci.yml");
	private static final Path RELEASE_WORKFLOW = Path.of(".github", "workflows", "release.yml");

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
	void wrapperDistributionChecksumIsPinned() throws IOException {
		Properties properties = new Properties();
		try (InputStream input = Files.newInputStream(WRAPPER_PROPERTIES)) {
			properties.load(input);
		}

		String distributionChecksum = properties.getProperty("distributionSha256Sum");
		assertNotNull(distributionChecksum, "Gradle must verify its downloaded distribution");
		assertTrue(distributionChecksum.matches("[0-9a-fA-F]{64}"),
				"The distribution checksum must be a SHA-256 digest");
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

	@Test
	void strictSha256MetadataCoversRootAndExternalConsumerGraphs() throws IOException {
		String rootMetadata = verifiedSha256Metadata(ROOT_VERIFICATION_METADATA);
		String consumerMetadata = verifiedSha256Metadata(CONSUMER_VERIFICATION_METADATA);

		assertFalse(rootMetadata.contains("<trusted-artifacts>"),
				"The root graph must verify every resolved external artifact");
		for (String coldCacheMetadata : List.of(
				"groovy-bom-4.0.22.module",
				"guava-parent-33.3.1-jre.pom",
				"guava-parent-33.4.8-jre.pom",
				"jackson-base-2.15.2.pom",
				"jackson-dataformats-text-2.15.2.pom",
				"junit-bom-5.10.3.module",
				"junit-bom-5.7.1.module",
				"junit-bom-5.9.2.module",
				"junit-bom-5.9.2.pom",
				"junit-bom-5.9.3.module",
				"junit-bom-6.1.3.pom",
				"spring-framework-bom-5.3.39.module")) {
			assertTrue(rootMetadata.contains("<artifact name=\"" + coldCacheMetadata + "\">"),
					() -> "Fresh Gradle resolution requires verified metadata for "
							+ coldCacheMetadata);
		}
		assertTrue(consumerMetadata.contains("<trusted-artifacts>"));
		assertTrue(consumerMetadata.contains("group=\"maven.modrinth\""));
		assertTrue(consumerMetadata.contains("name=\"airdrop\""));
		assertTrue(consumerMetadata.contains("locally staged changing Airdrop artifact"));
		assertTrue(consumerMetadata.contains("<artifact name=\"junit-bom-6.1.3.pom\">"),
				"Fresh consumer-fixture resolution requires the JUnit BOM POM checksum");
		assertFalse(consumerMetadata.contains("<component group=\"maven.modrinth\""),
				"The local staged artifact must be trusted by identity, not pinned to one build hash");
	}

	@Test
	void strictVerificationIsExplicitForRootConsumerAndIsolatedBuilds() throws IOException {
		String build = Files.readString(BUILD_FILE);
		String ci = Files.readString(CI_WORKFLOW);
		String release = Files.readString(RELEASE_WORKFLOW);

		assertTrue(build.contains("DependencyVerificationMode.STRICT"));
		assertTrue(build.contains("\"--dependency-verification=strict\""));
		assertTrue(ci.contains("./gradlew --no-daemon --dependency-verification=strict"));
		assertTrue(release.contains("./gradlew --no-daemon --dependency-verification=strict"));
	}

	@Test
	void consumerAndPublicationChecksCoverTheFinalArtifactGraph() throws IOException {
		String build = Files.readString(BUILD_FILE);
		String consumerBuild = Files.readString(CONSUMER_BUILD_FILE);

		assertTrue(count(build, "dependsOn(verifyApiPublication)") >= 2,
				"Release verification and the consumer fixture must both depend on staged publication checks");
		assertTrue(build.contains("checksumAlgorithms"));
		assertTrue(build.contains("MessageDigest.getInstance(algorithm)"));
		assertTrue(build.contains("Staged POM must be dependency-free"));
		assertTrue(build.contains("Staged API publication must not contain Gradle module metadata"));
		assertTrue(consumerBuild.contains("exclusiveContent"));
		assertTrue(consumerBuild.contains("includeModule(\"com.mojang\", \"brigadier\")"));
		assertTrue(consumerBuild.contains("includeModule(\"net.md-5\", \"bungeecord-chat\")"));
		assertTrue(consumerBuild.contains("isPreserveFileTimestamps = false"));
		assertTrue(consumerBuild.contains("isReproducibleFileOrder = true"));
	}

	@Test
	void ciUploadsAProviderSelectedRuntimeArtifactWithoutVersionCouplingOrGlobs() throws IOException {
		String build = Files.readString(BUILD_FILE);
		String ci = Files.readString(CI_WORKFLOW);

		assertTrue(build.contains("register<Sync>(\"prepareCiRuntimeArtifact\")"));
		assertTrue(build.contains("from(releaseJar.flatMap { it.archiveFile })"));
		assertTrue(ci.contains("prepareCiRuntimeArtifact"));
		assertTrue(ci.contains("build/ci-artifacts/Airdrop.jar"));
		assertFalse(ci.contains("build/libs/*.jar"));
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

	private static String verifiedSha256Metadata(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), () -> "Missing strict verification metadata: " + path);
		String metadata = Files.readString(path);
		assertTrue(metadata.contains("<verify-metadata>true</verify-metadata>"));
		assertFalse(Pattern.compile("<(md5|sha1|sha512)\\s").matcher(metadata).find(),
				"Verification metadata must contain only SHA-256 checksums");

		Matcher artifacts = Pattern.compile("<artifact name=\"[^\"]+\">(.*?)</artifact>", Pattern.DOTALL)
				.matcher(metadata);
		int artifactCount = 0;
		while (artifacts.find()) {
			artifactCount++;
			assertTrue(Pattern.compile("<sha256\\s+[^>]*value=\"[0-9a-f]{64}\"")
					.matcher(artifacts.group(1)).find(),
					() -> "Artifact lacks SHA-256 verification in " + path);
		}
		assertTrue(artifactCount > 0, () -> "No verified external artifacts in " + path);
		return metadata;
	}

	private static int count(String haystack, String needle) {
		return (haystack.length() - haystack.replace(needle, "").length()) / needle.length();
	}
}
