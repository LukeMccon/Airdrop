# AIRDR-31 developer experience design

Status: accepted after DRI design-review cycle 2 (`CONFIRM`)

## Goal

Make Airdrop predictable to install, operate, and integrate with by shipping one
small supported Java API, observable drop outcomes, integration-safe lifecycle
events, actionable diagnostics, and one canonical documentation source that can
be published to Modrinth without maintaining a second hand-edited copy.

This design covers AIRDR-31 through AIRDR-46. It deliberately treats Airdrop as
a Minecraft plugin rather than a general-purpose platform: the public surface is
small, Bukkit remains the runtime boundary, and internal controllers stay
internal.

## Decisions and assumptions

1. The already-approved documentation option is Modrinth-first. Canonical
   Markdown lives at `docs/modrinth.md`; a local command verifies or publishes
   that exact body through the Modrinth API. The command is implemented and
   tested locally, but this local-only delivery does not mutate the live
   Modrinth project. Completion is reported in two classes: local implementation
   and verification can complete here; live Modrinth publication, wiki redirect,
   and tag-triggered release evidence remain externally undeployed and their
   Plane criteria are not marked complete.
2. The supported boundary starts with Airdrop 5.0.0. The source-controlled
   development version becomes `5.0.0-SNAPSHOT`, and the independently
   versioned extension API begins at `1.0.0`. This avoids describing the already
   accepted 4.1 binary breaks as a compatible minor release while keeping the
   plugin and extension version signals unambiguous.
3. Only types under `com.airdropmc.api` are covered by compatibility promises.
   Existing controllers, `Crate`, managers, raw configuration wrappers, and
   `com.airdropmc.events` are implementation details. Legacy drop/land events
   remain as deprecated post-state adapters for one major line.
4. No public package-mutation API is added. There is no retained consumer use
   case that justifies exposing persistence transactions; administrators keep
   using commands and configuration. Consumers receive immutable package
   snapshots and a registry-change event.
5. LuckPerms convenience groups remain available when LuckPerms is installed,
   but Bukkit permissions work without it. Direct LuckPerms references are
   isolated behind an optional integration class.
6. Supported runtime coverage is exact rather than open-ended: Paper 1.21.11
   and Java 21. A future/latest Paper lane can broaden that matrix later.

## Supported API boundary

`com.airdropmc.api.AirdropApi` is the only service entry point. Airdrop registers
one provider with Bukkit's `ServicesManager` during `onEnable()` and unregisters
it during `onDisable()`. Consumers discover it through Bukkit after declaring
`depend: [Airdrop]` or `softdepend: [Airdrop]`; no static singleton is needed.

The interface exposes:

- `CompletionStage<AirdropApi> readiness()` and `ReadinessState state()`;
- `AirdropVersions versions()` and an immutable `AirdropStatus status()`;
- immutable package `listPackages()` and `findPackage(String)` snapshots;
- `requestPlayerDrop(Player, String, DropRequestOptions)` for permission- and
  economy-aware requests;
- `requestSystemDrop(Location, String, DropRequestOptions)` for explicitly
  unpaid system requests;
- read-only active-drop collection and lookup by request UUID, crate UUID,
  falling entity, or landed block.

Public value types include `AirdropPackage`, `DropRequestOptions`,
`ResolvedDropSettings`, `DropRequestDescriptor`, `ResolvedDropContext`,
`DropHandle`, `DropSpawnResult`, `DropOutcome`, `DropRejection`, `AirdropView`,
`AirdropStatus`, and version, state, source, delivery, payment, retirement, and
registry-cause enums. `AirdropPackage` avoids the `java.lang.Package` collision.

Value objects copy mutable Bukkit values at construction and on access:

- Bukkit locations are cloned;
- item stacks are cloned and returned in an unmodifiable copied list;
- collections and maps are immutable snapshots;
- request options resolve once before the request event and are stored as
  primitive resolved settings;
- no API getter returns live `Crate`, `DropOptions`, `Config`,
  `PackagesConfig`, `Package`, controller, registry map, or mutable lease.

Defensive copying protects Airdrop's state; it is not a blanket Paper
thread-safety guarantee. UUIDs, names, prices, versions, enums, primitive status
fields, and pure `WorldPosition` values are safe to read off-thread. Service
calls and accessors that accept or return Bukkit `Location`, `ItemStack`,
`Player`, `Block`, or `Entity` values are primary-thread-only and say so in
Javadocs. Programmer errors such as null arguments or off-thread Bukkit calls
fail fast at the API boundary. Expected operational failures are values, not
chat messages or exceptional completion.

## Thread and lifecycle contract

The service is registered in `STARTING` state before asynchronous configuration
work begins. Its readiness stage completes on the primary server thread only
after configuration, language, package registry, economy discovery, optional
integrations, and crate recovery are published successfully. Startup failure
completes readiness exceptionally, records a sanitized diagnostic, changes the
state to `FAILED`, and disables the plugin. Disable changes the state to
`STOPPING`, fails incomplete readiness and drop handles, unregisters the
service, and then performs existing cleanup.

Fatal startup failures are inability to read/materialize/publish the required
configuration or package registry, failure to schedule the primary-thread
commit, service-registration failure, and a catastrophic recovery-scan failure.
Degraded but ready states include economy disabled, no economy provider,
LuckPerms absent, LuckPerms convenience-group failure, and individual malformed
or conflicting recovered crates that are purged by the existing fail-closed
policy. Those degraded results are recorded in status/debug diagnostics instead
of failing readiness.

Calls that accept, return, or inspect Bukkit objects—drop requests, package item
copies, location copies, and entity/block lookups—require the primary thread.
Version/state access, readiness attachment, and the pure fields listed above are
safe off-thread. Airdrop performs state transitions, completes backing stages,
and fires Bukkit events on the primary thread, including economy callbacks that
start elsewhere. This does not guarantee a continuation executor: a non-async
continuation attached after completion can run on the attaching thread.
Consumers must schedule Bukkit work explicitly. Handles expose
`minimalCompletionStage()` views so callers cannot complete or cancel Airdrop's
internal futures.

Repeated enable/disable or duplicate asynchronous callbacks converge: service
registration replaces only this plugin's stale registration, unregister is
safe when already absent, and each request has one terminal completion guard.

## Request and outcome flow

Every request gets a UUID before operational validation and returns a
`DropHandle` with an always-present immutable `DropRequestDescriptor`, an
optional resolved context, a spawn stage, and a terminal stage. The descriptor
contains source, optional player UUID, requested package name, and the cloned
requested location. `ResolvedDropContext` exists only after package lookup,
target resolution, and option resolution succeed; it adds the package snapshot,
spawn/landing positions, and resolved settings. Unknown-package and target
resolution failures therefore remain typed without inventing unavailable data.

The primary-thread flow is:

1. Create the descriptor, then resolve package, location, and options into a
   detached context. Resolution failures complete the handle with a typed
   rejection and do not fire a request event.
2. Fire cancellable `AirdropRequestEvent` before reservation, payment, entity,
   task, or cooldown side effects.
3. Acquire the existing admission lease. Convert permission, sky, limit,
   shutdown, economy-disabled, provider-missing, and insufficient-funds paths
   into typed rejections.
4. For a free/system request, spawn immediately. For a paid request, run the
   existing affordability and withdrawal sequence and marshal every callback
   to the primary thread.
5. After the falling entity is registered and the lease's spawn state is
   committed, complete the spawn stage and fire `AirdropSpawnedEvent`, followed
   by deprecated `PackageDropEvent`.
6. Continue honoring a cancelled Paper `EntityChangeBlockEvent`. Otherwise fire
   cancellable `AirdropLandingAttemptEvent` before block, inventory, landed
   index, lease, or paid-delivery mutation.
7. On success, commit the barrel and landed index, fire `AirdropLandedEvent`,
   then deprecated `PackageLandEvent`.
8. Complete exactly once with `AirdropOutcomeEvent` after delivery/payment state
   is final.

`DeliveryStatus` and `PaymentStatus` are separate. Payment represents at least
`NOT_APPLICABLE`, `REJECTED`, `CHARGED`, `REFUNDED`, `REFUND_FAILED`, and
`UNKNOWN`; delivery represents rejected, failed, cancelled, shutdown, and
landed terminal states. Constructors enforce valid combinations, and rejection
details retain typed causes and retry duration where relevant.

Cancellation before admission releases nothing and never charges. Landing
cancellation removes the falling crate and releases its lease. If a confirmed
charge exists, it follows the existing single best-effort refund policy.
Timeout or shutdown ambiguity becomes `PaymentStatus.UNKNOWN` and is never
retried automatically. Late or duplicate callbacks cannot fire another outcome
or repeat payment/refund side effects.

## Events and active-drop queries

New events live under `com.airdropmc.api.event`:

- `AirdropRequestEvent` — cancellable, before side effects;
- `AirdropSpawnedEvent` — post-commit;
- `AirdropLandingAttemptEvent` — cancellable, before landing mutation;
- `AirdropLandedEvent` — post-commit;
- `AirdropOutcomeEvent` — terminal and exactly once;
- `PackageRegistryChangedEvent` — post-publication with cause, revision, and
  immutable created/updated/deleted snapshots;
- `AirdropRecoveredEvent` — a persisted crate became active;
- `AirdropRetiredEvent` — tracking ended with a typed reason.

All fire on the primary thread. Request/spawn/landing/outcome events only occur
after resolution and carry immutable IDs, source, optional player UUID,
package/context snapshot, locations, settings, and applicable delivery/payment
state. Existing `PackageDropEvent` and `PackageLandEvent` keep their post-fact,
non-cancellable semantics and are deprecated with migration Javadocs.

`CrateManager` continues to own live indexes, but exposes only a volatile,
immutable aggregate snapshot through the service so off-thread readers never
touch Bukkit collections. New internal request/crate UUID indexes are updated
atomically with falling/landed maps and removed from every retirement path.
New landed crates persist context schema version 1 plus request UUID, source,
optional player UUID, package name and price, and resolved primitive settings.
The original item snapshot is deliberately not copied into barrel metadata: it
may be too large, and the live barrel inventory can be player-modified. Recovery
never reconstructs original items from the current package registry or barrel
contents. Recovered views therefore contain an optional recovery descriptor and
optional request UUID, not an unconditional original `ResolvedDropContext`.
Legacy barrels without the new keys remain queryable by crate/block and expose
those fields as absent. `AirdropRecoveredEvent` follows this recovery-specific
contract; normal request lifecycle events retain complete resolved context.
Open, close, break, burn, explosion, and hopper behavior stays represented by
Paper events plus API lookup rather than new duplicate events.

## Package registry publication

Successful startup, full reload, create, update, and delete operations publish a
complete detached package map. The commit step compares old and new snapshots,
increments a monotonic in-memory revision, and fires one post-commit event.
Failed preparation/write/reload leaves the map and revision unchanged, fires no
registry event, and records the last failure for status output. The API never
exposes the raw YAML or mutation methods.

## Administrator experience

First-start provisioning makes `starter` free so a fresh Paper server can run a
drop without an economy provider. The quick start uses that existing package
instead of attempting to create it. Paid setup is a separate documented path.

`/airdrop` and malformed input render concise localized help and return handled
status rather than Bukkit's generated usage. Missing-package feedback points to
tab completion or `/airdrop package <name>`, which ordinary permitted players
can use. `/airdrop version` labels plugin, extension API, Paper compatibility,
and Java runtime separately and links Modrinth. Admin-only `/airdrop status`
adds readiness, economy mode/provider, package count, falling/landed counts,
limits, and the last sanitized reload failure.

Debug logging is restricted to readiness transitions, provider discovery,
configuration/package publication, admission decisions, and lifecycle
transitions. It never logs balances, full inventories, secrets, or per-tick
rendering.

## Documentation and publishing

`docs/modrinth.md` is a self-contained Modrinth project body covering:

- exact Paper/Java compatibility and optional dependencies;
- free and paid installation/quick starts;
- commands and permissions;
- every config key's type, default, range, fallback, and reload behavior;
- valid `packages.yml`, Bukkit item serialization, 27-stack truncation/filtering,
  and whole-candidate rejection;
- first-start, missing files, backup, safe regeneration, rollback, and upgrades;
- locale file naming, fallback, placeholders, color syntax, and translation;
- symptom-to-fix troubleshooting and status/log collection;
- developer dependency setup, service discovery/readiness/threading, requests,
  results, queries, event order, protection integration, and payment ambiguity;
- source, releases, bug report, and feature request links.

`scripts/modrinth-docs` is the rerunnable lever. `check-local` validates required
sections/links; `check-remote` compares the public Modrinth body with the file;
`publish` PATCHes the body only with an explicit project ID and token, then
rechecks it. README, generated plugin metadata, version/status output, and
release metadata use `https://modrinth.com/plugin/airdrop` as the canonical docs
destination. The release definition uploads the runtime, sources, and Javadoc
files to the same Modrinth version, then publishes and verifies the project body.
No standalone docs site is introduced.

## Build, publication, and compatibility

The existing runtime JAR remains the compile-only artifact; a separate API
module would add ceremony without hiding classes from a Java JAR. Gradle's
`maven-publish` staging publication uses the same externally supported Modrinth
Maven coordinate, `maven.modrinth:airdrop:<plugin-version>`, and publishes
runtime, sources, and supported-API Javadoc JARs. Consumers are told not to
shade it.

Javadoc generation includes the supported package and fails on warnings. The
initial API 1.0.0 release records a deterministic public signature because no
prior supported API artifact exists. Subsequent releases run JApiCmp against
the latest supported Modrinth Maven artifact and cover only
`com.airdropmc.api`; incompatible changes fail verification unless the API major
or explicitly selected baseline changes. Release verification cross-checks the
source version, artifact name, embedded plugin/API/Paper versions, changelog,
docs coordinate, Javadocs, consumer fixture, and license.

MockBukkit moves to the maintained `org.mockbukkit.mockbukkit` coordinate that
resolves the chosen Paper target. A JUnit BOM owns all Jupiter/Platform versions.
Dependency-insight assertions lock Paper 1.21.11 and the selected JUnit version.
Repository content filters route Paper and VaultUnlocked groups only to their
required repositories; unused JitPack/Sonatype routes are removed. Gradle
dependency verification, the wrapper SHA-256, Dependabot entries for Gradle and
the LightKeeper Maven build, reproducible JAR settings, and `META-INF/LICENSE`
complete artifact provenance.

## Real-server and consumer verification

LightKeeper setup removes only generated server/runtime state before a rerun,
keeps prior reports and logs, always validates/rebuilds the pinned plugin
adapter, and therefore survives partial previous preparation. The fast unit
suite remains separate.

A small consumer plugin compiles from the local Maven publication using only
`com.airdropmc.api`, declares `depend: [Airdrop]`, discovers the service,
attaches to readiness, and observes lifecycle events. Its build declares Paper
explicitly and consumes a dependency-free staged POM so it matches Modrinth
Maven rather than relying on richer project metadata. LightKeeper loads its JAR
with the packaged Airdrop JAR. The default real-server lane omits LuckPerms,
assigns permission through LightKeeper's player API, and covers the normal free
drop plus economy enabled with no provider and a priced rejection. A separate
fake Vault/VaultUnlocked server plugin is excluded: no deterministic provider
fixture exists, and creating one is disproportionate to the criterion's
"when practical" qualifier; provider-backed charge/refund behavior remains
covered by deterministic unit tests. Release verification runs the exact tagged
artifact through LightKeeper rather than relying on a different workflow
result.

## Test strategy

Each behavior change follows red-green-refactor and lands as an AIRDR-keyed,
individually verifiable commit. Focused tests cover:

- first-start free provisioning, help, permissions, links, and status states;
- absence/presence of LuckPerms and generated `softdepend` metadata;
- defensive copying of every public model and collection;
- service registration/readiness/failure/disable and primary-thread completion;
- every free/paid rejection, charge/refund/timeout/shutdown/duplicate-completion
  outcome;
- event order, cancellation cleanup, immutable correlation, and legacy events;
- registry revisions/diffs, failed reloads, recovery/retirement, and lookup
  cleanup;
- documentation examples and publisher dry-run payload;
- dependency resolution, Javadocs, API compatibility, metadata, license,
  reproducibility, and empty runtime dependency classpath;
- clean and repeated non-clean LightKeeper runs with the consumer fixture.

The final gate is `clean test build`, documentation/API/release verification,
two consecutive bare `lightkeeperTest` runs, and one independent DRI
implementation review followed by affected re-verification.

## Delivery sequence and worktree boundaries

One integration worktree owns the ordered commit stack. Isolated agent
worktrees branch from the current integration tip and are integrated only after
their focused checks pass:

1. AIRDR-44, 35, 32: align the test foundation, decouple LuckPerms, and correct
   first-run behavior.
2. AIRDR-36, 37, 38, 39, 40, 42, 43: immutable API, service, outcomes, events,
   queries, compatibility, and diagnostics, kept sequential because they share
   lifecycle state.
3. AIRDR-33, 34, 41, 46, 45: canonical documentation, publication/provenance,
   consumer fixture, and real-server release gate after signatures stabilize.

AIRDR-46 follows final dependency selection so verification metadata describes
the actual graph. AIRDR-45 is last because it verifies the packaged result.
Each integration point must be green before the next branch is cut.

## Exclusions

- No standalone documentation website or airdropmc.com redesign.
- No public package create/update/delete API without a concrete consumer.
- No duplicate Bukkit inventory/open/close/break events.
- No automatic retry of ambiguous economy operations.
- No promise of Paper versions that are not in the automated matrix.
- No fake economy-provider plugin solely to add a redundant real-server smoke
  path; deterministic paid outcomes remain covered at the unit boundary.
- No live Modrinth, wiki, GitHub, push, or pull-request mutation in this
  local-only run; the resulting commands and files are ready for a later
  credentialed publish, and remote-only Plane criteria remain open until that
  deployment is observed.
