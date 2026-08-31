package com.airdropmc.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerFixtureContractTest {
	private static final Path FIXTURE = Path.of("consumer-fixture");
	private static final Path FIXTURE_JAR = FIXTURE.resolve(
			Path.of("build", "libs", "airdrop-consumer-fixture.jar"));

	@Test
	void fixtureIsASeparateBuildDrivenByTheVerifiedStagingPublication() throws IOException {
		String settings = Files.readString(Path.of("settings.gradle.kts"), StandardCharsets.UTF_8);
		String rootBuild = Files.readString(Path.of("build.gradle.kts"), StandardCharsets.UTF_8);
		String fixtureBuild = Files.readString(FIXTURE.resolve("build.gradle.kts"), StandardCharsets.UTF_8);

		assertFalse(settings.contains("consumer-fixture"));
		assertTrue(rootBuild.contains("register<GradleBuild>(\"consumerFixtureTest\")"));
		assertTrue(rootBuild.contains("dependsOn(verifyApiPublication)"));
		assertTrue(fixtureBuild.contains("compileOnly(\"maven.modrinth:airdrop:$airdropVersion\")"));
		assertTrue(fixtureBuild.contains("compileOnly(\"io.papermc.paper:paper-api:$paperVersion\")"));
		assertTrue(fixtureBuild.contains("ignoreGradleMetadataRedirection()"));
		assertFalse(fixtureBuild.contains("shadow"));
	}

	@Test
	void packagedFixtureHasExactNameMetadataAndNoEmbeddedAirdropClasses() throws IOException {
		assertTrue(Files.isRegularFile(FIXTURE_JAR), FIXTURE_JAR::toString);
		try (JarFile archive = new JarFile(FIXTURE_JAR.toFile())) {
			assertNotNull(archive.getJarEntry("plugin.yml"));
			assertNotNull(archive.getJarEntry(
					"dev/airdropmc/fixture/AirdropConsumerFixture.class"));
			assertFalse(archive.stream().anyMatch(
					entry -> entry.getName().startsWith("com/airdropmc/")));
		}
	}

	@Test
	void fixtureObservesEverySupportedIntegrationEventAndMarshalsReadiness() throws IOException {
		String source = Files.readString(FIXTURE.resolve(Path.of(
				"src", "main", "java", "dev", "airdropmc", "fixture",
				"AirdropConsumerFixture.java")), StandardCharsets.UTF_8);

		for (String handler : new String[]{
				"AirdropRequestEvent", "AirdropSpawnedEvent", "AirdropLandingAttemptEvent",
				"AirdropLandedEvent", "AirdropOutcomeEvent", "PackageRegistryChangedEvent",
				"AirdropRecoveredEvent", "AirdropRetiredEvent"}) {
			assertTrue(source.contains(handler), () -> "Missing consumer handler for " + handler);
		}
		assertTrue(source.contains("getServicesManager().load(AirdropApi.class)"));
		assertTrue(source.contains("api.readiness().whenComplete"));
		assertTrue(source.contains("getScheduler().runTask(this, action)"));
	}
}
