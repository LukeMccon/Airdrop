# AIRDR-1 Package Save Integrity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make package create, update, and delete publish live state and report success only after `packages.yml` has been safely replaced.

**Architecture:** Package mutations create a detached `YamlConfiguration`, change that candidate, and pass it to a boolean persistence boundary in `AbstractConfig`. The persistence boundary writes a uniquely named sibling temporary file, replaces the target, and only then publishes the candidate; callers retain their current GUI or package state and show a localized error when persistence returns `false`.

**Tech Stack:** Java 21, Paper API configuration classes, Java NIO files, JUnit Jupiter, Mockito, MockBukkit, Gradle

---

### Task 1: Record the approved concurrency boundary

**Files:**
- Modify: `docs/superpowers/specs/2026-08-22-airdr-1-package-save-integrity-design.md`

- [x] **Step 1: Add stale editors to the non-goals**

Add this bullet under `Non-Goals`:

```markdown
- Detecting stale package editors; if two operators edit the same package, the last successful save wins.
```

- [x] **Step 2: Verify the spec states the agreed scope**

Run: `rg -n "stale package editors|last successful save wins" docs/superpowers/specs/2026-08-22-airdr-1-package-save-integrity-design.md`

Expected: one matching non-goal.

### Task 2: Add a transactional configuration save boundary

**Files:**
- Create: `src/test/java/com/airdropmc/config/AbstractConfigPersistenceTest.java`
- Modify: `src/main/java/com/airdropmc/config/AbstractConfig.java`

- [x] **Step 1: Write the failing persistence tests**

Create a test-only `TestConfig` subclass and tests that use `@TempDir`. The success test must construct a candidate `YamlConfiguration`, call `saveConfig(candidate)`, and assert that the target contains the candidate, `getConfig()` returns the candidate instance, and no `*.tmp` sibling remains. The failure test must use a `FileConfiguration` whose `save(File)` throws `IOException`, then assert `false`, unchanged target contents, unchanged live config identity, and no remaining temp file.

Key assertions:

```java
assertTrue(config.saveConfig(candidate));
assertSame(candidate, config.getConfig());
assertEquals("new", YamlConfiguration.loadConfiguration(target.toFile()).getString("value"));

FileConfiguration failedCandidate = mock(FileConfiguration.class);
doThrow(new IOException("forced failure")).when(failedCandidate).save(any(File.class));
assertFalse(config.saveConfig(failedCandidate));
assertSame(originalConfig, config.getConfig());
assertEquals(originalFileContents, Files.readString(target));
```

- [x] **Step 2: Run the persistence test and verify RED**

Run: `./gradlew test --tests com.airdropmc.config.AbstractConfigPersistenceTest`

Expected: compilation fails because `saveConfig(FileConfiguration)` does not exist and `saveConfig()` does not return a boolean.

- [x] **Step 3: Implement the minimal staged save**

Change the public API to:

```java
public boolean saveConfig() {
	return saveConfig(config);
}

public boolean saveConfig(FileConfiguration candidate) {
	// create sibling temp, candidate.save(temp), move with atomic/fallback replacement,
	// set config = candidate only after the move, clean temp, log once, return outcome
}
```

Use `Files.createTempFile`, `Files.move` with `StandardCopyOption.ATOMIC_MOVE` and `REPLACE_EXISTING`, catch `AtomicMoveNotSupportedException` to retry with `REPLACE_EXISTING`, and delete the temporary file in cleanup. Return `false` without publishing when the config or file is unavailable or an `IOException` occurs.

- [x] **Step 4: Run the persistence test and verify GREEN**

Run: `./gradlew test --tests com.airdropmc.config.AbstractConfigPersistenceTest`

Expected: both tests pass.

### Task 3: Stage package mutations before persistence

**Files:**
- Modify: `src/test/java/com/airdropmc/packages/PackageManagerMutationTest.java`
- Modify: `src/main/java/com/airdropmc/packages/PackageManager.java`

- [x] **Step 1: Update success tests and add failing mutation tests**

Stub `packagesConfig.saveConfig(any(FileConfiguration.class))` to return `true` for existing success tests and assert each mutation returns `true`. Capture the candidate passed to `saveConfig` to verify the staged value.

Add one failed-save test each for update, create, and delete. Stub candidate saves to return `false`, save `config.saveToString()` before the operation, and assert:

```java
assertFalse(PackageManager.updatePackageInventory("starter", changedItems));
assertEquals(List.of(), PackageManager.get("starter").getItems());
assertEquals(originalYaml, config.saveToString());

assertFalse(PackageManager.createPackage(newPackage));
assertThrows(PackageNotFoundException.class, () -> PackageManager.get("newpkg"));
assertEquals(originalYaml, config.saveToString());

assertFalse(PackageManager.deletePackage("starter"));
assertDoesNotThrow(() -> PackageManager.get("starter"));
assertEquals(originalYaml, config.saveToString());
```

For failed create and delete, set a mocked `PackagesGui` and verify `initializeItems()` is never invoked.

- [x] **Step 2: Run the mutation tests and verify RED**

Run: `./gradlew test --tests com.airdropmc.packages.PackageManagerMutationTest`

Expected: compilation/assertion failures because mutation methods return `void`, mutate live state before saving, and call the no-argument save method.

- [x] **Step 3: Implement staged package candidates**

Add a private helper that serializes the current `FileConfiguration` with `saveToString()` and loads it into a new `YamlConfiguration`. Change mutation signatures to boolean:

```java
public static boolean updatePackageInventory(String packageName, List<ItemStack> items)
public static boolean createPackage(Package pkg)
public static boolean deletePackage(String packageName)
```

For each mutation, validate against live state, change only the staged configuration, call `packagesConfig.saveConfig(staged)`, and return `false` immediately on failure. Only after success should update change the live package items, create insert the package, delete remove the package, and create/delete refresh the package listing GUI.

- [x] **Step 4: Run the mutation tests and verify GREEN**

Run: `./gradlew test --tests com.airdropmc.packages.PackageManagerMutationTest`

Expected: all mutation tests pass.

### Task 4: Keep operators in failed editors and report the failure

**Files:**
- Create: `src/test/java/com/airdropmc/packages/PackagePersistenceFailureFeedbackTest.java`
- Modify: `src/main/java/com/airdropmc/packages/PackageGui.java`
- Modify: `src/main/java/com/airdropmc/packages/CreatePackageGui.java`
- Modify: `src/main/java/com/airdropmc/controllers/PackageController.java`
- Modify: `src/main/java/com/airdropmc/lang/MessageKey.java`
- Modify: `src/main/resources/lang/en.yml`

- [x] **Step 1: Write failing operator-flow tests**

Use MockBukkit with a mocked `PackagesConfig` whose candidate save returns `false`. Verify:

- Editing an existing package leaves the chest inventory open, sends text containing `No changes were made`, and does not send the saved-success text.
- Creating a package leaves the chest inventory open, sends the persistence error, and does not send the created-success text.
- Deleting by command retains the package, sends the persistence error, and does not send the deleted-success text.

- [x] **Step 2: Run the feedback tests and verify RED**

Run: `./gradlew test --tests com.airdropmc.packages.PackagePersistenceFailureFeedbackTest`

Expected: compilation or assertion failures because callers ignore save results and the error key does not exist.

- [x] **Step 3: Implement failure feedback and success-only close behavior**

Add:

```java
ERROR_PACKAGE_SAVE_FAILED(
		"errors.package-save-failed",
		"Could not save package changes. No changes were made. Check the server log."),
```

Add the same default to `src/main/resources/lang/en.yml`. In both GUI save methods, send this key and return when the manager returns `false`; restore inventory and close only on `true`. In `deletePackageCommand`, send this key and suppress the deleted-success message when deletion returns `false`. Change controller convenience methods to return the manager's boolean result so programmatic callers can observe persistence failure.

- [x] **Step 4: Run the feedback tests and verify GREEN**

Run: `./gradlew test --tests com.airdropmc.packages.PackagePersistenceFailureFeedbackTest`

Expected: all operator-flow tests pass.

### Task 5: Verify and commit AIRDR-1

**Files:**
- Verify all files changed above

- [x] **Step 1: Run focused AIRDR-1 tests**

Run: `./gradlew test --tests com.airdropmc.config.AbstractConfigPersistenceTest --tests com.airdropmc.packages.PackageManagerMutationTest --tests com.airdropmc.packages.PackagePersistenceFailureFeedbackTest`

Expected: all focused tests pass.

- [x] **Step 2: Run the full test suite and build**

Run: `./gradlew clean build`

Expected: `BUILD SUCCESSFUL` with zero failing tests.

- [x] **Step 3: Review the diff and acceptance criteria**

Run: `git diff --check && git diff --stat && git status --short`

Expected: no whitespace errors; only AIRDR-1 implementation, tests, language, plan, and spec files are changed.

Confirm create/update/delete failure preserves both live package and YAML state, operator success is suppressed, editor failure stays open, temporary files are cleaned, and last-write-wins remains outside this issue.

- [x] **Step 4: Commit locally**

Run:

```bash
git add -f docs/superpowers/plans/2026-08-22-airdr-1-package-save-integrity.md docs/superpowers/specs/2026-08-22-airdr-1-package-save-integrity-design.md
git add src/main src/test
git commit -m "AIRDR-1: preserve packages when saves fail" -m "Closes AIRDR-1"
```

Expected: a local commit is created. Do not push and do not create a pull request.
