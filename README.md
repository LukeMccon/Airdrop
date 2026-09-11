<div align="center">

<img src="readme/airdrop-banner.png" height="270px" width="200px" alt="Airdrop logo" />

### *From the skies!*

![Paper 1.21.11](https://img.shields.io/badge/Paper-1.21.11-blue.svg)
![Java 21](https://img.shields.io/badge/Java-21-orange.svg)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![CI](https://github.com/LukeMccon/Airdrop/actions/workflows/ci.yml/badge.svg)
[![Download from Modrinth](https://img.shields.io/badge/Modrinth-download-1bd96a.svg)](https://modrinth.com/plugin/airdrop/versions)

A Paper plugin for customizable care packages with parachutes, effects,
economy support, and in-game package editing.

</div>

## Compatibility

| Airdrop | Extension API | Paper | Java | Automated lanes |
| --- | --- | --- | --- | --- |
| Current source (`4.1.0-SNAPSHOT`) | `1.0.0` | `1.21.11` | `21` | unit + LightKeeper |

Paper `1.21.11` and Java `21` are the exact supported runtime. LuckPerms,
VaultUnlocked, Vault, and an economy provider are optional. The first-start
`starter` package costs `10.0`; paid player requests require enabled economy
support, a Vault-compatible bridge, and an economy provider.

Plugin developers should use only `com.airdropmc.api`. See the
[Airdrop 4.1 migration guide](docs/migration-4.1.md) and
[extension API version policy](docs/development/api-versioning.md).

## Get started

The [canonical Modrinth documentation](https://modrinth.com/plugin/airdrop)
contains the maintained installation, configuration, command, troubleshooting,
and developer integration guide:

- [Install and configure the starter package](https://modrinth.com/plugin/airdrop#installation)
- [Configure Airdrop](https://modrinth.com/plugin/airdrop#configuration)
- [Troubleshoot a server](https://modrinth.com/plugin/airdrop#troubleshooting)
- [Integrate a plugin](https://modrinth.com/plugin/airdrop#developer-integration)

## Build locally

```bash
./gradlew clean build
./gradlew test
./gradlew runServer
./gradlew --dependency-verification=strict lightkeeperTest
```

A bare `lightkeeperTest` rerun archives the previous generated server's `logs`
and `crash-reports` under
`lightkeeper/target/lightkeeper-reports/previous-server`, then resets only the
generated server and runtime manifest. It retains Failsafe reports, LightKeeper
reports, and the pinned adapter repository for diagnosis and reuse. Use
`./gradlew --dependency-verification=strict clean lightkeeperTest` for a full
reset of `lightkeeper/target`.

The canonical Modrinth body is repository-owned. Validate it without network
access with:

```bash
./gradlew verifyModrinthDocs
```

## Update dependencies with their checksums

CI uses strict Gradle dependency verification. Dependabot version bumps can
require new checksums in `gradle/verification-metadata.xml`, including transitive
dependencies introduced by a Gradle wrapper update.
Dependabot groups the wrapper and run-paper plugin because plugin updates can
require a newer Gradle API. Apply the wrapper update first when reviewing older,
separate PRs for these tools.

On the dependency update branch, generate candidate checksums with:

```bash
./gradlew --no-daemon \
  --init-script scripts/refresh-dependency-verification.init.gradle \
  --write-verification-metadata sha256 help
git diff -- gradle/verification-metadata.xml
```

The init script works around Gradle's snapshot metadata writer bug by excluding
Paper from generation and retaining its existing checksums. Paper API upgrades
are manual: update the supported Paper/Java matrix and both build systems
together. Normal builds still verify the complete dependency graph.

Review the new coordinates and verify their checksums against the publishing
repositories before committing the metadata with the version update. Follow
[Gradle's dependency verification guidance](https://docs.gradle.org/current/userguide/dependency_verification.html).
Then run the CI commands without the generation init script:

```bash
./gradlew --no-daemon --dependency-verification=strict clean test build verifyApiCompatibility prepareCiRuntimeArtifact
./gradlew --no-daemon --dependency-verification=strict lightkeeperTest
```

Source is available under the [MIT license](LICENSE). Use the
[bug](https://github.com/LukeMccon/Airdrop/issues/new?labels=bug) and
[feature](https://github.com/LukeMccon/Airdrop/issues/new?labels=enhancement)
forms for project feedback.
