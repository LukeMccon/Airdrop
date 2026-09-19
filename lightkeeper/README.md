# Real-Paper integration tests

AIRDR-87 extends the pinned LightKeeper suite. Run both lanes from the repository root with Java 21:

```bash
./gradlew --no-daemon --dependency-verification=strict lightkeeperTest
```

The task builds the exact runtime and separately compiled public-API consumer JARs, archives previous server logs, resets generated server/manifests, and runs these lanes **serially**:

- `lightkeeperScenarioTest` / Maven profile `scenarios`: deterministic package overlay and fixture plugin named `Vault`. Covers delivery, destruction/hopper behavior, economy results and late confirmations, public API requests, package navigation/editing/reload, paid chunk recovery, and scheduled expiry. LuckPerms is absent; permission tests use Bukkit attachments.
- `lightkeeperFreshInstallTest` / Maven profile `fresh-install`: Airdrop and the consumer only, with no Airdrop overlay, Vault, or LuckPerms. Checks generated paid starter data, provider-unavailable rejection, and customized data preservation across a graceful stop/start.

Each Gradle lane can also be run individually. Both depend on the same preparation/reset tasks. Full `clean` removes generated integration outputs; ordinary reruns retain diagnostics. Reports are under `target/failsafe-reports` and `target/lightkeeper-reports`, with fresh-install results in their `fresh-install` subdirectories. Runtime manifests contain authentication tokens: do not publish them. CI exports sanitized copies only.

## Assertion boundaries

LightKeeper's menu clicks generate synthetic `LEFT` / `PICKUP_ALL` events, **not normal Minecraft inventory transfers**. The GUI tests cover Airdrop's virtual package editor, navigation, and permission handling; they do not establish real client cursor conservation, shift-click safety, or inventory transaction correctness. The existing hopper scenario covers actual automated extraction. Lifecycle partial-content setup is controlled fixture setup, not claimed player extraction.

`@FreshServer` restarts the process without deleting its files. Normal scenarios must restore package bytes and temporary settings, close event captures, settle held economy confirmations, remove bots, and clean retained crates. The fresh-install lane is a separate disposable server with only one test: it edits files only while stopped, then leaves the second boot running so the extension can capture failure diagnostics before shutdown. Gradle resets that lane's files before the next run.

The pinned adapter captures outbound chat only for legacy/default bots, not full-login bots. GUI feedback and a dedicated no-provider localization scenario therefore use legacy bots; physical delivery and the original typed no-provider regression retain full-login bots. These message checks inspect real command feedback, not fixture-generated text.

Economy holds delay confirmation without blocking the fixture executor. A debit or credit may already have happened even when Airdrop reports `UNKNOWN`; tests must assert the ledger separately and observe request-correlated late-result reconciliation before checking for duplicate outcomes or money movement.

The fixture commands and `Vault.jar` are test-only. Never deploy them, or the consumer fixture, to a normal server. This suite does not cover crash recovery, real LuckPerms/provider compatibility matrices, or genuine native player inventory transactions.
