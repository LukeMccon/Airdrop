# Airdrop versions the extension API independently

Airdrop 5 starts the supported Java extension API at `1.0.0`. The plugin,
extension API, Paper compatibility, and Java requirement are separate values:

| Signal | Current development value | Purpose |
| --- | --- | --- |
| Airdrop plugin | `5.0.0-SNAPSHOT` | Plugin features and releases |
| Extension API | `1.0.0` | Supported types under `com.airdropmc.api` |
| Paper compatibility | `1.21.11` | Exact Paper target in `plugin.yml` |
| Java compatibility | `21` | Required JVM feature version |

Bukkit calls its Paper target `api-version`. That field never describes the
Airdrop extension API.

## Only `com.airdropmc.api` receives compatibility guarantees

The compatibility policy covers public and protected types and members in
`com.airdropmc.api`, including `com.airdropmc.api.event`. Packages such as
`com.airdropmc.controllers`, `com.airdropmc.helpers`, `com.airdropmc.internal`,
and the legacy `com.airdropmc.events` adapters remain implementation details.

Consumers should discover `AirdropApi` through Bukkit's `ServicesManager` and
compile only against supported API types. They should not shade the Airdrop JAR
or construct implementation classes.

## Semantic versions describe consumer impact

- An API minor may add compatible types, methods, events, or enum-independent
  behavior.
- An API patch may correct behavior without breaking the documented contract.
- Removing a supported member, changing a signature, or breaking documented
  semantics requires an API major.
- A deprecated supported member remains available for at least the rest of the
  current Airdrop plugin major. Its Javadoc names the replacement.

The plugin version can change without changing the extension API version. An
API version changes only when the supported consumer contract changes.

## Deterministic checks make API changes reviewable

Run the local compatibility checks with:

```bash
./gradlew generateApiSignature verifyApiCompatibility
```

`generateApiSignature` compiles the project and writes a sorted, timestamp-free
signature to `build/api-signatures/current.txt`. It includes public and
protected supported classes and members, and it excludes internal packages,
legacy events, compiler bridges, and synthetic members. The checked-in file at
`config/api-signatures/<api-version>.txt` records the reviewed surface for that
API version.

Ordinary builds compare only with the checked-in baseline; they never select or
download an arbitrary published Airdrop version. Release preparation can add a
binary and source comparison against an explicit supported artifact:

```bash
./gradlew -PpreviousApiJar=/absolute/path/to/airdrop.jar verifyApiCompatibility
```

An explicit normalized baseline is also accepted with
`-PpreviousApiBaseline=/absolute/path/to/signature.txt`. A reviewed baseline
change must either use the API version required by the semantic policy or pass
`-PreviewedApiBaselineChange=<current-api-version>` during the deliberate
regeneration step. The baseline diff remains visible in source control.

## A release must agree on every version signal

`verifyReleaseArtifact` checks the release tag and JAR name against the source
plugin version. It also checks `plugin.yml`, `airdrop-api.properties`, the JAR
manifest, the Java class-file target, the changelog heading, and the API
compatibility result. Run it with the intended tag:

```bash
./gradlew -PreleaseTag=5.0.0 verifyReleaseArtifact
```
