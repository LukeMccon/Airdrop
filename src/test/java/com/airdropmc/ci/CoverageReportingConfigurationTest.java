package com.airdropmc.ci;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageReportingConfigurationTest {
	private static final Path BUILD_FILE = Path.of("build.gradle.kts");
	private static final Path CI_WORKFLOW = Path.of(".github", "workflows", "ci.yml");

	@Test
	void gradleGeneratesAJaCoCoXmlReportForSonarQubeCloud() throws IOException {
		String build = Files.readString(BUILD_FILE);

		assertTrue(build.contains("jacoco"), "Gradle must apply its built-in JaCoCo plugin");
		assertTrue(build.contains("id(\"org.sonarqube\")"),
				"Gradle must apply the SonarScanner plugin");
		assertTrue(build.contains("xml.required.set(true)"),
				"JaCoCo XML output must be enabled for SonarQube Cloud");
		assertTrue(build.contains("html.required.set(true)"),
				"JaCoCo HTML output must remain available for local inspection");
		assertTrue(build.contains("property(\"sonar.projectKey\", \"LukeMccon_Airdrop\")"));
		assertTrue(build.contains("property(\"sonar.organization\", \"luke-m\")"));
		assertTrue(build.contains("\"sonar.coverage.jacoco.xmlReportPaths\""));
		assertTrue(build.contains("\"build/reports/jacoco/test/jacocoTestReport.xml\""));
		assertTrue(build.contains("dependsOn(tasks.named(\"jacocoTestReport\"))"),
				"Sonar analysis must not run before the coverage report exists");
	}

	@Test
	void ciRunsAuthenticatedAnalysisAndRetainsCoverageArtifacts() throws IOException {
		String workflow = Files.readString(CI_WORKFLOW);

		assertTrue(workflow.contains("fetch-depth: 0"),
				"Sonar analysis needs complete Git history for blame and pull request analysis");
		assertTrue(workflow.contains("SONAR_TOKEN: ${{ secrets.SONARQUBE_TOKEN }}"));
		assertTrue(workflow.contains("SONAR_HOST_URL: ${{ secrets.SONARQUBE_HOST }}"));
		assertTrue(workflow.contains("./gradlew --no-daemon sonar"));
		assertTrue(workflow.contains("build/reports/jacoco/test"),
				"CI must retain the human-readable and XML coverage reports");
	}
}
