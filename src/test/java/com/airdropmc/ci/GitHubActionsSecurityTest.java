package com.airdropmc.ci;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
		for (Path workflow : workflowFiles(WORKFLOWS_DIRECTORY)) {
			Map<?, ?> document = yamlMap(loadYaml(Files.readString(workflow)));
			Object permissions = value(document, "permissions");
			assertTrue(hasContentsPermission(permissions, "read"),
					() -> workflow + " must default to contents: read");
		}
	}

	@Test
	void onlyGitHubReleasePublisherCanWriteContents() throws IOException {
		List<WritePermission> writePermissions = findWritePermissions(WORKFLOWS_DIRECTORY);

		assertEquals(
				List.of(new WritePermission(WORKFLOWS_DIRECTORY.resolve("release.yml"), "job:publish-github")),
				writePermissions,
				"Only the GitHub release publisher may receive contents: write"
		);
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

		assertEquals(List.of(new WritePermission(ci, "workflow")), permissions);
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

	@Test
	void quotedAndSpacedUsesKeysAreScanned(@TempDir Path workflowsDirectory) throws IOException {
		Files.writeString(workflowsDirectory.resolve("variants.yml"), """
				jobs:
				  test:
				    steps:
				      - uses : owner/first@v1
				      - "uses": owner/second@v2
				""");

		ActionScan scan = scanExternalActions(workflowsDirectory);

		assertEquals(2, scan.externalActionCount());
		assertEquals(2, scan.violations().size());
	}

	@Test
	void writePermissionScanIncludesWriteAllAndFlowMaps(@TempDir Path workflowsDirectory) throws IOException {
		Path ci = workflowsDirectory.resolve("ci.yml");
		Files.writeString(ci, """
				permissions: read-all
				jobs:
				  broad:
				    permissions: write-all
				  inline:
				    permissions: { contents: write }
				""");

		List<WritePermission> permissions = findWritePermissions(workflowsDirectory);

		assertEquals(2, permissions.size());
	}

	@Test
	void duplicateDependabotUpdatesKeysDoNotSatisfyPolicy() {
		String duplicateConfiguration = """
				version: 2
				updates:
				  - package-ecosystem: "github-actions"
				    directory: "/"
				    schedule:
				      interval: "weekly"
				    open-pull-requests-limit: 5
				updates: []
				""";

		assertFalse(hasControlledGitHubActionsUpdates(duplicateConfiguration));
	}

	private ActionScan scanExternalActions(Path workflowsDirectory) throws IOException {
		List<String> violations = new ArrayList<>();
		int externalActionCount = 0;

		for (Path workflow : workflowFiles(workflowsDirectory)) {
			List<String> semanticReferences = findUsesReferences(loadYaml(Files.readString(workflow)));
			List<ActionReference> lineReferences = new ArrayList<>();
			List<String> lines = Files.readAllLines(workflow);
			for (int index = 0; index < lines.size(); index++) {
				String line = lines.get(index);
				ActionReference action = parseActionReference(line);
				if (action == null) {
					continue;
				}
				lineReferences.add(action);
				if (action.reference().startsWith("./")) {
					continue;
				}

				if (!isPinnedExternalAction(action.reference())
						|| action.version() == null
						|| !VERSION_COMMENT.matcher(action.version()).matches()) {
					violations.add(workflow + ":" + (index + 1) + " " + line.trim());
				}
			}

			List<String> lineValues = lineReferences.stream()
					.map(ActionReference::reference)
					.toList();
			if (!sameReferences(semanticReferences, lineValues)) {
				violations.add(workflow + ": every YAML uses entry must use one canonical, commented line");
			}
			externalActionCount += semanticReferences.stream()
					.filter(reference -> !reference.startsWith("./"))
					.count();
		}

		return new ActionScan(externalActionCount, violations);
	}

	private List<WritePermission> findWritePermissions(Path workflowsDirectory) throws IOException {
		List<WritePermission> permissions = new ArrayList<>();
		for (Path workflow : workflowFiles(workflowsDirectory)) {
			Map<?, ?> document = yamlMap(loadYaml(Files.readString(workflow)));
			if (grantsContentsWrite(value(document, "permissions"))) {
				permissions.add(new WritePermission(workflow, "workflow"));
			}

			Map<?, ?> jobs = yamlMap(value(document, "jobs"));
			for (Map.Entry<?, ?> job : jobs.entrySet()) {
				Map<?, ?> jobDefinition = yamlMap(job.getValue());
				if (grantsContentsWrite(value(jobDefinition, "permissions"))) {
					permissions.add(new WritePermission(workflow, "job:" + job.getKey()));
				}
			}
		}
		return permissions;
	}

	private boolean hasControlledGitHubActionsUpdates(String contents) {
		try {
			Map<?, ?> document = yamlMap(loadYaml(contents));
			if (!(value(document, "version") instanceof Number version) || version.intValue() != 2) {
				return false;
			}

			Object updatesValue = value(document, "updates");
			if (!(updatesValue instanceof List<?> updates)) {
				return false;
			}

			long matchingUpdates = updates.stream()
					.map(this::yamlMap)
					.filter(update -> "github-actions".equals(value(update, "package-ecosystem")))
					.filter(update -> "/".equals(value(update, "directory")))
					.filter(update -> {
						Map<?, ?> schedule = yamlMap(value(update, "schedule"));
						return "weekly".equals(value(schedule, "interval"));
					})
					.filter(update -> value(update, "open-pull-requests-limit") instanceof Number limit
							&& limit.intValue() == 5)
					.count();
			return matchingUpdates == 1;
		} catch (YAMLException exception) {
			return false;
		}
	}

	private ActionReference parseActionReference(String line) {
		String directive = line.strip();
		if (directive.startsWith("-")) {
			directive = directive.substring(1).stripLeading();
		}
		int valueSeparator = directive.indexOf(':');
		if (valueSeparator < 0) {
			return null;
		}
		String key = directive.substring(0, valueSeparator).strip();
		if (key.length() >= 2
				&& (key.startsWith("\"") && key.endsWith("\"")
				|| key.startsWith("'") && key.endsWith("'"))) {
			key = key.substring(1, key.length() - 1);
		}
		if (!key.equals("uses")) {
			return null;
		}

		String value = directive.substring(valueSeparator + 1).strip();
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

	private boolean sameReferences(List<String> semanticReferences, List<String> lineReferences) {
		List<String> sortedSemantic = new ArrayList<>(semanticReferences);
		List<String> sortedLines = new ArrayList<>(lineReferences);
		sortedSemantic.sort(Comparator.naturalOrder());
		sortedLines.sort(Comparator.naturalOrder());
		return sortedSemantic.equals(sortedLines);
	}

	private List<String> findUsesReferences(Object node) {
		List<String> references = new ArrayList<>();
		if (node instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if ("uses".equals(String.valueOf(entry.getKey()))) {
					references.add(String.valueOf(entry.getValue()));
				}
				references.addAll(findUsesReferences(entry.getValue()));
			}
		} else if (node instanceof List<?> list) {
			for (Object value : list) {
				references.addAll(findUsesReferences(value));
			}
		}
		return references;
	}

	private boolean grantsContentsWrite(Object permissions) {
		return "write-all".equals(permissions) || hasContentsPermission(permissions, "write");
	}

	private boolean hasContentsPermission(Object permissions, String expected) {
		return expected.equals(value(yamlMap(permissions), "contents"));
	}

	private Object loadYaml(String contents) {
		LoaderOptions options = new LoaderOptions();
		options.setAllowDuplicateKeys(false);
		return new Yaml(new SafeConstructor(options)).load(contents);
	}

	private Map<?, ?> yamlMap(Object value) {
		return value instanceof Map<?, ?> map ? map : Map.of();
	}

	private Object value(Map<?, ?> map, String key) {
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (key.equals(String.valueOf(entry.getKey()))) {
				return entry.getValue();
			}
		}
		return null;
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

	private record WritePermission(Path workflow, String scope) {
	}
}
