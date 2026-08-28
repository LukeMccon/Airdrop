# AIRDR-26 LightKeeper Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task, and apply superpowers:test-driven-development for the configuration contract.

**Goal:** Add an opt-in LightKeeper real-Paper integration lane that verifies the default starter airdrop end to end while bumping Airdrop's Paper floor to 1.21.11.

**Architecture:** Keep LightKeeper in a pinned Maven sidecar and expose it through a standalone Gradle `lightkeeperTest` bridge. Provision the built Airdrop JAR and exact LuckPerms release into Paper 1.21.11, then run one Failsafe integration test. Preserve fast Gradle unit tests and isolate the real-server lane in CI.

**Tech Stack:** Java 21, Paper 1.21.11, Gradle 9, Maven 3.9.16, LightKeeper, JUnit, Maven Failsafe, LuckPerms.

---

### Task 1: Record the approved design and baseline

**Files:**
- Add: `docs/superpowers/specs/2026-08-28-airdr-26-lightkeeper-integration-design.md`
- Add: `docs/superpowers/plans/2026-08-28-airdr-26-lightkeeper-integration.md`

- [ ] Run `./gradlew test` before changing behavior and confirm the existing suite passes.
- [ ] Commit the approved design and executable plan with AIRDR-26 attribution.

### Task 2: Drive the integration configuration with a failing contract

**Files:**
- Add: `src/test/java/com/airdropmc/ci/LightkeeperIntegrationConfigurationTest.java`

- [ ] Add focused assertions for the full LightKeeper commit, Maven 3.9.16 checksum, Paper 1.21.11, LuckPerms Modrinth ID `b0mk8uS6`, required Maven repositories, Failsafe, the absolute Airdrop JAR property, Gradle task isolation, diagnostics paths, CI job, and release fallback.
- [ ] Run:

```bash
./gradlew test --tests com.airdropmc.ci.LightkeeperIntegrationConfigurationTest --rerun-tasks
```

- [ ] Confirm RED because the sidecar, Gradle bridge, CI lane, and 1.21.11 metadata do not yet exist.

### Task 3: Bump the Airdrop compatibility floor

**Files:**
- Modify: `build.gradle.kts`
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Modify: `.github/workflows/release.yml`
- Modify: `src/test/java/com/airdropmc/config/PluginYmlPermissionsTest.java`

- [ ] Change the Paper API dependency, `runServer`, and generated `api-version` to 1.21.11.
- [ ] Update current README badges, requirements, and test commands.
- [ ] Update the 4.0 changelog compatibility statement.
- [ ] Change the release workflow's Modrinth game-version fallback to 1.21.11.
- [ ] Update the generated-plugin assertion to 1.21.11.
- [ ] Run the generated-plugin test and confirm GREEN.

### Task 4: Add the pinned LightKeeper Maven sidecar

**Files:**
- Add: `lightkeeper/pom.xml`
- Add: `lightkeeper/mvnw`
- Add: `lightkeeper/mvnw.cmd`
- Add: `lightkeeper/.mvn/wrapper/maven-wrapper.properties`
- Add: `lightkeeper/src/test/resources/overlay/plugins/Airdrop/config.yml`

- [ ] Pin Maven 3.9.16 and its verified distribution SHA-256 in the wrapper.
- [ ] Add JitPack to dependency and plugin repositories and Paper's Maven repository to dependencies.
- [ ] Pin both LightKeeper framework and Maven plugin to commit `be585af08221c37bcbc8c9d7f5a40a27dbd2dff1`.
- [ ] Declare Paper API 1.21.11, the compatible JUnit engine, and Failsafe explicitly.
- [ ] Configure Paper, runtime manifest, server work root, stable plugin filenames, and Airdrop's user agent.
- [ ] Require an absolute `airdrop.jar.path` and provision exact LuckPerms Modrinth version `b0mk8uS6`.
- [ ] Add the minimal economy-off, particle-off, one-chicken test overlay without overriding `packages.yml`.

### Task 5: Add the Gradle bridge and CI lane

**Files:**
- Modify: `build.gradle.kts`
- Modify: `.github/workflows/ci.yml`

- [ ] Register `lightkeeperTest` as an always-run `Exec` task depending on `jar`.
- [ ] Invoke `lightkeeper/mvnw verify` from the sidecar directory and pass the built JAR as an absolute property.
- [ ] Keep `lightkeeperTest` detached from `test`, `check`, and `build`; have `clean` remove `lightkeeper/target`.
- [ ] Add a separate Ubuntu CI job with Java 21, Gradle and Maven caching, and `./gradlew --no-daemon lightkeeperTest`.
- [ ] Always upload Failsafe reports, LightKeeper reports, diagnostics, and runtime manifest while tolerating missing paths.

### Task 6: Implement the real starter-drop scenario

**Files:**
- Add: `lightkeeper/src/test/java/com/airdropmc/integration/StarterDropIT.java`

- [ ] Start Paper through LightKeeper and await Airdrop's economy-disabled startup log.
- [ ] Create a unique deterministic flat world and landing platform.
- [ ] Join a full-login bot at the landing X/Z coordinate.
- [ ] Grant `airdrop.drop` through LuckPerms, assert command success, and poll the live permission.
- [ ] Capture `PackageDropEvent` and `PackageLandEvent` across the plugin classloader.
- [ ] Execute `/airdrop starter`, assert exactly one drop event, then immediately teleport the bot away.
- [ ] Await exactly one landing event and the expected barrel block.
- [ ] Return the bot adjacent to the barrel, open it through player interaction, and assert slots 0-4 including bread amount 2.
- [ ] Close the inventory and clean bot/captures in `finally` blocks.

### Task 7: Make the contract GREEN and validate the real server

**Files:**
- Test: `src/test/java/com/airdropmc/ci/LightkeeperIntegrationConfigurationTest.java`
- Test: `lightkeeper/src/test/java/com/airdropmc/integration/StarterDropIT.java`

- [ ] Run the focused configuration test until it passes.
- [ ] Run `./gradlew lightkeeperTest` and diagnose any real-server failure from retained diagnostics.
- [ ] Keep fixes inside the approved architecture and rerun until the starter-drop scenario passes.

### Task 8: Verify, independently review, and integrate

**Files:**
- All AIRDR-26 files.

- [ ] Run fresh verification:

```bash
git diff --check
./gradlew test
./gradlew lightkeeperTest
./gradlew clean build
```

- [ ] Exercise release artifact verification with a valid release tag.
- [ ] Inspect the built JAR for generated `api-version: '1.21.11'` and absence of LightKeeper classes.
- [ ] Commit implementation changes with AIRDR-26 in the commit subject.
- [ ] Request exactly one independent read-only implementation review against the accepted design and verification evidence.
- [ ] Apply every valid finding, rerun affected checks, and commit the review fixes if needed.
- [ ] Fast-forward the original `4.0-beta` worktree to the verified AIRDR-26 branch without touching unrelated untracked files.
