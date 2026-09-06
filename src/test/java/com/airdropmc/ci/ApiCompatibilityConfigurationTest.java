package com.airdropmc.ci;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiCompatibilityConfigurationTest {

	@Test
	void gradlePropertiesOwnEveryCompatibilityVersion() throws IOException {
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(
				Path.of("gradle.properties"), StandardCharsets.UTF_8)) {
			properties.load(reader);
		}

		assertEquals("4.1.0-SNAPSHOT", properties.getProperty("airdropPluginVersion"));
		assertEquals("1.0.0", properties.getProperty("airdropExtensionApiVersion"));
		assertEquals("1.21.11", properties.getProperty("airdropPaperVersion"));
		assertEquals("21", properties.getProperty("airdropJavaVersion"));
		assertEquals("0.26.1", properties.getProperty("airdropJapicmpVersion"));
		assertFalse(properties.containsKey("airdropApiBaselineRequired"),
				"The final API baseline must not retain a temporary opt-out switch");
	}

	@Test
	void buildProvidesDeterministicSignatureAndExplicitPreviousArtifactChecks() throws IOException {
		String build = Files.readString(Path.of("build.gradle.kts"));
		String generator = Files.readString(Path.of(
				"build-tools/src/main/java/com/airdropmc/tools/ApiSignatureGenerator.java"));

		assertTrue(build.contains("generateApiSignature"));
		assertTrue(build.contains("verifyApiCompatibility"));
		assertTrue(build.contains("previousApiJar"));
		assertTrue(build.contains("previousApiBaseline"));
		assertTrue(build.contains("Previous API JAR must contain airdrop-api.properties"));
		assertTrue(build.contains("previousApiMajor != currentApiMajor"));
		assertTrue(build.contains("reviewedApiBaselineChange"));
		assertTrue(build.contains("Airdrop-API-Version"));
		assertTrue(build.contains("japicmp"));
		assertFalse(build.contains("latest.release"));
		assertTrue(generator.contains("javassist.bytecode.ClassFile"),
				"Signature generation must inspect classfiles without loading API classes");
		assertFalse(generator.contains("Class.forName"));
		assertFalse(build.contains("URLClassLoader"));
		assertFalse(build.contains("baseline recording is pending AIRDR-43"));
	}

	@Test
	void semanticPolicyAndFourOneMigrationAreTrackedDespiteTheDocsIgnoreRule() throws IOException {
		String ignore = Files.readString(Path.of(".gitignore"));
		String policy = Files.readString(Path.of("docs/development/api-versioning.md"));
		Path migrationPath = Path.of("docs/migration-4.1.md");
		assertTrue(Files.isRegularFile(migrationPath), "Expected the Airdrop 4.1 migration guide");
		String migration = Files.readString(migrationPath);

		assertTrue(ignore.contains("!docs/development/api-versioning.md"));
		assertTrue(ignore.contains("!docs/migration-4.1.md"));
		assertTrue(policy.contains("API minor"));
		assertTrue(policy.contains("API major"));
		assertTrue(policy.contains("deprecated"));
		assertTrue(migration.contains("com.airdropmc.api"));
		assertTrue(migration.contains("PackageDropEvent"));
		assertTrue(migration.contains("PackageLandEvent"));
	}

	@Test
	void fourOneDecisionRecordsExplainTheAcceptedImplementationBreak() throws IOException {
		for (Path record : new Path[]{
				Path.of("docs/development/2026-08-31-airdr-31-developer-experience-design.md"),
				Path.of("docs/development/2026-08-31-airdr-31-implementation-plan.md")}) {
			String contents = Files.readString(record);
			String normalized = contents.replaceAll("\\s+", " ");
			assertTrue(contents.contains("Revision (2026-09-06)"), record.toString());
			assertTrue(contents.contains("Airdrop 4.1.0"), record.toString());
			assertTrue(normalized.contains("no known consumers"), record.toString());
			assertTrue(normalized.contains("4.0 implementation-facing"), record.toString());
			assertTrue(contents.contains("extension API") && contents.contains("1.0.0"),
					record.toString());
		}
	}

	@Test
	void releaseLineGuardDerivesTheCurrentPluginAndApiVersions() throws Exception {
		Path verifier = Path.of("scripts/verify-release-line");
		assertTrue(Files.isExecutable(verifier), "Expected an executable release-line verifier");
		String verifierContents = Files.readString(verifier, StandardCharsets.UTF_8);
		assertTrue(verifierContents.contains("airdropPluginVersion"));
		assertTrue(verifierContents.contains("airdropExtensionApiVersion"));
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(
				Path.of("gradle.properties"), StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		String sourceVersion = properties.getProperty("airdropPluginVersion");
		assertFalse(verifierContents.contains(sourceVersion),
				"The release-line guard must derive the current version instead of hardcoding it");

		Process process = new ProcessBuilder(verifier.toString())
				.redirectErrorStream(true)
				.start();
		assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Release-line verifier timed out");
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertEquals(0, process.exitValue(), output);

		String build = Files.readString(Path.of("build.gradle.kts"));
		assertTrue(build.contains("register<Exec>(\"verifyReleaseLine\")"));
		assertTrue(build.contains("dependsOn(verifyReleaseLine)"));
	}

	@Test
	void localBuildDefinitionsRunCompatibilityAndReleaseVerification() throws IOException {
		String ci = Files.readString(Path.of(".github/workflows/ci.yml"));
		String release = Files.readString(Path.of(".github/workflows/release.yml"));

		assertTrue(ci.contains("verifyApiCompatibility"));
		assertTrue(release.contains("verifyApiCompatibility"));
		assertTrue(release.contains("verifyReleaseArtifact"));
	}

	@Test
	void releaseVerificationCrossChecksEveryPublishedVersionBoundary() throws IOException {
		String build = Files.readString(Path.of("build.gradle.kts"));

		assertTrue(build.contains("sourceDevelopmentVersion.removeSuffix(\"-SNAPSHOT\")"));
		assertTrue(build.contains("airdrop-api.properties"));
		assertTrue(build.contains("Airdrop-API-Version"));
		assertTrue(build.contains("Airdrop-Paper-Version"));
		assertTrue(build.contains("Airdrop-Java-Version"));
		assertTrue(build.contains("com/airdropmc/Airdrop.class"));
		assertTrue(build.contains("CHANGELOG.md"));
		assertTrue(build.contains("api-version"));
	}
}
