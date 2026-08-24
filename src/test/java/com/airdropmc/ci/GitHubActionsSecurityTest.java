package com.airdropmc.ci;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubActionsSecurityTest {
	private static final Path WORKFLOWS_DIRECTORY = Path.of(".github", "workflows");
	private static final Pattern VERSION_COMMENT = Pattern.compile(
			"^v\\d+(?:\\.\\d+)*(?:[-+][0-9A-Za-z.-]+)?$"
	);

	@Test
	void externalActionsUseCommitPinsWithReadableVersions() throws IOException {
		ActionScan scan = scanExternalActions(WORKFLOWS_DIRECTORY);

		assertTrue(scan.externalActionCount() > 0, "Expected at least one external action reference");
		assertTrue(scan.violations().isEmpty(), () ->
				"External actions must use a 40-character commit and readable version comment:\n"
						+ String.join("\n", scan.violations()));
	}

	@Test
	void workflowsDefaultToReadOnlyContents() throws IOException {
		Pattern readOnlyPermissions = Pattern.compile("(?m)^permissions:\\R  contents: read\\s*$");
		for (Path workflow : workflowFiles(WORKFLOWS_DIRECTORY)) {
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
		List<WritePermission> writePermissions = findWritePermissions(WORKFLOWS_DIRECTORY);
		int writePermission = release.indexOf("      contents: write");

		assertEquals(1, writePermissions.size(), "Only one job may receive contents: write");
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
		assertTrue(hasControlledGitHubActionsUpdates(contents));
	}

	@Test
	void compactYamlActionStepsAreScanned(@TempDir Path workflowsDirectory) throws IOException {
		Files.writeString(workflowsDirectory.resolve("compact.yml"), """
				jobs:
				  test:
				    steps:
				      - uses: owner/action@v1
				""");

		ActionScan scan = scanExternalActions(workflowsDirectory);

		assertEquals(1, scan.externalActionCount());
		assertEquals(1, scan.violations().size());
	}

	@Test
	void writePermissionScanIncludesEveryWorkflow(@TempDir Path workflowsDirectory) throws IOException {
		Files.writeString(workflowsDirectory.resolve("release.yml"), "permissions:\n  contents: read\n");
		Path ci = workflowsDirectory.resolve("ci.yml");
		Files.writeString(ci, "permissions:\n  contents: write\n");

		List<WritePermission> permissions = findWritePermissions(workflowsDirectory);

		assertEquals(List.of(new WritePermission(ci, 1)), permissions);
	}

	@Test
	void commentedDependabotSettingsDoNotSatisfyPolicy() {
		String commentedConfiguration = """
				version: 2
				updates:
				  # package-ecosystem: "github-actions"
				  # directory: "/"
				  # interval: "weekly"
				  # open-pull-requests-limit: 5
				""";

		assertFalse(hasControlledGitHubActionsUpdates(commentedConfiguration));
	}

	private ActionScan scanExternalActions(Path workflowsDirectory) throws IOException {
		List<String> violations = new ArrayList<>();
		int externalActionCount = 0;

		for (Path workflow : workflowFiles(workflowsDirectory)) {
			List<String> lines = Files.readAllLines(workflow);
			for (int index = 0; index < lines.size(); index++) {
				String line = lines.get(index);
				ActionReference action = parseActionReference(line);
				if (action == null || action.reference().startsWith("./")) {
					continue;
				}

				externalActionCount++;
				if (!isPinnedExternalAction(action.reference())
						|| action.version() == null
						|| !VERSION_COMMENT.matcher(action.version()).matches()) {
					violations.add(workflow + ":" + (index + 1) + " " + line.trim());
				}
			}
		}

		return new ActionScan(externalActionCount, violations);
	}

	private List<WritePermission> findWritePermissions(Path workflowsDirectory) throws IOException {
		List<WritePermission> permissions = new ArrayList<>();
		for (Path workflow : workflowFiles(workflowsDirectory)) {
			List<String> lines = Files.readAllLines(workflow);
			for (int index = 0; index < lines.size(); index++) {
				if (lines.get(index).trim().equals("contents: write")) {
					permissions.add(new WritePermission(workflow, index));
				}
			}
		}
		return permissions;
	}

	private boolean hasControlledGitHubActionsUpdates(String contents) {
		List<String> activeLines = contents.lines()
				.map(String::stripTrailing)
				.filter(line -> !line.isBlank())
				.filter(line -> !line.stripLeading().startsWith("#"))
				.toList();
		List<String> expectedBlock = List.of(
				"  - package-ecosystem: \"github-actions\"",
				"    directory: \"/\"",
				"    schedule:",
				"      interval: \"weekly\"",
				"    open-pull-requests-limit: 5"
		);
		int versionIndex = activeLines.indexOf("version: 2");
		int updatesIndex = activeLines.indexOf("updates:");

		return versionIndex >= 0
				&& updatesIndex > versionIndex
				&& startsWith(activeLines, updatesIndex + 1, expectedBlock);
	}

	private ActionReference parseActionReference(String line) {
		String directive = line.strip();
		if (directive.startsWith("-")) {
			directive = directive.substring(1).stripLeading();
		}
		if (!directive.startsWith("uses:")) {
			return null;
		}

		String value = directive.substring("uses:".length()).strip();
		int commentIndex = versionCommentIndex(value);
		if (commentIndex < 0) {
			return new ActionReference(value, null);
		}

		String reference = value.substring(0, commentIndex).stripTrailing();
		String version = value.substring(commentIndex + 1).strip();
		return new ActionReference(reference, version);
	}

	private int versionCommentIndex(String value) {
		for (int index = 1; index < value.length(); index++) {
			if (value.charAt(index) == '#' && Character.isWhitespace(value.charAt(index - 1))) {
				return index;
			}
		}
		return -1;
	}

	private boolean isPinnedExternalAction(String reference) {
		int separator = reference.lastIndexOf('@');
		if (separator <= 0 || separator != reference.indexOf('@')) {
			return false;
		}

		String actionPath = reference.substring(0, separator);
		String commit = reference.substring(separator + 1);
		String[] pathSegments = actionPath.split("/", -1);
		if (pathSegments.length < 2 || commit.length() != 40) {
			return false;
		}
		for (String segment : pathSegments) {
			if (segment.isBlank() || segment.chars().anyMatch(Character::isWhitespace)) {
				return false;
			}
		}
		return commit.chars().allMatch(character ->
				character >= '0' && character <= '9'
						|| character >= 'a' && character <= 'f'
						|| character >= 'A' && character <= 'F');
	}

	private boolean startsWith(List<String> lines, int startIndex, List<String> expected) {
		if (startIndex < 0 || startIndex + expected.size() > lines.size()) {
			return false;
		}
		return lines.subList(startIndex, startIndex + expected.size()).equals(expected);
	}

	private List<Path> workflowFiles(Path workflowsDirectory) throws IOException {
		try (Stream<Path> files = Files.list(workflowsDirectory)) {
			return files
					.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
					.sorted(Comparator.naturalOrder())
					.toList();
		}
	}

	private record ActionScan(int externalActionCount, List<String> violations) {
	}

	private record ActionReference(String reference, String version) {
	}

	private record WritePermission(Path workflow, int lineIndex) {
	}
}
