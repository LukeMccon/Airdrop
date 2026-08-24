# AIRDR-10 GitHub Actions Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade every external GitHub Action to its latest stable compatible release, pin all invocations to reviewed commits, enforce the policy in tests, automate controlled updates, and restrict release write permission to the GitHub publisher job.

**Architecture:** A focused JUnit policy test scans workflow source files without adding another workflow dependency. Workflow references use immutable release commits with readable inline versions, Dependabot proposes later GitHub Actions updates, and the release workflow applies read-only permission globally with one job-level write override.

**Tech Stack:** GitHub Actions YAML, GitHub Dependabot, Java 21, JUnit Jupiter 5, Gradle 9.

---

### Task 1: Enforce and apply immutable action pins

**Files:**
- Create: `src/test/java/com/airdropmc/ci/GitHubActionsSecurityTest.java`
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/develop-pr.yml`
- Modify: `.github/workflows/release.yml`

- [ ] **Step 1: Write the failing action-reference policy test**

Create `GitHubActionsSecurityTest.java` with a test that enumerates `.yml` and `.yaml` files in `.github/workflows`, ignores repository-local references beginning with `./`, and requires every other `uses:` reference to have a 40-character hexadecimal commit and an exact `vN[.N...]` version comment:

```java
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
```

- [ ] **Step 2: Run the focused test and verify the red state**

Run:

```bash
./gradlew test --tests com.airdropmc.ci.GitHubActionsSecurityTest.externalActionsUseCommitPinsWithReadableVersions
```

Expected: FAIL listing all 17 floating `uses:` lines from the three workflow files.

- [ ] **Step 3: Replace every floating action reference**

Apply these exact official release pins consistently across all repeated invocations:

```text
actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961 # v5.7.0
actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
actions/download-artifact@3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c # v8.0.1
gradle/actions/wrapper-validation@9c971963bec38e04b3d30dcc455b5382be2fdbfb # v6.3.0
gradle/actions/setup-gradle@9c971963bec38e04b3d30dcc455b5382be2fdbfb # v6.3.0
trufflesecurity/trufflehog@bcfcf73aaf4759d4dadc2783177c245a02792318 # v3.97.0
softprops/action-gh-release@3d0d9888cb7fd7b750713d6e236d1fcb99157228 # v3.0.2
Kira-NT/mc-publish@52307b03863581dec6b652b83e597aec02ebb075 # v3.3.1
```

The `mc-publish` owner changes from its former `Kir-Antipov` name to the canonical repository owner returned by the official release URL. Do not change workflow inputs or job structure.

- [ ] **Step 4: Run the focused test and verify the green state**

Run the same focused Gradle command. Expected: PASS with 17 external action invocations inspected and no violations.

- [ ] **Step 5: Commit immutable action references**

```bash
git add src/test/java/com/airdropmc/ci/GitHubActionsSecurityTest.java .github/workflows
git commit -m "AIRDR-10: pin updated GitHub Actions"
```

### Task 2: Restrict workflow token permissions

**Files:**
- Modify: `src/test/java/com/airdropmc/ci/GitHubActionsSecurityTest.java`
- Modify: `.github/workflows/develop-pr.yml`
- Modify: `.github/workflows/release.yml`

- [ ] **Step 1: Add failing least-privilege tests**

Add the static `assertEquals` import and these methods to `GitHubActionsSecurityTest`:

```java
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
```

- [ ] **Step 2: Run the two tests and verify the red state**

Run:

```bash
./gradlew test --tests 'com.airdropmc.ci.GitHubActionsSecurityTest.*Contents*'
```

Expected: FAIL because `develop-pr.yml` has no top-level read permission and `release.yml` grants write permission at workflow scope.

- [ ] **Step 3: Apply least-privilege permission blocks**

Add this top-level block to `develop-pr.yml` and replace the release workflow's top-level write value with read:

```yaml
permissions:
  contents: read
```

Add this block directly under `publish-github` after `needs: build`:

```yaml
    permissions:
      contents: write
```

- [ ] **Step 4: Run the focused tests and full policy class**

Run:

```bash
./gradlew test --tests com.airdropmc.ci.GitHubActionsSecurityTest
```

Expected: PASS.

- [ ] **Step 5: Commit least-privilege permissions**

```bash
git add src/test/java/com/airdropmc/ci/GitHubActionsSecurityTest.java .github/workflows/develop-pr.yml .github/workflows/release.yml
git commit -m "AIRDR-10: limit release write permission"
```

### Task 3: Configure controlled action updates

**Files:**
- Modify: `src/test/java/com/airdropmc/ci/GitHubActionsSecurityTest.java`
- Create: `.github/dependabot.yml`

- [ ] **Step 1: Add the failing Dependabot policy test**

Add this method to `GitHubActionsSecurityTest`:

```java
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
```

- [ ] **Step 2: Run the focused test and verify the red state**

Run:

```bash
./gradlew test --tests com.airdropmc.ci.GitHubActionsSecurityTest.dependabotProposesControlledGitHubActionUpdates
```

Expected: FAIL with `Expected .github/dependabot.yml`.

- [ ] **Step 3: Add the Dependabot configuration**

Create `.github/dependabot.yml`:

```yaml
version: 2
updates:
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 5
```

- [ ] **Step 4: Run the focused test and full policy class**

Run:

```bash
./gradlew test --tests com.airdropmc.ci.GitHubActionsSecurityTest
```

Expected: PASS.

- [ ] **Step 5: Commit dependency automation**

```bash
git add .github/dependabot.yml src/test/java/com/airdropmc/ci/GitHubActionsSecurityTest.java
git commit -m "AIRDR-10: automate action pin updates"
```

### Task 4: Verify the complete change

**Files:**
- Verify: `.github/workflows/ci.yml`
- Verify: `.github/workflows/develop-pr.yml`
- Verify: `.github/workflows/release.yml`
- Verify: `.github/dependabot.yml`
- Verify: `src/test/java/com/airdropmc/ci/GitHubActionsSecurityTest.java`

- [ ] **Step 1: Run the complete test suite**

```bash
./gradlew test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run the clean build**

```bash
./gradlew clean build
```

Expected: BUILD SUCCESSFUL and a plugin JAR under `build/libs/`.

- [ ] **Step 3: Inspect action references and repository hygiene**

```bash
rg -n '^\s*uses:' .github/workflows
git diff --check github/4.0-beta...HEAD
git status --short
```

Expected: 17 external lines, each with a 40-character SHA and version comment; no whitespace errors; clean status.

### Task 5: Deliver, review, and integrate AIRDR-10

**Files:**
- No repository file changes expected unless review finds an actionable defect.

- [ ] **Step 1: Push and open the linked PR**

```bash
git push -u github chore/airdr-10-pin-actions
gh pr create --base 4.0-beta --head chore/airdr-10-pin-actions --title "AIRDR-10: harden GitHub Actions dependencies" --body-file <prepared-pr-body>
```

The PR body must summarize action upgrades, pin enforcement, Dependabot, least-privilege permissions, validation, and include `Closes AIRDR-10`.

- [ ] **Step 2: Run bounded review-comment follow-up**

Invoke `pr-comment-autopilot` for the required 30-minute AIRDR-10 review window. Evaluate each comment technically before changing code.

- [ ] **Step 3: Request independent agent review**

Give a fresh agent the issue requirements, `github/4.0-beta...HEAD` diff, action release evidence, and validation results. Require findings by severity with file and line references, or an explicit no-findings result.

- [ ] **Step 4: Address validated findings and re-verify**

Use `superpowers:receiving-code-review` before any review-driven edits. Repeat the focused policy class, complete test suite, clean build, and diff checks after changes.

- [ ] **Step 5: Merge only after all gates pass**

Merge the approved PR into `4.0-beta`, update the local target branch without discarding its existing commits, verify the remote merge, and move AIRDR-10 to Done in Plane.
