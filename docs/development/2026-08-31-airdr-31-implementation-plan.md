# AIRDR-31 Developer Experience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` for the assigned worktree and `superpowers:test-driven-development` for every behavior change. The program controller owns integration checkpoints. Because `$dri` permits exactly one independent implementation review, workers must not start per-task review agents; the controller starts the sole read-only review after the complete stack is green.

**Goal:** Ship a locally integrated Airdrop 5.0 developer-experience release with a small immutable extension API, typed drop outcomes and events, useful administrator diagnostics, canonical Modrinth-ready documentation, aligned test dependencies, and a repeatable real-server consumer gate.

**Architecture:** Keep one `program/airdr-31-developer-experience` integration branch based on the updated local `develop`. Cut each isolated worktree from the current program tip, implement its Plane item with red-green-refactor, commit with the AIRDR key, verify it, then fast-forward the program branch before cutting the next dependent worktree. `com.airdropmc.api` is the only supported boundary; an internal implementation adapts existing controllers, package snapshots, economy sessions, admission leases, and crate indexes. Repository Markdown remains the canonical docs source and local scripts prove the exact Modrinth payload without publishing it.

**Tech Stack:** Java 21, Paper 1.21.11, Gradle Kotlin DSL, JUnit Platform/Jupiter, MockBukkit, Maven/LightKeeper, Bukkit `ServicesManager`, GitHub Actions definitions executed only as local source changes, POSIX shell, Modrinth v2 API contract.

---

## Execution rules

- Work only in the named worktree. Do not push, open a PR, merge through GitHub, publish Modrinth content, alter the wiki, or create a release/tag.
- Before editing, move the corresponding Plane item to In Progress. After local acceptance is proven, add a concise evidence comment and move it to Done only if none of its criteria require an unperformed remote mutation.
- For every behavior change, write the focused test first, run it and retain the expected failure, implement the smallest change, rerun the focused test, then run `./gradlew test` before committing.
- Use tabs and same-line braces in Java. Do not expose existing internal `Crate`, `Package`, controller, mutable config, or registry types from `com.airdropmc.api`.
- Commit subjects use `AIRDR-N: imperative summary`. Never combine unrelated Plane items in one commit.
- After integrating a worktree, run `git diff --check`, the task-specific verification, and `./gradlew test` from the program worktree. Delete an integrated worktree only after those checks pass.
- The final program verification is followed by exactly one independent, read-only DRI implementation review. Evaluate that report with `superpowers:receiving-code-review`, apply validated fixes on the program branch, and rerun affected plus full checks. Do not request a second final review.

## Worktree and commit sequence

| Order | Worktree branch | Plane item | Local commit intent |
|---:|---|---|---|
| 1 | `feature/airdr-44-test-matrix` | AIRDR-44 | align Paper, MockBukkit, and JUnit resolution |
| 2 | `feature/airdr-35-optional-luckperms` | AIRDR-35 | make LuckPerms an isolated soft integration |
| 3 | `feature/airdr-32-onboarding` | AIRDR-32 | make a fresh free drop and command discovery work |
| 4 | `feature/airdr-36-api-models` | AIRDR-36 | add immutable supported API models |
| 5 | `feature/airdr-37-api-service` | AIRDR-37 | register a lifecycle-safe Bukkit service |
| 6 | `feature/airdr-38-drop-outcomes` | AIRDR-38 | return correlated typed async outcomes |
| 7 | `feature/airdr-39-lifecycle-events` | AIRDR-39 | add cancellable pre-events and immutable post-events |
| 8 | `feature/airdr-40-queries-registry` | AIRDR-40 | publish registry changes and active-drop indexes |
| 9 | `feature/airdr-42-version-policy` | AIRDR-42 | establish API 1.0 compatibility/version gates |
| 10 | `feature/airdr-43-diagnostics` | AIRDR-43 | expose status and bounded debug diagnostics |
| 11 | `feature/airdr-33-modrinth-source` | AIRDR-33 | add canonical docs source and publisher lever |
| 12 | `feature/airdr-34-admin-docs` | AIRDR-34 | complete and contract-test operating documentation |
| 13 | `feature/airdr-41-api-publication` | AIRDR-41 | publish sources/Javadocs and compile a consumer |
| 14 | `feature/airdr-46-provenance` | AIRDR-46 | lock dependency and artifact provenance |
| 15 | `feature/airdr-45-lightkeeper` | AIRDR-45 | make the real-server consumer/release gate repeatable |

## Task 1: AIRDR-44 — align the automated compatibility matrix

**Files:**

- Modify: `build.gradle.kts`
- Modify: `README.md`
- Create: `src/test/java/com/airdropmc/build/CompatibilityMatrixTest.java`
- Modify: the 25 tests returned by `rg -l '^import be\.seeseemelk\.mockbukkit' src/test/java` (mechanical import migration to `org.mockbukkit.mockbukkit`)

**Steps:**

1. Create the worktree from the current program tip and record the baseline:

   ```bash
   git worktree add .worktrees/airdr-44-test-matrix -b feature/airdr-44-test-matrix program/airdr-31-developer-experience
   cd .worktrees/airdr-44-test-matrix
   ./gradlew test
   ./gradlew dependencyInsight --dependency paper-api --configuration testRuntimeClasspath
   ./gradlew dependencyInsight --dependency junit-jupiter-api --configuration testRuntimeClasspath
   ```

2. Add `CompatibilityMatrixTest` assertions for generated plugin metadata (`api-version: 1.21.11`), the source-controlled compatibility table, and the existence of a Gradle verification task named `verifyDependencyMatrix`. Run:

   ```bash
   ./gradlew test --tests com.airdropmc.build.CompatibilityMatrixTest
   ```

   Expected: fail because the table/task do not yet exist.

3. In `build.gradle.kts`, import `org.junit:junit-bom:6.1.3` once and remove component-level JUnit versions; select maintained `org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3`; keep Paper compile-only and also declare Paper explicitly for tests at `1.21.11-R0.1-SNAPSHOT`. Mechanically migrate all 25 MockBukkit imports from `be.seeseemelk.mockbukkit` to `org.mockbukkit.mockbukkit`. Add `verifyDependencyMatrix` which resolves both test compile/runtime classpaths and fails unless Paper resolves to 1.21.11 and all `org.junit*` modules resolve to the selected 6.1.3 BOM line. Wire it into `check`, which existing `build` workflow commands already execute; do not modify the secret-scanning-only develop workflow.

4. Add an explicit README table for the current source version, extension API `unavailable`, Paper `1.21.11`, Java `21`, and automated lanes `unit + LightKeeper`. Remove every `1.21.11+`, `latest`, or otherwise unbounded support claim. AIRDR-42 updates the same table to Airdrop 5/API 1 only after those artifacts exist.

5. Verify the focused and complete behavior:

   ```bash
   ./gradlew verifyDependencyMatrix
   ./gradlew dependencyInsight --dependency paper-api --configuration testCompileClasspath
   ./gradlew dependencyInsight --dependency paper-api --configuration testRuntimeClasspath
   ./gradlew dependencyInsight --dependency junit-jupiter-api --configuration testRuntimeClasspath
   ./gradlew clean test build
   git diff --check
   ```

   Confirm dependency insight reports Paper 1.21.11 and one intentional JUnit line. Commit:

   ```bash
   git add -A && git commit -m "AIRDR-44: align automated compatibility matrix"
   ```

## Task 2: AIRDR-35 — isolate LuckPerms as an optional convenience

**Files:**

- Modify: `build.gradle.kts`
- Modify: `src/main/java/com/airdropmc/Airdrop.java`
- Modify: `src/main/java/com/airdropmc/helpers/PermissionsHelper.java`
- Create: `src/main/java/com/airdropmc/integrations/LuckPermsIntegration.java`
- Create: `src/main/java/com/airdropmc/integrations/OptionalIntegrations.java`
- Modify: `src/test/java/com/airdropmc/config/PluginYmlPermissionsTest.java`
- Modify: `src/test/java/com/airdropmc/helpers/PermissionsHelperTest.java`
- Create: `src/test/java/com/airdropmc/AirdropOptionalDependenciesTest.java`
- Create: `src/test/java/com/airdropmc/integrations/LuckPermsIntegrationTest.java`
- Create: `src/test/java/com/airdropmc/integrations/LuckPermsIsolationTest.java`
- Modify: `src/test/java/com/airdropmc/AirdropListenerRegistrationTest.java`
- Modify: `src/test/java/com/airdropmc/economy/AirdropEconomyLifecycleTest.java`
- Modify: `README.md`

**Steps:**

1. Add a plugin-descriptor test requiring `LuckPerms` under `softdepend`, never `depend`. Add an actual plugin startup test with no registered LuckPerms plugin/service and prove Bukkit permission nodes still authorize a package. Remove fake LuckPerms setup from existing startup/economy tests. Add an isolated integration test which supplies a mocked LuckPerms service and proves `airdrop-admin`/`airdrop-user` group-node setup is retained. Run the new tests and observe the hard-dependency/direct-reference failures.

2. Move every `net.luckperms.*` import and group-creation operation into `LuckPermsIntegration`. `OptionalIntegrations.initialize(Server)` must check the plugin/service by name before constructing that class and catch `LinkageError` as a degraded optional result. `Airdrop` and `PermissionsHelper` must contain no LuckPerms type in fields, signatures, bytecode descriptors, or imports; Bukkit `Player#hasPermission` remains authoritative.

3. Change generated metadata to `softDepend = listOf("LuckPerms", "Vault")`; retain `compileOnly("net.luckperms:api:5.4")` solely to compile the isolated adapter and `testRuntimeOnly` so the present-integration unit tests can load the adapter. Unit-test "absence" means no registered plugin/service plus bytecode isolation; the genuinely classpath-absent real-server proof belongs to AIRDR-45. Update README compatibility/dependency text to make LuckPerms optional and describe retained convenience groups.

4. Verify both paths and class isolation:

   ```bash
   ./gradlew test --tests com.airdropmc.AirdropOptionalDependenciesTest --tests com.airdropmc.integrations.LuckPermsIntegrationTest --tests com.airdropmc.integrations.LuckPermsIsolationTest --tests com.airdropmc.helpers.PermissionsHelperTest --tests com.airdropmc.config.PluginYmlPermissionsTest
   ./gradlew test
   git diff --check
   ```

   `LuckPermsIsolationTest` must inspect compiled constant pools and prove `Airdrop`, `PermissionsHelper`, and `OptionalIntegrations` contain no `net/luckperms/` reference while the isolated adapter does.

5. Commit:

   ```bash
   git add -A && git commit -m "AIRDR-35: make LuckPerms integration optional"
   ```

## Task 3: AIRDR-32 — make first-run onboarding and command discovery correct

**Files:**

- Modify: `src/main/java/com/airdropmc/config/ConfigCoordinator.java`
- Modify: `src/main/java/com/airdropmc/commands/CmdAirdrop.java`
- Modify: `src/main/java/com/airdropmc/commands/DropCommand.java`
- Modify: `src/main/java/com/airdropmc/AirdropCommandNames.java`
- Modify: `src/main/java/com/airdropmc/lang/MessageKey.java`
- Modify: `src/main/java/com/airdropmc/Airdrop.java`
- Modify: `src/main/resources/lang/en.yml`
- Modify: `src/main/resources/packages.yml`
- Modify: `README.md`
- Modify: `src/test/java/com/airdropmc/config/ConfigCoordinatorTest.java`
- Create: `src/test/java/com/airdropmc/FirstRunOnboardingTest.java`
- Modify: `src/test/java/com/airdropmc/commands/CmdAirdropArgumentCountTest.java`
- Create: `src/test/java/com/airdropmc/commands/CmdAirdropHelpTest.java`
- Modify: `src/test/java/com/airdropmc/commands/DropCommandPackageIdentityTest.java`
- Modify: `src/test/java/com/airdropmc/commands/CmdAirdropLifecycleSafetyTest.java`
- Modify: `src/test/java/com/airdropmc/helpers/ChatHandlerSenderRoutingTest.java`

**Steps:**

1. Add failing tests proving a missing packages file provisions exactly one `starter` package at price `0.0`, an existing registry/starter is not overwritten or duplicated, actual plugin startup with no LuckPerms/economy reaches ready, and an operator can request the free starter. Add command tests proving `/airdrop` and generic malformed input return `true` after rendering localized permission-aware help and package-not-found feedback never points ordinary players at the admin browser. Add version-output expectations for plugin version, extension API availability, Paper target, Java runtime, and `https://modrinth.com/plugin/airdrop`.

2. Change only the starter default price to zero and make the shipped `packages.yml` an explicit `packages: {}` document. Keep first-start creation idempotent.

3. Add concise localized help entries for player commands and admin-only commands. Route zero arguments and generic malformed forms through help before readiness gating, while preserving targeted `package create/delete` feedback and permission filtering. Point missing-package feedback to root tab completion rather than `/airdrop packages`. Rename the existing Bukkit metadata getter/output to Paper compatibility and render extension API as `unavailable` until AIRDR-42 creates the supported boundary. Use stable named placeholders for all four version signals plus the canonical docs URL. Correct README's free quick start to use the provisioned starter exactly once and document paid setup separately.

4. Run:

   ```bash
   ./gradlew test --tests com.airdropmc.config.ConfigCoordinatorTest --tests com.airdropmc.FirstRunOnboardingTest --tests 'com.airdropmc.commands.*' --tests com.airdropmc.helpers.ChatHandlerSenderRoutingTest
   ./gradlew test
   git diff --check
   ```

5. Commit:

   ```bash
   git add -A && git commit -m "AIRDR-32: correct first-run onboarding"
   ```

## Task 4: AIRDR-36 — define immutable supported API models

**Files:**

- Create: `src/main/java/com/airdropmc/api/package-info.java`
- Create: `src/main/java/com/airdropmc/api/AirdropPackage.java`
- Create: `src/main/java/com/airdropmc/api/AirdropView.java`
- Create: `src/main/java/com/airdropmc/api/DropRequestOptions.java`
- Create: `src/main/java/com/airdropmc/api/ResolvedDropSettings.java`
- Create: `src/main/java/com/airdropmc/api/DropRequestDescriptor.java`
- Create: `src/main/java/com/airdropmc/api/ResolvedDropContext.java`
- Create: `src/main/java/com/airdropmc/api/WorldPosition.java`
- Create: `src/main/java/com/airdropmc/api/DropSource.java`
- Create: `src/main/java/com/airdropmc/api/DropState.java`
- Create: `src/main/java/com/airdropmc/api/FallingAirdropView.java`
- Create: `src/main/java/com/airdropmc/api/LandedAirdropView.java`
- Modify: `src/main/java/com/airdropmc/config/DropOptions.java`
- Modify: `src/main/java/com/airdropmc/controllers/DropController.java`
- Modify: `src/main/java/com/airdropmc/Crate.java`
- Modify: `src/main/java/com/airdropmc/ParachuteSystem.java`
- Modify: extension-looking implementation types to add `@ApiStatus.Internal`
- Create: `src/test/java/com/airdropmc/api/ApiModelImmutabilityTest.java`
- Create: `src/test/java/com/airdropmc/api/ApiModelValidationTest.java`
- Create: `src/test/java/com/airdropmc/api/SupportedApiBoundaryTest.java`
- Modify: `src/test/java/com/airdropmc/config/DropOptionsTest.java`
- Modify: `src/test/java/com/airdropmc/CrateLandingLifecycleTest.java`
- Modify: `src/test/java/com/airdropmc/ParachuteSystemTest.java`

**Public shape:**

```java
public final class AirdropPackage {
	public String name();
	public BigDecimal price();
	public List<ItemStack> items();
}

public final class DropRequestOptions {
	public static DropRequestOptions defaults();
	public OptionalInt chickenCount();
	public OptionalDouble fallingSpeed();
	public OptionalInt dropHeight();
	public Optional<Boolean> landingEffects();
	public Optional<Boolean> continuousEffects();
	public Optional<Boolean> flareEffects();
	public Optional<Boolean> smokeEnabled();
	public OptionalInt smokeHeight();
}

public record ResolvedDropSettings(
		int chickenCount,
		double fallingSpeed,
		int dropHeight,
		boolean landingEffects,
		boolean continuousEffects,
		boolean flareEffects,
		boolean smokeEnabled,
		int smokeHeight,
		Duration requestCooldown,
		int maxFalling,
		int maxLanded,
		Duration landedLifetime) { }
```

`DropRequestDescriptor` contains request UUID, source, optional player UUID, requested package name, and a detached requested position. `ResolvedDropContext` adds the package snapshot, spawn and landing positions, and resolved settings. `AirdropView` exposes only IDs, phase, positions, package identity, source, optional player/request/recovery data, and primitive terminal state; it never exposes a live Bukkit entity/block or internal crate.

**Steps:**

1. Write tests which mutate constructor inputs, returned lists, returned `ItemStack`s, and returned Bukkit locations, then assert the model remains unchanged. Add constructor tests for nulls, blank package names, invalid prices/settings, mismatched worlds, and illegal view state. Add a reflection test which fails if a public `com.airdropmc.api` signature mentions `com.airdropmc.Crate`, `com.airdropmc.config`, `com.airdropmc.controllers`, `com.airdropmc.packages.Package`, mutable registries, or public setters.

2. Implement the value types as final classes/records with validated constructors. Clone `ItemStack` and `Location` inputs and outputs; use `List.copyOf`/`Map.copyOf` only after cloning mutable elements. Use `WorldPosition` for pure off-thread position reads. Document that methods accepting or returning Bukkit types are primary-thread-only.

3. Add an internal conversion boundary, not a public constructor overload, from `Package`/`DropOptions` to the supported snapshots. `DropOptions.resolve(DropLimitSettings)` resolves every nullable visual/motion option plus admission cooldown/capacities and landed lifetime exactly once. `Crate` and `ParachuteSystem` store only `ResolvedDropSettings`; landing must not reread `ConfigKeys`. Retain old constructors only as internal/deprecated adapters that immediately resolve.

4. Mark `Airdrop`, `Crate`, `DropOptions`, `DropController`, `Package`, `PackageManager`, `CrateManager`, and raw config wrappers with `@ApiStatus.Internal`. The supported reflection test must exclude `com.airdropmc.internal` completely and treat every public type under `com.airdropmc.api` except the explicitly named `api.event` classes as supported.

5. Verify:

   ```bash
   ./gradlew test --tests 'com.airdropmc.api.ApiModel*' --tests com.airdropmc.api.SupportedApiBoundaryTest --tests com.airdropmc.config.DropOptionsTest --tests com.airdropmc.CrateLandingLifecycleTest --tests com.airdropmc.ParachuteSystemTest
   ./gradlew test
   git diff --check
   ```

6. Commit:

   ```bash
   git add -A && git commit -m "AIRDR-36: define immutable extension API models"
   ```

## Task 5: AIRDR-37 — register a lifecycle-safe Bukkit service

**Files:**

- Create: `src/main/java/com/airdropmc/api/AirdropApi.java`
- Create: `src/main/java/com/airdropmc/api/AirdropVersions.java`
- Create: `src/main/java/com/airdropmc/api/AirdropStatus.java`
- Create: `src/main/java/com/airdropmc/api/ReadinessState.java`
- Create: `src/main/java/com/airdropmc/api/EconomyState.java`
- Create: `src/main/java/com/airdropmc/internal/api/DefaultAirdropApi.java`
- Create: `src/main/java/com/airdropmc/internal/api/AirdropServiceLifecycle.java`
- Modify: `src/main/java/com/airdropmc/Airdrop.java`
- Modify: `src/main/java/com/airdropmc/config/ConfigCoordinator.java`
- Modify: `src/main/java/com/airdropmc/helpers/CrateManager.java`
- Create: `src/test/java/com/airdropmc/api/AirdropApiServiceTest.java`
- Create: `src/test/java/com/airdropmc/api/AirdropApiReadinessTest.java`
- Create: `src/test/java/com/airdropmc/api/AirdropApiThreadContractTest.java`
- Modify: `src/test/java/com/airdropmc/AirdropListenerRegistrationTest.java`
- Modify: `src/test/java/com/airdropmc/config/ConfigCoordinatorTest.java`
- Modify: `src/test/java/com/airdropmc/economy/AirdropEconomyLifecycleTest.java`
- Modify: `src/test/java/com/airdropmc/commands/CmdAirdropLifecycleSafetyTest.java`

**Service contract:**

```java
public interface AirdropApi {
	CompletionStage<AirdropApi> readiness();
	ReadinessState state();
	AirdropVersions versions();
	AirdropStatus status();
	List<AirdropPackage> listPackages();
	Optional<AirdropPackage> findPackage(String name);
	DropHandle requestPlayerDrop(Player player, String packageName, DropRequestOptions options);
	DropHandle requestSystemDrop(Location location, String packageName, DropRequestOptions options);
	Collection<AirdropView> activeDrops();
	Optional<AirdropView> findByRequestId(UUID requestId);
	Optional<AirdropView> findByCrateId(UUID crateId);
	Optional<AirdropView> findByFallingEntity(FallingBlock entity);
	Optional<AirdropView> findByLandedBlock(Block block);
}
```

Methods whose dependent types arrive in later tasks may initially return empty snapshots or typed `UNAVAILABLE` handles, but their public signatures land here and cannot expose implementation classes.

**Steps:**

1. Add failing MockBukkit lifecycle tests for: `ServicePriority.Normal` provider registration before async startup completes; `STARTING` state; readiness completion only after the primary-thread commit; service retrieval through `ServicesManager`; degraded ready without economy/LuckPerms; exceptional readiness plus plugin disable for fatal configuration failure; `STOPPING` plus failed incomplete readiness on disable; unregister of the exact provider; and safe repeated disable/stale registration replacement.

2. Implement `DefaultAirdropApi` as one plugin-owned provider outside the supported package. Its private backing `CompletableFuture` is never returned directly: `readiness()` returns `minimalCompletionStage()`. Register the provider before starting configuration, publish ready only from the scheduler's primary-thread commit, and unregister before existing cleanup.

3. Classify startup results at the boundary. Required config/language/package publication, main-thread scheduling, service registration, and catastrophic recovery scan failures are fatal—remove the current catch-and-warn around a catastrophic `CrateManager.recoverLoadedCrates()` failure. Missing economy provider, disabled economy, absent/failing LuckPerms, and individually purged malformed recovered crates are degraded status entries, not readiness failure. Change `ConfigCoordinator` dispatch rejection so the operation completes exceptionally and the queue advances/closes; it must never remain pending until plugin close.

4. Add primary-thread guards only on API methods that accept/return/inspect Bukkit objects. Permit `versions()`, `state()`, readiness attachment, and pure snapshot fields off-thread. Tests must show non-async continuations are not promised a particular executor while backing completions and Bukkit events originate on the primary thread.

5. Verify:

   ```bash
   ./gradlew test --tests 'com.airdropmc.api.AirdropApi*'
   ./gradlew test
   git diff --check
   ```

6. Commit:

   ```bash
   git add -A && git commit -m "AIRDR-37: register lifecycle-safe Airdrop service"
   ```

## Task 6: AIRDR-38 — return typed correlated drop outcomes

**Files:**

- Create: `src/main/java/com/airdropmc/api/DropHandle.java`
- Create: `src/main/java/com/airdropmc/api/DropSpawnResult.java`
- Create: `src/main/java/com/airdropmc/api/DropOutcome.java`
- Create: `src/main/java/com/airdropmc/api/DropRejection.java`
- Create: `src/main/java/com/airdropmc/api/DropRejectionReason.java`
- Create: `src/main/java/com/airdropmc/api/DeliveryStatus.java`
- Create: `src/main/java/com/airdropmc/api/PaymentStatus.java`
- Create: `src/main/java/com/airdropmc/internal/drop/DefaultDropHandle.java`
- Create: `src/main/java/com/airdropmc/internal/drop/DropRequestProcess.java`
- Create: `src/main/java/com/airdropmc/internal/drop/DropRequestCoordinator.java`
- Create: `src/main/java/com/airdropmc/internal/drop/DropSettingsResolver.java`
- Modify: `src/main/java/com/airdropmc/controllers/DropController.java`
- Modify: `src/main/java/com/airdropmc/paid/PaidDropSession.java`
- Modify: `src/main/java/com/airdropmc/Crate.java`
- Modify: `src/main/java/com/airdropmc/Airdrop.java`
- Modify: `src/main/java/com/airdropmc/commands/DropCommand.java`
- Create: `src/test/java/com/airdropmc/api/DropHandleTest.java`
- Create: `src/test/java/com/airdropmc/api/DropOutcomeValidationTest.java`
- Create: `src/test/java/com/airdropmc/api/DropRequestCoordinatorTest.java`
- Modify: `src/test/java/com/airdropmc/controllers/DropControllerEconomyFlowTest.java`
- Modify: `src/test/java/com/airdropmc/paid/PaidDropSessionTest.java`

**Steps:**

1. Add failing tests for a handle whose descriptor is always present, resolved context is optional, and spawn/terminal stages cannot be completed or cancelled by consumers. Cover unknown package, bad target, permission, sky, falling/landed/location/cooldown limits, shutting down, economy disabled, provider missing, insufficient funds, withdrawal failure, spawn failure, landing failure, timeout ambiguity, refund success/failure, shutdown, and duplicate/late callback suppression.

2. Make `DropHandle` expose immutable descriptor/context and minimal stage views:

   ```java
   public interface DropHandle {
	UUID requestId();
	DropRequestDescriptor descriptor();
	Optional<ResolvedDropContext> context();
	CompletionStage<DropSpawnResult> spawn();
	CompletionStage<DropOutcome> outcome();
   }
   ```

   Expected operational failures complete as `DropOutcome` values; programmer errors such as null input or off-thread Bukkit access fail immediately.

   Use sealed outcome variants rather than a nullable field bag: `Rejected` has descriptor, optional context, typed rejection, and payment; `Landed` has complete context, landed view, and payment; `Failed` has complete context plus only `FAILED`, `CANCELLED`, or `SHUTDOWN` delivery and payment. Variant constructors enforce valid payment/delivery combinations.

3. Refactor `DropController` behind `DropRequestCoordinator`: allocate request ID/descriptor first; resolve package, target, and options; acquire admission; perform payment; spawn; land; complete. Free player, paid player, and explicitly unpaid system calls use the same state machine. Remove chat output from `PaidDropSession`; `DropCommand` translates stages/outcomes to localized messages. Keep old `DropController` void methods only as deprecated adapters to the coordinator.

4. Store delivery and payment independently. Enforce valid combinations in `DropOutcome`: free/system uses `NOT_APPLICABLE`; an uncharged rejection cannot say `CHARGED`; confirmed charged failures attempt one refund; ambiguous timeout/shutdown becomes `UNKNOWN` and is never automatically retried. Guard terminal completion and payment/refund side effects with an atomic/main-thread single-completion transition.

5. Marshal every economy callback to the primary thread before state mutation. Track all incomplete processes and complete them once on disable: affordability pending is uncharged/rejected, withdrawal pending is `UNKNOWN`, confirmed charged/falling is `SHUTDOWN` + `CHARGED`, and refund pending is `UNKNOWN`. Late callbacks cannot spawn/refund/complete twice.

6. Verify:

   ```bash
   ./gradlew test --tests com.airdropmc.api.DropHandleTest --tests com.airdropmc.api.DropOutcomeValidationTest --tests com.airdropmc.api.DropRequestCoordinatorTest --tests com.airdropmc.controllers.DropControllerEconomyFlowTest --tests com.airdropmc.paid.PaidDropSessionTest
   ./gradlew test
   git diff --check
   ```

7. Commit:

   ```bash
   git add -A && git commit -m "AIRDR-38: expose typed asynchronous drop outcomes"
   ```

## Task 7: AIRDR-39 — add integration-safe lifecycle events

**Files:**

- Create: `src/main/java/com/airdropmc/api/event/AbstractAirdropEvent.java`
- Create: `src/main/java/com/airdropmc/api/event/AirdropRequestEvent.java`
- Create: `src/main/java/com/airdropmc/api/event/AirdropSpawnedEvent.java`
- Create: `src/main/java/com/airdropmc/api/event/AirdropLandingAttemptEvent.java`
- Create: `src/main/java/com/airdropmc/api/event/AirdropLandedEvent.java`
- Create: `src/main/java/com/airdropmc/api/event/AirdropOutcomeEvent.java`
- Modify: `src/main/java/com/airdropmc/internal/drop/DropRequestProcess.java`
- Modify: `src/main/java/com/airdropmc/internal/drop/DropRequestCoordinator.java`
- Modify: `src/main/java/com/airdropmc/controllers/DropController.java`
- Modify: `src/main/java/com/airdropmc/listeners/FallingCrateListener.java`
- Modify: `src/main/java/com/airdropmc/Crate.java`
- Modify: `src/main/java/com/airdropmc/events/PackageDropEvent.java`
- Modify: `src/main/java/com/airdropmc/events/PackageLandEvent.java`
- Create: `src/test/java/com/airdropmc/api/event/AirdropEventOrderTest.java`
- Create: `src/test/java/com/airdropmc/api/event/AirdropEventCancellationTest.java`
- Create: `src/test/java/com/airdropmc/api/event/AirdropEventImmutabilityTest.java`
- Modify: `src/test/java/com/airdropmc/events/PackageEventLocationIsolationTest.java`
- Modify: `src/test/java/com/airdropmc/CrateLandingLifecycleTest.java`

**Steps:**

1. Write the event-order tests first. Successful flow is request-pre, spawn-post, legacy drop-post, landing-attempt-pre, landed-post, legacy land-post, outcome-post. Resolution failure fires no request event. Every event fires once on the primary thread and shares the request UUID/context.

2. Make request and landing-attempt events implement `Cancellable`. Request cancellation occurs before lease/payment/entity/task/cooldown mutation. Landing cancellation occurs before barrel/inventory/index/lease/delivery mutation, removes the falling crate, releases admission, and performs at most the existing single best-effort refund when charged. Preserve cancellation of Paper's `EntityChangeBlockEvent` as an independent protection path.

3. New events expose supported immutable snapshots only. Clone Bukkit locations on construction/access and never expose `Crate`, a lease, mutable contents, raw options, live maps, or internal controllers. Add normal Bukkit `HandlerList` boilerplate and synchronous event constructors.

4. Deprecate `PackageDropEvent` and `PackageLandEvent` with migration Javadocs to the supported post-events. Preserve their existing post-fact, non-cancellable behavior for the 5.x line and fire them only after the new committed post-event.

5. Emit outcome exactly once after final delivery/payment state. Recovery/retirement events and typed removal reasons land with the indexes in AIRDR-40; do not add duplicate native inventory/open/close/break events here.

6. Verify:

   ```bash
   ./gradlew test --tests 'com.airdropmc.api.event.*' --tests com.airdropmc.events.PackageEventLocationIsolationTest --tests com.airdropmc.CrateLandingLifecycleTest
   ./gradlew test
   git diff --check
   ```

7. Commit:

   ```bash
   git add -A && git commit -m "AIRDR-39: add cancellable Airdrop lifecycle events"
   ```

## Task 8: AIRDR-40 — publish registry changes and read-only active-drop queries

**Files:**

- Create: `src/main/java/com/airdropmc/api/PackageRegistryChange.java`
- Create: `src/main/java/com/airdropmc/api/PackageRegistryCause.java`
- Create: `src/main/java/com/airdropmc/api/RetirementReason.java`
- Create: `src/main/java/com/airdropmc/api/RecoveredDropDescriptor.java`
- Create: `src/main/java/com/airdropmc/api/event/PackageRegistryChangedEvent.java`
- Create: `src/main/java/com/airdropmc/api/event/AirdropRecoveredEvent.java`
- Create: `src/main/java/com/airdropmc/api/event/AirdropRetiredEvent.java`
- Create: `src/main/java/com/airdropmc/internal/api/PackageRegistryPublisher.java`
- Create: `src/main/java/com/airdropmc/internal/drop/ActiveDropRegistry.java`
- Create: `src/main/java/com/airdropmc/internal/recovery/DropContextPersistence.java`
- Modify: `src/main/java/com/airdropmc/internal/api/DefaultAirdropApi.java`
- Modify: `src/main/java/com/airdropmc/packages/PackageManager.java`
- Modify: `src/main/java/com/airdropmc/config/ConfigCoordinator.java`
- Modify: `src/main/java/com/airdropmc/helpers/CrateManager.java`
- Modify: `src/main/java/com/airdropmc/Crate.java`
- Modify: every crate retirement listener under `src/main/java/com/airdropmc/listeners/`
- Create: `src/test/java/com/airdropmc/api/PackageRegistryPublisherTest.java`
- Create: `src/test/java/com/airdropmc/api/ActiveDropIndexTest.java`
- Create: `src/test/java/com/airdropmc/api/AirdropRecoveryViewTest.java`
- Modify: `src/test/java/com/airdropmc/helpers/CrateManagerTest.java`
- Modify: `src/test/java/com/airdropmc/helpers/PaidCrateRecoveryTest.java`
- Modify: `src/test/java/com/airdropmc/config/ConfigCoordinatorTest.java`

**Steps:**

1. Add failing registry tests for startup, reload, create, update, and delete. A successful publication compares detached old/new maps, increments a monotonic in-memory revision, and fires one post-commit event containing immutable created/updated/deleted snapshots plus cause. Failed preparation/write/reload preserves map and revision and fires nothing.

2. Add failing lookup tests for immutable aggregate snapshots and request UUID, crate UUID, falling entity UUID, and landed `DropLocationKey`. Falling-to-landed is one atomic transition, not retire-then-add. Removal must occur once with a typed reason on every retirement path. Off-thread `activeDrops()` reads only the latest volatile immutable pure snapshot; Bukkit entity/block lookups retain primary-thread guards.

3. Extend new landed-barrel metadata with context schema version `1`, request UUID, source, optional player UUID, package name/price, and resolved primitive settings. Do not persist the original item list. Keep existing identity/paid/expiry/recovery keys.

4. Recovery tests must prove: schema-v1 metadata yields an optional recovery descriptor/request ID; legacy barrels without the new fields remain queryable by crate/block with those optionals empty; malformed/conflicting individual crates are purged fail-closed and reported degraded; recovery never reconstructs item snapshots from the current registry or mutable barrel inventory.

5. Fire `AirdropRecoveredEvent` after a recovered crate is indexed and exactly one `AirdropRetiredEvent` after tracking ends due to opened/closed cleanup, break, burn/explosion, expiry, cancellation, hot disable, or recovery purge. Use the recovery-specific optional context contract and do not synthesize normal request/spawn/landed events for recovered legacy state.

6. Verify:

   ```bash
   ./gradlew test --tests com.airdropmc.api.PackageRegistryPublisherTest --tests com.airdropmc.api.ActiveDropIndexTest --tests com.airdropmc.api.AirdropRecoveryViewTest --tests com.airdropmc.helpers.PaidCrateRecoveryTest --tests com.airdropmc.helpers.CrateManagerTest
   ./gradlew test
   git diff --check
   ```

7. Commit:

   ```bash
   git add -A && git commit -m "AIRDR-40: publish registry and active-drop snapshots"
   ```

## Task 9: AIRDR-42 — establish extension API versioning and compatibility checks

**Files:**

- Modify: `build.gradle.kts`
- Modify: `gradle.properties`
- Modify: `.gitignore`
- Modify: `src/main/java/com/airdropmc/Airdrop.java`
- Create: `src/main/resources/airdrop-api.properties`
- Create: `config/api-signatures/1.0.0.txt`
- Create: `docs/development/api-versioning.md`
- Create: `docs/migration-5.md`
- Create: `src/test/java/com/airdropmc/api/AirdropVersionsTest.java`
- Create: `src/test/java/com/airdropmc/ci/ApiCompatibilityConfigurationTest.java`
- Modify: `src/test/java/com/airdropmc/commands/CmdAirdropLifecycleSafetyTest.java`
- Modify: `src/test/java/com/airdropmc/config/PluginYmlPermissionsTest.java`
- Modify: `CHANGELOG.md`
- Modify: `README.md`
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/release.yml`

**Steps:**

1. Add failing tests requiring source version `5.0.0-SNAPSHOT`, extension API `1.0.0`, Paper `1.21.11`, and Java `21` to appear as distinct named fields in `AirdropVersions`, version command, README matrix, generated metadata, changelog, and verification tasks. Rename the internal `pluginApiVersion` terminology/getter to Paper compatibility (retain a deprecated adapter only for necessary 5.x source migration) and reject use of Bukkit's `plugin.yml api-version` as the extension API version.

2. Embed `Airdrop-API-Version: 1.0.0` in the runtime manifest and add an API-signature task limited to public/protected members under `com.airdropmc.api` (including events and excluding every `com.airdropmc.internal` type). Normalize sorted class/member signatures so timestamps and filesystem order cannot change the result. Record the initial `config/api-signatures/1.0.0.txt` only after the supported surface compiles.

3. Add `verifyApiCompatibility`. For the initial 1.0 line it compares the generated signature to the recorded baseline. For subsequent local/release builds, accept an explicit previous supported JAR/baseline property, run JApiCmp or an equivalent binary/source comparison only over `com.airdropmc.api`, and fail incompatible changes unless the extension API major or explicitly reviewed baseline changes. Never silently fetch an arbitrary latest version during ordinary `test`.

4. Add narrow ignore exceptions for the two version/migration documents. Document the semantic policy: additions may ship in an API minor; compatible fixes in patch; removal/signature/semantic breaks require API major; deprecated supported members remain for at least the current plugin major. Document the 5.0 migration away from implementation packages and legacy events.

5. Extend local release verification to cross-check release tag, runtime artifact filename, embedded plugin version, extension API resource, Paper/Java contract, changelog heading, and compatibility result.

6. Verify:

   ```bash
   ./gradlew generateApiSignature verifyApiCompatibility
   ./gradlew test --tests com.airdropmc.api.AirdropVersionsTest --tests com.airdropmc.ci.ApiCompatibilityConfigurationTest
   ./gradlew -PreleaseTag=5.0.0 verifyReleaseArtifact
   ./gradlew test
   git diff --check
   ```

7. Commit:

   ```bash
   git add -A && git commit -m "AIRDR-42: establish extension API compatibility policy"
   ```

## Task 10: AIRDR-43 — add status and focused debug diagnostics

**Files:**

- Modify: `src/main/java/com/airdropmc/api/AirdropStatus.java`
- Modify: `src/main/java/com/airdropmc/internal/api/DefaultAirdropApi.java`
- Create: `src/main/java/com/airdropmc/internal/diagnostics/AirdropDiagnostics.java`
- Create: `src/main/java/com/airdropmc/commands/StatusCommand.java`
- Modify: `src/main/java/com/airdropmc/commands/CmdAirdrop.java`
- Modify: `src/main/java/com/airdropmc/AirdropCommandNames.java`
- Modify: `src/main/java/com/airdropmc/AirdropTabCompleter.java`
- Modify: `src/main/java/com/airdropmc/config/ConfigCoordinator.java`
- Modify: `src/main/java/com/airdropmc/economy/EconomyProviderDiscovery.java`
- Modify: `src/main/java/com/airdropmc/limits/DropAdmissionController.java`
- Modify: `src/main/java/com/airdropmc/internal/drop/DropRequestCoordinator.java`
- Modify: `src/main/java/com/airdropmc/helpers/AirdropLogger.java`
- Modify: `src/main/java/com/airdropmc/lang/MessageKey.java`
- Modify: `src/main/resources/lang/en.yml`
- Create: `src/test/java/com/airdropmc/commands/StatusCommandTest.java`
- Create: `src/test/java/com/airdropmc/diagnostics/AirdropDiagnosticsTest.java`
- Modify: `src/test/java/com/airdropmc/commands/TabCompletionPermissionsTest.java`
- Modify: `src/test/java/com/airdropmc/config/ConfigCoordinatorTest.java`
- Modify: `src/test/java/com/airdropmc/packages/PackageNamePolicyTest.java`
- Modify: `src/test/java/com/airdropmc/packages/PackageManagerConfigRobustnessTest.java`
- Modify: `src/test/java/com/airdropmc/controllers/PackageControllerPermissionsTest.java`

**Steps:**

1. Add failing command tests for admin-only `/airdrop status`, permission-aware tab completion, and localized output in `STARTING`, ready/economy-disabled, ready/provider-missing, `FAILED`, and `STOPPING`. Handle `status` before the normal readiness gate. Require plugin/API/Paper/Java versions, readiness, economy mode/provider, package count/revision, pending/falling/landed counts, configured limits, sanitized last config/package failure, and canonical docs link.

2. Store a bounded immutable diagnostic snapshot in `DefaultAirdropApi`. Capture failures in `Airdrop.reloadConfiguration()` and package async operations—not only command callbacks—as a stable category plus single-line, control-character-stripped, bounded message; send neither stack traces nor raw file contents to players. Clear it only after the matching publication succeeds.

3. Add debug calls, gated by the existing `logging.debug`, for readiness transitions, provider discovery, configuration/package publication, admission decisions, and request lifecycle transitions. Log request IDs and typed reasons, not balances, complete inventories, credentials/tokens, chat contents, or per-tick rendering.

4. Keep command output localized with named placeholders. Add `status` to reserved top-level command names and update all package-name/permission tests that enumerate them. Ensure console/admin access works, ordinary players receive the standard admin-permission message, and `/airdrop version` remains concise while `/airdrop status` is operationally complete.

5. Verify:

   ```bash
   ./gradlew test --tests com.airdropmc.commands.StatusCommandTest --tests com.airdropmc.diagnostics.AirdropDiagnosticsTest --tests com.airdropmc.commands.TabCompletionPermissionsTest
   ./gradlew test
   git diff --check
   ```

6. Commit:

   ```bash
   git add -A && git commit -m "AIRDR-43: expose actionable Airdrop diagnostics"
   ```

## Task 11: AIRDR-33 — add a canonical Modrinth source and publisher lever

**Files:**

- Create: `docs/modrinth.md`
- Create (executable): `scripts/modrinth-docs`
- Create: `src/test/java/com/airdropmc/docs/ModrinthDocsContractTest.java`
- Modify: `build.gradle.kts`
- Modify: `README.md`
- Modify: `src/main/java/com/airdropmc/lang/MessageKey.java`
- Modify: `src/main/resources/lang/en.yml`
- Modify: `.github/workflows/release.yml`

**Steps:**

1. Add a failing contract test for the canonical file, stable required headings, all canonical project/support links, absence of obsolete wiki links, and a local verification task. The initial `docs/modrinth.md` must be self-contained enough to serve as a project body even before AIRDR-34/41 fill the detailed reference sections.

2. Implement `scripts/modrinth-docs` with these idempotent commands:

   - `check-local`: validate headings, links, and non-empty Markdown without network access.
   - `render-payload`: print deterministic JSON whose `body` is the exact file content.
   - `check-remote`: GET the public Modrinth project and fail on body drift.
   - `publish`: require explicit `MODRINTH_PROJECT_ID` and `MODRINTH_TOKEN`, compare first, PATCH only when different, then recheck.

   Use a unique Airdrop User-Agent, never echo the token, never accept the token as a positional argument, and fail before network mutation when configuration is missing. Keep `check-local` and `render-payload` usable without credentials.

3. Add `verifyModrinthDocs` to Gradle and wire it into `check`. Reduce README duplication to a short project overview, compatibility/quick-start pointer, local build commands, and the canonical URL. Point generated/in-plugin support links and the local release definition at Modrinth. Add the local workflow steps needed to publish/recheck the body after artifacts, but do not execute them.

4. Verify without remote mutation:

   ```bash
   ./gradlew test --tests com.airdropmc.docs.ModrinthDocsContractTest
   ./scripts/modrinth-docs check-local
   ./scripts/modrinth-docs render-payload | jq -e --rawfile expected docs/modrinth.md '.body == $expected'
   ./gradlew verifyModrinthDocs
   git diff --check
   ```

5. Commit:

   ```bash
   git add -A && git commit -m "AIRDR-33: add canonical Modrinth documentation publisher"
   ```

**Leave open externally:** live `publish`, observed remote equality, and GitHub wiki deletion/redirect are not performed in this local-only run. Record the local evidence in Plane but do not move AIRDR-33 to Done.

## Task 12: AIRDR-34 — document and test the administrator operating contract

**Files:**

- Modify: `docs/modrinth.md`
- Modify: `src/main/resources/packages.yml`
- Modify: `src/main/resources/lang/en.yml`
- Modify: `src/main/java/com/airdropmc/lang/MessageKey.java`
- Expand: `src/test/java/com/airdropmc/docs/ModrinthDocsContractTest.java`

**Required sections:**

- Exact compatibility and optional dependencies.
- Free quick start and a separate paid/economy setup.
- Commands and permissions, including help/version/status visibility.
- Every `config.yml` leaf with type, default, range, fallback, and reload behavior.
- `packages.yml` names, finite non-negative prices, Bukkit item serialization, invalid-item filtering, 27-stack truncation, and whole-candidate rejection.
- First-start provisioning, missing files, backup, safe regeneration, rollback, and upgrades.
- Locale file naming, configured/bundled fallback, placeholders, `&` color syntax, and translation creation.
- Symptom-to-fix troubleshooting for dependencies, economy discovery, invalid YAML, sky checks, limits, retained state after failed reload, payment ambiguity, and status/log collection.

**Steps:**

1. Extend the docs contract test to load and flatten every leaf in shipped `config.yml`, then require an anchored reference entry for each key. Extract the marked package YAML example, parse it with `YamlConfiguration`, and materialize it with `PackageManager`; assert expected names/prices and at most 27 retained stacks.

2. Make the shipped packages resource explicit as `packages: {}` if AIRDR-32 has not already done so. Correct or remove stale language claiming invalid prices fall back to `0.0` because the real behavior rejects the whole candidate. State that continuous effects stop when tracking ends.

3. Write the complete operating reference using current code/config as the source of truth. Do not claim live Modrinth publication or Paper versions outside the automated matrix.

4. Verify:

   ```bash
   ./gradlew test --tests com.airdropmc.docs.ModrinthDocsContractTest
   ./scripts/modrinth-docs check-local
   ./gradlew verifyModrinthDocs test
   git diff --check
   ```

5. Commit:

   ```bash
   git add -A && git commit -m "AIRDR-34: document administrator configuration contracts"
   ```

## Task 13: AIRDR-41 — stage API artifacts and compile an external consumer

### 13a. Sources, API-only Javadocs, and dependency-free publication

**Files:**

- Modify: `build.gradle.kts`
- Create: `src/test/java/com/airdropmc/api/ApiPublicationContractTest.java`
- Modify: `.github/workflows/release.yml`

**Steps:**

1. Add failing publication tests requiring runtime, sources, and Javadoc artifacts; supported API classes in runtime/sources; API docs in the Javadoc JAR; no internal API docs; and a POM without runtime/transitive dependencies.

2. Apply `maven-publish`, enable `sourcesJar`, and create `apiJavadoc` restricted to `com/airdropmc/api/**` excluding `internal`. Treat Javadoc warnings as errors. Package `apiJavadocJar` with classifier `javadoc`.

3. Add a local staging publication with the consumer-visible coordinate `maven.modrinth:airdrop:<plugin-version>`. Publish the runtime, sources, and API Javadoc JARs and deliberately strip dependencies from its POM so it matches Modrinth Maven behavior. Add `verifyApiPublication` to inspect artifacts, POM, versions, and class/doc contents.

4. Update the local release definition to upload all three artifacts to the same Modrinth version with runtime first. Do not execute GitHub or Modrinth steps.

5. Verify:

   ```bash
   ./gradlew apiJavadoc apiJavadocJar sourcesJar
   ./gradlew publishModrinthPublicationToStagingRepository
   ./gradlew verifyApiPublication
   ./gradlew test --tests com.airdropmc.api.ApiPublicationContractTest
   ```

6. Commit:

   ```bash
   git add -A && git commit -m "AIRDR-41: stage API sources and Javadocs"
   ```

### 13b. Compile-only consumer fixture and integration guide

**Files:**

- Create: `consumer-fixture/settings.gradle.kts`
- Create: `consumer-fixture/build.gradle.kts`
- Create: `consumer-fixture/src/main/java/dev/airdropmc/fixture/AirdropConsumerFixture.java`
- Create: `consumer-fixture/src/test/java/dev/airdropmc/fixture/SupportedImportsTest.java`
- Modify: `build.gradle.kts`
- Modify: `docs/modrinth.md`

**Fixture contract:**

- It is a separate Gradle build, not a root subproject.
- It resolves only `maven.modrinth:airdrop:<version>` from the passed local staging repository and declares Paper explicitly as `compileOnly`.
- It imports Airdrop only from `com.airdropmc.api`, does not shade Airdrop, and generates metadata with `depend: [Airdrop]`.
- It discovers the service through `ServicesManager`, attaches to readiness, observes the supported lifecycle events, and logs stable markers with the correlated request UUID.

**Steps:**

1. Write `SupportedImportsTest` to scan source/compiled constant pools and reject any `com.airdropmc` import outside `com.airdropmc.api`. Start red with the unresolved coordinate/service types.

2. Implement the fixture and stable markers `AIRDR_CONSUMER_READY`, `AIRDR_CONSUMER_SPAWNED`, `AIRDR_CONSUMER_LANDED`, and `AIRDR_CONSUMER_OUTCOME` (request ID plus delivery/payment for outcome).

3. Add root `consumerFixtureTest`, dependent on the staging publication, which passes absolute repository path and plugin version into the nested build. Its POM assertion must prove the fixture is not accidentally relying on root-project dependency metadata.

4. Complete the developer guide with Gradle/Maven setup, explicit Paper dependency, no shading, hard/soft dependency metadata, service discovery, readiness/threads, player/system requests, spawn/terminal stages, typed rejection/outcome handling, active queries, complete event order, protection cancellation, and ambiguous-payment no-retry policy.

5. Verify:

   ```bash
   ./gradlew consumerFixtureTest
   ./gradlew verifyModrinthDocs verifyApiPublication test
   jar tf consumer-fixture/build/libs/*.jar
   git diff --check
   ```

6. Commit:

   ```bash
   git add -A && git commit -m "AIRDR-41: add consumer fixture and integration guide"
   ```

**Leave open externally:** actual classifier availability on a released Modrinth version, tag-triggered upload, and the live published integration guide are not performed. Keep AIRDR-41 open unless its Plane criteria explicitly allow locally staged publication.

## Task 14: AIRDR-46 — harden dependency and artifact provenance

**Files:**

- Modify: `build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/verification-metadata.xml`
- Modify: `.github/dependabot.yml`
- Create: `src/test/java/com/airdropmc/ci/BuildProvenanceTest.java`
- Modify: `src/test/java/com/airdropmc/config/PluginYmlPermissionsTest.java`
- Potentially create: `consumer-fixture/gradle/verification-metadata.xml`

**Steps:**

1. Add failing provenance tests for canonical generated website metadata, packaged license, reproducible archive flags, narrowly routed repositories, Gradle/Maven updater entries, wrapper checksum, strict verification metadata, three publication artifacts, and an empty runtime dependency classpath.

2. Route ordinary dependencies through Central; exclusively route `io.papermc.paper` to PaperMC and `net.milkbowl.vault` to the creatorfromhell repository. Remove generic CodeMC, Sonatype, and JitPack from the root after proving no selected dependency uses them. Remove unnecessary plugin repositories only after `./gradlew help` proves plugin resolution.

3. Verify the official Gradle distribution checksum before setting `distributionSha256Sum=8fad3d78296ca518113f3d29016617c7f9367dc005f932bd9d93bf45ba46072b`. Package root `LICENSE` as `META-INF/LICENSE-Airdrop.txt`; set reproducible file order and no preserved timestamps; set generated `website` to `https://modrinth.com/plugin/airdrop`.

4. Add Dependabot definitions for Gradle `/` and Maven `/lightkeeper` alongside the existing actions updater. Generate SHA-256 verification metadata only after the full root dependency/plugin graph is final. For the fixture, verify external dependencies but explicitly handle the locally staged changing Airdrop artifact.

5. Extend `verifyReleaseArtifact` to inspect license, website, plugin/API/Paper versions, sources/Javadocs, runtime dependency emptiness, and reproducible output. Add a build check that builds twice from clean inputs and compares runtime JAR SHA-256.

6. Verify:

   ```bash
   ./gradlew test --tests com.airdropmc.ci.BuildProvenanceTest
   ./gradlew help
   ./gradlew dependencies --configuration runtimeClasspath
   ./gradlew --write-verification-metadata sha256 test build apiJavadocJar publishModrinthPublicationToStagingRepository
   ./gradlew --dependency-verification=strict clean test build verifyApiPublication
   ./gradlew -PreleaseTag=5.0.0 verifyReleaseArtifact
   jar tf build/libs/Airdrop-5.0.0.jar
   git diff --check
   ```

   `runtimeClasspath` must report no dependencies and the runtime JAR must contain the license/canonical metadata.

7. Commit the verified provenance configuration and generated checksums together so strict verification is never broken between integration points:

   ```bash
   git add -A && git commit -m "AIRDR-46: harden verified artifact provenance"
   ```

## Task 15: AIRDR-45 — make LightKeeper a repeatable packaged-consumer gate

### 15a. Narrow runtime reset and preserve diagnostics

**Files:**

- Modify: `build.gradle.kts`
- Modify: `lightkeeper/bootstrap-lightkeeper-plugin.sh`
- Modify: `src/test/java/com/airdropmc/ci/LightkeeperIntegrationConfigurationTest.java`
- Modify: `README.md`
- Modify: `docs/modrinth.md`

**Steps:**

1. Add failing source/task tests requiring `archiveLightkeeperDiagnostics` and `resetLightkeeperRuntime`. Archive prior server `logs/**` and `crash-reports/**` into `lightkeeper/target/lightkeeper-reports/previous-server/`, then delete only `lightkeeper/target/lightkeeper-server` and `lightkeeper/target/lightkeeper/runtime-manifest.json`. Preserve Failsafe reports, LightKeeper reports, and the adapter repository.

2. Make `lightkeeperTest` depend on the reset, current runtime JAR, `consumerFixtureTest`, and adapter validation. Ensure adapter preparation revalidates/rebuilds its pinned descriptor even when prior generated output exists. Pass both packaged plugin paths to Maven.

3. Document `clean lightkeeperTest` as the full reset and bare `lightkeeperTest` as the normal idempotent rerun; explicitly state which diagnostics each retains.

4. Commit:

   ```bash
   ./gradlew test --tests com.airdropmc.ci.LightkeeperIntegrationConfigurationTest
   git add -A && git commit -m "AIRDR-45: make LightKeeper reruns idempotent"
   ```

### 15b. No-LuckPerms, no-provider, and consumer lifecycle coverage

**Files:**

- Modify: `lightkeeper/pom.xml`
- Modify: `lightkeeper/src/test/resources/overlay/plugins/Airdrop/config.yml`
- Modify: `lightkeeper/src/test/resources/overlay/plugins/Airdrop/packages.yml`
- Modify: `lightkeeper/src/test/java/com/airdropmc/integration/AirdropIntegrationSupport.java`
- Modify: `lightkeeper/src/test/java/com/airdropmc/integration/StarterDropIT.java`
- Create: `lightkeeper/src/test/java/com/airdropmc/integration/EconomyNoProviderIT.java`
- Create: `lightkeeper/src/test/java/com/airdropmc/integration/ApiConsumerIT.java`
- Modify: `.github/workflows/release.yml`

**Steps:**

1. Remove the provisioned LuckPerms JAR and load the consumer fixture from its packaged path. Enable economy; retain free `starter`; add priced `paid`. Grant player permissions through LightKeeper's player permission API rather than LuckPerms commands.

2. Add `EconomyNoProviderIT`: Airdrop reaches degraded ready with no provider, the paid request receives the typed/localized unavailable response, and no request/spawn/legacy event, falling entity, or crate is created.

3. Add `ApiConsumerIT`: observe fixture-ready, trigger a free starter drop, and assert request/spawn/land/outcome markers have one correlated request UUID and primary-thread order. Keep the existing AIRDR-30 lifecycle edge tests intact.

4. Update the local release definition so its build command runs `lightkeeperTest` against the artifact produced from that checked-out tag. Do not invoke the remote workflow.

5. Prove clean, interrupted, and repeated non-clean convergence:

   ```bash
   ./gradlew clean lightkeeperTest
   ./gradlew lightkeeperTest
   mkdir -p lightkeeper/target/lightkeeper-server
   touch lightkeeper/target/lightkeeper-server/incomplete-preparation
   ./gradlew lightkeeperTest
   test -f lightkeeper/target/lightkeeper-reports/failsafe-summary.xml
   ./gradlew lightkeeperTest
   ./gradlew -PreleaseTag=5.0.0 verifyReleaseArtifact lightkeeperTest
   git diff --check
   ```

6. Commit:

   ```bash
   git add -A && git commit -m "AIRDR-45: gate packaged consumer integration"
   ```

No fake Vault/VaultUnlocked server plugin is added; deterministic provider-backed charge/refund paths remain unit-tested. **Leave open externally:** no tag-triggered job or actual release publication is performed, so AIRDR-45 remains open with local evidence.

## Task 16: integrate, independently review once, and update local `develop`

**Files:**

- Potentially modify any reviewed file with a validated finding.
- Do not touch the user's untracked root-worktree `repository-audit.md`.

**Steps:**

1. From the program worktree, inspect the complete stack and run the local final gate:

   ```bash
   git status --short
   git diff --check develop...HEAD
   ./gradlew --dependency-verification=strict clean test build
   ./gradlew verifyDependencyMatrix verifyApiCompatibility verifyModrinthDocs verifyApiPublication consumerFixtureTest
   ./gradlew -PreleaseTag=5.0.0 verifyReleaseArtifact
   ./gradlew clean lightkeeperTest
   ./gradlew lightkeeperTest
   ```

2. Start exactly one independent agent who made no implementation edits. Give it the accepted design, this plan, `develop...program/airdr-31-developer-experience`, and all test output. Its review is read-only and must report severity, exact file/line evidence, violated acceptance criterion, and a concrete fix. This is the sole DRI implementation review.

3. Evaluate every finding with `superpowers:receiving-code-review`. Reproduce credible defects, reject unsupported suggestions with evidence, apply fixes directly to the program branch with tests first, and commit them as `AIRDR-31: address final implementation review`. Do not request another final review.

4. Rerun every affected focused test plus the entire final gate above. Confirm the runtime artifact, sources, Javadocs, consumer fixture, docs payload, and two consecutive bare LightKeeper runs all correspond to the same HEAD.

5. Reconcile Plane:

   - Move locally satisfied AIRDR-32, 34–40, 42–44, and 46 to Done with commands/commit evidence.
   - Keep AIRDR-33 open for live Modrinth/wiki work.
   - Keep AIRDR-41 open if its wording requires hosted classifiers/live guide rather than staged artifacts.
   - Keep AIRDR-45 open for observed tag-triggered release evidence.
   - Keep AIRDR-31 open while any child remote acceptance remains open; add a summary that local implementation is complete and list the exact remaining external actions.

6. In the root worktree, prove `develop` is still clean apart from the preserved user file and fast-forward it locally:

   ```bash
   cd /home/luke/dev/airdrop
   git status --short
   git switch develop
   git merge --ff-only program/airdr-31-developer-experience
   git log --oneline --decorate -20
   ./gradlew test
   git status --short
   ```

   Do not push or perform any GitHub operation. Report the final local `develop` commit, verification evidence, preserved untracked file, and externally open Plane items.
