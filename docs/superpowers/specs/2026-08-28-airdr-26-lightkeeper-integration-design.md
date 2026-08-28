# AIRDR-26 LightKeeper Integration Design

## Goal

Add a small real-Paper integration-test lane powered by LightKeeper and move Airdrop's declared Paper compatibility floor from 1.21.8 to 1.21.11. The first scenario will prove the highest-value vertical slice: a permitted player requests the default starter package, the crate lands, and its barrel contains the expected items.

## Compatibility Decision

LightKeeper currently targets Paper 1.21.11. AIRDR-26 therefore updates every active Airdrop declaration of its supported Paper version to 1.21.11:

- the Paper API compile dependency;
- the local `runServer` version;
- generated `plugin.yml` `api-version`;
- README badges and requirements;
- changelog compatibility notes;
- release-workflow Modrinth fallback metadata; and
- the generated-plugin contract test.

This is an intentional floor bump, not a multi-version test matrix. Historical audit material is left unchanged.

## Chosen Architecture

Keep the LightKeeper project in a Maven sidecar under `lightkeeper/`. Airdrop remains a Gradle project, while the sidecar follows LightKeeper's supported Maven-plugin model. A Gradle `lightkeeperTest` task bridges the projects by building the Airdrop JAR and invoking the sidecar's checked-in Maven wrapper with an absolute `airdrop.jar.path` property.

The LightKeeper dependencies and plugin are pinned to upstream commit `be585af08221c37bcbc8c9d7f5a40a27dbd2dff1`. The advertised JitPack `1.2.0` framework coordinate is not currently resolvable, while full-commit coordinates are. LuckPerms is provisioned from the exact Modrinth version ID `b0mk8uS6` so permission behavior does not float independently of the test.

The sidecar declares JitPack as both a dependency and plugin repository, declares the Paper repository, and includes explicit test-scoped Paper API and JUnit engine dependencies. Maven Failsafe owns the integration-test lifecycle. The wrapper pins Maven 3.9.16 and verifies the Maven distribution checksum.

The new task is deliberately not attached to Gradle `test`, `check`, or `build`. It runs in a separate CI job so contributors retain a fast unit-test loop and LightKeeper failures remain easy to identify.

## Provisioning and Diagnostics

LightKeeper will provision:

- Paper 1.21.11;
- the just-built Airdrop JAR from `-Dairdrop.jar.path`; and
- LuckPerms `b0mk8uS6` for Bukkit 1.21.11.

Provisioned plugins are renamed to stable `Airdrop.jar` and `LuckPerms.jar` names. The client user agent identifies Airdrop and its repository.

The server work directory lives under `lightkeeper/target/lightkeeper-server`. The runtime manifest is written to `lightkeeper/target/lightkeeper/runtime-manifest.json`, preserving the exact Paper build resolved by LightKeeper even though the Paper build itself is intentionally allowed to follow the current stable 1.21.11 build. Successful runs clean the server work area; failed runs retain diagnostics.

CI always uploads Failsafe reports, LightKeeper reports, LightKeeper diagnostics, and the runtime manifest when present.

## Runtime Configuration

The overlay supplies only `plugins/Airdrop/config.yml`. It disables economy integration and particle effects, requests one chicken, and uses a short controlled fall. It intentionally omits `packages.yml`, allowing Airdrop to create its shipped default starter package.

The scenario uses a unique flat world and constructs a small landing platform. A full-login LightKeeper bot begins on the landing X/Z coordinate. LuckPerms grants the bot `airdrop.drop`, then the test verifies both command success and the bot's live permission before invoking `/airdrop starter`.

The test captures `PackageDropEvent` and `PackageLandEvent`. Immediately after the single drop event, it teleports the bot away so later player-obstruction behavior cannot move the landing barrel. It then verifies exactly one land event, the barrel position, and the default starter contents through a real adjacent-player interaction:

- iron helmet in slot 0;
- iron chestplate in slot 1;
- iron leggings in slot 2;
- iron boots in slot 3; and
- two bread in slot 4.

Bot and event-capture cleanup runs even after assertion failures.

## Contract Tests

A fast JUnit contract test will first define the decision-bearing integration configuration. It will verify the pinned LightKeeper commit, Maven and Paper versions, exact LuckPerms version ID, required repositories, Failsafe binding, Airdrop JAR property, Gradle task isolation, runtime-manifest path, CI job/artifact wiring, and 1.21.11 release metadata.

The contract test should parse or narrowly inspect the relevant files. It must not become a brittle full-file snapshot.

## Verification

AIRDR-26 is complete when all of the following pass:

1. The focused integration-configuration contract test.
2. The existing Gradle unit-test suite.
3. `./gradlew lightkeeperTest`, including real Paper startup and the starter-drop scenario.
4. `./gradlew clean build`.
5. Release artifact verification with a valid release tag.
6. Inspection of the built plugin JAR confirming `api-version: '1.21.11'` and no bundled LightKeeper classes.

## Exclusions

- Paid-economy drop scenarios.
- Crash/restart recovery.
- GUI safety or package editing.
- Spigot, Folia, or a Minecraft-version matrix.
- Pinning an exact Paper build beyond the recorded runtime manifest.
- Refactoring unrelated Airdrop production code.
