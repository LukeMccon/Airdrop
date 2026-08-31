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
| Current source (`5.0.0-SNAPSHOT`) | `1.0.0` | `1.21.11` | `21` | unit + LightKeeper |

Paper `1.21.11` and Java `21` are the exact supported runtime. LuckPerms,
VaultUnlocked, Vault, and an economy provider are optional; the first-start
`starter` package is free and works without them.

Plugin developers should use only `com.airdropmc.api`. See the
[Airdrop 5 migration guide](docs/migration-5.md) and
[extension API version policy](docs/development/api-versioning.md).

## Get started

The [canonical Modrinth documentation](https://modrinth.com/plugin/airdrop)
contains the maintained installation, configuration, command, troubleshooting,
and developer integration guide:

- [Install and run the free starter](https://modrinth.com/plugin/airdrop#installation)
- [Configure Airdrop](https://modrinth.com/plugin/airdrop#configuration)
- [Troubleshoot a server](https://modrinth.com/plugin/airdrop#troubleshooting)
- [Integrate a plugin](https://modrinth.com/plugin/airdrop#developer-integration)

## Build locally

```bash
./gradlew clean build
./gradlew test
./gradlew runServer
./gradlew lightkeeperTest
```

The canonical Modrinth body is repository-owned. Validate it without network
access with:

```bash
./gradlew verifyModrinthDocs
```

Source is available under the [MIT license](LICENSE). Use the
[bug](https://github.com/LukeMccon/Airdrop/issues/new?labels=bug) and
[feature](https://github.com/LukeMccon/Airdrop/issues/new?labels=enhancement)
forms for project feedback.
