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
