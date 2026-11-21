# Cut Airdrop 4.2.0 from the verified release branch

AIRDR-93 tracks this release. Promote `release/airdr-93-4.2` to `main` through
a reviewed PR. The branch includes the tested AIRDR-95 free-crate retention
fixes as well as the command hints and parachute lead fix; tagging develop
without that integration would omit part of the tested behavior.

## Verify the final candidate

Use Java 21 and Paper 1.21.11. Keep the source version
`airdropPluginVersion=4.2.0-SNAPSHOT`; the release property generates stable
artifact metadata. Record the clean candidate's full commit and JAR checksum:

```bash
git status --short
git rev-parse HEAD
./gradlew --no-daemon -PreleaseTag=4.2.0 clean test build \
  verifyApiCompatibility verifyReleaseArtifact lightkeeperTest
sha256sum build/libs/Airdrop-4.2.0.jar
```

Retain the automated retention, native command-tree, and parachute lead checks.
The dedicated Paper 26.2 / Java 25 server is additional manual coverage, not
a replacement for the supported runtime. Its owner has confirmed command
hints and the parachute lead fix. AIRDR-100 remains an open investigation into
a reserved-location mismatch; do not describe it as fixed by this release.

## Promote the exact verified tree

Main requires an approving review and a code-owner review. Wait for the PR's
checks and required review, then merge with a merge commit using the recorded
candidate SHA as the expected head. Afterward, fetch main and compare the
candidate tree with the merge commit. Any file difference needs verification
before tagging. Wait for CI on that exact main merge commit too.

## Publish and verify distribution

Create `v4.2.0` explicitly at the verified main merge commit. Do not tag the
repository's default branch (`develop`) by name. Extract only the first
changelog section, ending before the `# Airdrop 4.1.0` heading; remove internal
HTML comments and turn relative documentation links into links under
`https://github.com/LukeMccon/Airdrop/blob/v4.2.0/`.

Create and review a draft GitHub release with those notes. Publishing it as
stable/latest starts [Release Publish](../../.github/workflows/release.yml),
which builds and verifies the tag, uploads the runtime JAR to GitHub, and
publishes runtime, sources, and Javadoc artifacts to Modrinth. A tag or draft
alone does not start distribution. Repository configuration must include
`MODRINTH_TOKEN` and `MODRINTH_PROJECT_ID`.

Confirm all publication jobs pass. Download the hosted runtime artifacts,
compare their SHA-256 hashes with the verified build, check metadata, and
start a server with a downloaded JAR before declaring publication complete.
Keep a published tag fixed; a runtime correction afterward needs a patch
release. Bring the main release merge back into develop through a normal PR.

The Modrinth project overview is a separate operation: the release workflow
does not publish `docs/modrinth-description.md`.
