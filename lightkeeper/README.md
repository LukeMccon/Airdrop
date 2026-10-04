# Real-Paper integration tests

AIRDR-87 extends the pinned LightKeeper suite; AIRDR-92 reduces repeated server startup and setup work. Run both lanes from the repository root with Java 21:

```bash
./gradlew --no-daemon lightkeeperTest
```

The task builds the exact runtime and separately compiled public-API consumer JARs, archives previous server logs, resets generated server/manifests, and runs these lanes **serially**:

- `lightkeeperScenarioTest` / Maven profile `scenarios`: deterministic package overlay and fixture plugin named `Vault`. Covers delivery, gifts/grants, destruction/hopper behavior, economy results and late confirmations, public API requests, package navigation/editing/repricing/reload, paid chunk recovery, and scheduled expiry. LuckPerms is absent; permission tests use Bukkit attachments.
- `lightkeeperFreshInstallTest` / Maven profile `fresh-install`: Airdrop and the consumer only, with no Airdrop overlay, Vault, or LuckPerms. Checks generated paid starter data, provider-unavailable rejection, and customized data preservation across a graceful stop/start.

Each Gradle lane can also be run individually. Both depend on the same preparation/reset tasks. Full `clean` removes generated integration outputs; ordinary reruns retain diagnostics. Reports are under `target/failsafe-reports` and `target/lightkeeper-reports`, with fresh-install results in their `fresh-install` subdirectories. Runtime manifests contain authentication tokens: do not publish them. CI exports sanitized copies only.

Scenario methods share one Paper process per test class, with separate worlds and players for each method. CI runs economy/GUI, physical lifecycle, and fresh-install coverage in three independent jobs. Each job has its own checkout and Maven outputs; local lanes remain serial because they share the Maven module. A coverage check requires every integration class to appear in exactly one CI shard.

Platform setup uses the consumer fixture's console-only `platform` command to batch the same native Bukkit block writes into one server call. Setup finishes before bots and event captures are created, and preserves the chunk lifecycle behavior exercised by the persistence tests.

Select specific scenario classes through Failsafe without changing the default full suite:

```bash
./gradlew lightkeeperScenarioTest -PlightkeeperTests=PaidEconomyIT,PackageGuiIT
```

Summarize the latest integration reports with `./scripts/lightkeeper-timings`. CI adds this table to each job summary. For a before/after comparison, save the baseline `lightkeeper/target/failsafe-reports` directory outside `target`, then run:

```bash
./scripts/lightkeeper-timings --compare /path/to/baseline/failsafe-reports --log /path/to/gradle-console.log
```

The summary includes tests, failures, skips, and summed suite time. An optional console log adds the Paper launch count. Suite time includes server lifecycle callbacks but excludes Gradle/Maven preparation. Filtered local runs retain earlier reports, so use reports from full runs or a clean report directory when comparing performance.

A local Java 21 comparison on 2026-10-04, before integrating gift/grant and AIRDR-90 repricing coverage, ran the same 26 integration methods with no failures or skips. Full `lightkeeperTest` elapsed time fell from 603.48s to 291.74s (about 52%), summed suite time from 579.95s to 282.78s, and Paper launches from 27 to 10. These measurements used warm dependency caches and serial local lanes; the new CI matrix has not yet been timed remotely.

After integrating that coverage from `develop`, all 30 integration methods passed in 328.50s with 11 Paper launches (318.47s summed suite time).

## Assertion boundaries

LightKeeper's menu clicks generate synthetic `LEFT` / `PICKUP_ALL` events, **not normal Minecraft inventory transfers**. The GUI tests cover Airdrop's virtual package editor, navigation, and permission handling; they do not establish real client cursor conservation, shift-click safety, or inventory transaction correctness. The existing hopper scenario covers actual automated extraction. Lifecycle partial-content setup is controlled fixture setup, not claimed player extraction.

Normal scenarios must restore package bytes and temporary settings, close event captures, settle held economy confirmations, remove bots, and clean retained crates before the next method. Request assertions use the current test's output offset; startup readiness is checked separately. The fresh-install lane retains `@FreshServer`, which restarts the process without deleting its files. This separate disposable server has only one test: it edits files only while stopped, then leaves the second boot running so the extension can capture failure diagnostics before shutdown. Gradle resets that lane's files before the next run.

The pinned adapter captures outbound chat only for legacy/default bots, not full-login bots. GUI feedback and a dedicated no-provider localization scenario therefore use legacy bots; physical delivery and the original typed no-provider regression retain full-login bots. These message checks inspect real command feedback, not fixture-generated text.

Economy holds delay confirmation without blocking the fixture executor. A debit or credit may already have happened even when Airdrop reports `UNKNOWN`; tests must assert the ledger separately and observe request-correlated late-result reconciliation before checking for duplicate outcomes or money movement.

The fixture commands and `Vault.jar` are test-only. Never deploy them, or the consumer fixture, to a normal server. This suite does not cover crash recovery, real LuckPerms/provider compatibility matrices, or genuine native player inventory transactions.
