package com.airdropmc.ci;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightkeeperIntegrationConfigurationTest {
	private static final Path LIGHTKEEPER_POM = Path.of("lightkeeper", "pom.xml");
	private static final Path MAVEN_WRAPPER_PROPERTIES = Path.of(
			"lightkeeper", ".mvn", "wrapper", "maven-wrapper.properties");
	private static final Path LIGHTKEEPER_PLUGIN_BOOTSTRAP = Path.of(
			"lightkeeper", "bootstrap-lightkeeper-plugin.sh");
	private static final Path ECONOMY_FIXTURE_PLUGIN = Path.of(
			"lightkeeper", "src", "main", "resources", "plugin.yml");
	private static final Path LIGHTKEEPER_CONFIG = Path.of(
			"lightkeeper", "src", "test", "resources", "overlay", "plugins", "Airdrop", "config.yml");
	private static final Path LIGHTKEEPER_PACKAGES = Path.of(
			"lightkeeper", "src", "test", "resources", "overlay", "plugins", "Airdrop", "packages.yml");
	private static final String LIGHTKEEPER_COMMIT = "be585af08221c37bcbc8c9d7f5a40a27dbd2dff1";

	@Test
	void sidecarPinsTheServerAndPluginProvisioningInputs() throws IOException {
		String pom = requiredContents(LIGHTKEEPER_POM);

		assertContains(pom, LIGHTKEEPER_COMMIT);
		assertContains(pom, "1.21.11");
		assertContains(pom, "https://jitpack.io");
		assertContains(pom, "https://repo.papermc.io/repository/maven-public/");
		assertContains(pom, "maven-failsafe-plugin");
		assertContains(pom, "airdrop.jar.path");
		assertContains(pom, "b0mk8uS6");
		assertContains(pom, "runtime-manifest.json");
		assertContains(pom, "lightkeeper-server");
	}

	@Test
	void mavenWrapperPinsAndVerifiesItsDistribution() throws IOException {
		String wrapper = requiredContents(MAVEN_WRAPPER_PROPERTIES);

		assertContains(wrapper, "apache-maven-3.9.16-bin.zip");
		assertTrue(Pattern.compile("(?m)^distributionSha256Sum=[0-9a-f]{64}$").matcher(wrapper).find(),
				"Maven wrapper must verify the pinned distribution with SHA-256");
	}

	@Test
	void sidecarBuildsAndProvisionsAnExactDecimalEconomyFixture() throws IOException {
		String pom = requiredContents(LIGHTKEEPER_POM);
		String plugin = requiredContents(ECONOMY_FIXTURE_PLUGIN);
		String config = requiredContents(LIGHTKEEPER_CONFIG);
		String packages = requiredContents(LIGHTKEEPER_PACKAGES);

		assertContains(pom, "VaultUnlockedAPI");
		assertContains(pom, "<vault-unlocked.version>2.20</vault-unlocked.version>");
		assertContains(pom, "maven-shade-plugin");
		assertContains(pom, "<project.build.outputTimestamp>");
		assertContains(pom, "${project.build.finalName}-fixture.jar");
		assertContains(pom, "<shadedArtifactAttached>true</shadedArtifactAttached>");
		assertContains(pom, "<renameTo>Vault.jar</renameTo>");
		assertContains(plugin, "name: Vault");
		assertContains(plugin, "main: com.airdropmc.lightkeeper.economy.LightkeeperEconomyPlugin");
		assertContains(config, "economy:\n  enabled: true");
		assertTrue(Pattern.compile("(?s)premium:.*price: 10\\.25").matcher(packages).find(),
				"LightKeeper must expose a separately priced integration package");
	}

	@Test
	void pluginAdapterVerifiesAndRepairsThePinnedJitpackDescriptor() throws IOException {
		String bootstrap = requiredContents(LIGHTKEEPER_PLUGIN_BOOTSTRAP);

		assertContains(bootstrap, LIGHTKEEPER_COMMIT);
		assertContains(bootstrap, "defff158d56b215c9756e0042dc456fc09b76e1f13cfc23d6a3941aefac444e7");
		assertContains(bootstrap, "META-INF/maven/plugin.xml");
		assertContains(bootstrap, "sha256sum");
	}

	@Test
	void gradleExposesAnIsolatedLightkeeperTask() throws IOException {
		String build = requiredContents(Path.of("build.gradle.kts"));

		assertContains(build, "register<Exec>(\"lightkeeperTest\")");
		assertContains(build, "dependsOn(\"jar\")");
		assertContains(build, "-Dairdrop.jar.path=");
		assertFalse(Pattern.compile("(?s)(named|register).*\\(\"(test|check|build)\"\\).*dependsOn\\(.*lightkeeperTest")
				.matcher(build).find(), "Fast Gradle verification must not depend on the real-server lane");
	}

	@Test
	void ciRunsLightkeeperSeparatelyAndAlwaysCollectsDiagnostics() throws IOException {
		String workflow = requiredContents(Path.of(".github", "workflows", "ci.yml"));

		assertContains(workflow, "lightkeeper-test:");
		assertContains(workflow, "./gradlew --no-daemon lightkeeperTest");
		assertContains(workflow, "lightkeeper/target/failsafe-reports");
		assertContains(workflow, "lightkeeper/target/lightkeeper-reports");
		assertContains(workflow, "lightkeeper/target/lightkeeper-diagnostics");
		assertContains(workflow, "lightkeeper/target/lightkeeper/runtime-manifest.json");
		assertContains(workflow, "del(.agentAuthToken)");
	}

	@Test
	void activePaperCompatibilityMetadataTargets12111() throws IOException {
		String build = requiredContents(Path.of("build.gradle.kts"));
		String release = requiredContents(Path.of(".github", "workflows", "release.yml"));

		assertFalse(build.contains("1.21.8"), "Gradle must not retain the old Paper floor");
		assertContains(build, "paper-api:1.21.11-R0.1-SNAPSHOT");
		assertContains(build, "minecraftVersion(\"1.21.11\")");
		assertContains(build, "apiVersion = \"1.21.11\"");
		assertContains(release, "GAME_VERSIONS=\"1.21.11\"");
	}

	private String requiredContents(Path path) throws IOException {
		assertTrue(Files.isRegularFile(path), () -> "Missing required integration file: " + path);
		return Files.readString(path);
	}

	private void assertContains(String contents, String expected) {
		assertTrue(contents.contains(expected), () -> "Expected configuration to contain: " + expected);
	}
}
