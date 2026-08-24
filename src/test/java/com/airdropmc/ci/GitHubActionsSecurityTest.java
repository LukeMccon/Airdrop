package com.airdropmc.ci;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubActionsSecurityTest {
	private static final Path WORKFLOWS_DIRECTORY = Path.of(".github", "workflows");
	private static final Pattern USES_LINE = Pattern.compile(
			"^\\s*uses:\\s*([^\\s#]+)(?:\\s+#\\s*(\\S.*?))?\\s*$"
	);
	private static final Pattern PINNED_EXTERNAL_ACTION = Pattern.compile(
			"^[^/@\\s]+/[^@\\s]+(?:/[^@\\s]+)*@[0-9a-fA-F]{40}$"
	);
	private static final Pattern VERSION_COMMENT = Pattern.compile(
			"^v\\d+(?:\\.\\d+)*(?:[-+][0-9A-Za-z.-]+)?$"
	);

	@Test
	void externalActionsUseCommitPinsWithReadableVersions() throws IOException {
		List<String> violations = new ArrayList<>();
		int externalActionCount = 0;

		for (Path workflow : workflowFiles()) {
			List<String> lines = Files.readAllLines(workflow);
			for (int index = 0; index < lines.size(); index++) {
				String line = lines.get(index);
				Matcher matcher = USES_LINE.matcher(line);
				if (!matcher.matches() || matcher.group(1).startsWith("./")) {
					continue;
				}

				externalActionCount++;
				String reference = matcher.group(1);
				String version = matcher.group(2);
				if (!PINNED_EXTERNAL_ACTION.matcher(reference).matches()
						|| version == null
						|| !VERSION_COMMENT.matcher(version).matches()) {
					violations.add(workflow + ":" + (index + 1) + " " + line.trim());
				}
			}
		}

		assertTrue(externalActionCount > 0, "Expected at least one external action reference");
		assertTrue(violations.isEmpty(), () ->
				"External actions must use a 40-character commit and readable version comment:\n"
						+ String.join("\n", violations));
	}

	@Test
	void workflowsDefaultToReadOnlyContents() throws IOException {
		Pattern readOnlyPermissions = Pattern.compile("(?m)^permissions:\\R  contents: read\\s*$");
		for (Path workflow : workflowFiles()) {
			String contents = Files.readString(workflow);
			assertTrue(readOnlyPermissions.matcher(contents).find(),
					() -> workflow + " must default to contents: read");
		}
	}

	@Test
	void onlyGitHubReleasePublisherCanWriteContents() throws IOException {
		String release = Files.readString(WORKFLOWS_DIRECTORY.resolve("release.yml"));
		int publishGitHub = release.indexOf("\n  publish-github:\n");
		int publishModrinth = release.indexOf("\n  publish-modrinth:\n");
		int writePermission = release.indexOf("      contents: write");
		long writePermissionCount = release.lines()
				.filter(line -> line.trim().equals("contents: write"))
				.count();

		assertEquals(1, writePermissionCount, "Only one job may receive contents: write");
		assertTrue(publishGitHub >= 0 && publishModrinth > publishGitHub,
				"Expected GitHub and Modrinth publish jobs");
		assertTrue(writePermission > publishGitHub && writePermission < publishModrinth,
				"contents: write must belong to publish-github");
	}

	@Test
	void dependabotProposesControlledGitHubActionUpdates() throws IOException {
		Path dependabot = Path.of(".github", "dependabot.yml");
		assertTrue(Files.isRegularFile(dependabot), "Expected .github/dependabot.yml");

		String contents = Files.readString(dependabot);
		assertTrue(contents.contains("package-ecosystem: \"github-actions\""));
		assertTrue(contents.contains("directory: \"/\""));
		assertTrue(contents.contains("interval: \"weekly\""));
		assertTrue(contents.contains("open-pull-requests-limit: 5"));
	}

	private List<Path> workflowFiles() throws IOException {
		try (Stream<Path> files = Files.list(WORKFLOWS_DIRECTORY)) {
			return files
					.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
					.sorted(Comparator.naturalOrder())
					.toList();
		}
	}
}
