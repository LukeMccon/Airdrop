# Airdrop

Airdrop is a Paper plugin for care packages that descend with a parachute and
land as barrels. Server owners can edit package inventories in game, set free or
paid access, limit active drops, localize messages, and adjust landing effects.
A small Java API lets other plugins request drops and observe their lifecycle.

This file is the canonical source for
[the Airdrop Modrinth project page](https://modrinth.com/plugin/airdrop). Release
automation can publish these exact bytes; the repository does not maintain a
second hand-edited project description.

## Quick links

- [Installation](https://modrinth.com/plugin/airdrop#installation)
- [Configuration](https://modrinth.com/plugin/airdrop#configuration)
- [Troubleshooting](https://modrinth.com/plugin/airdrop#troubleshooting)
- [Developer integration](https://modrinth.com/plugin/airdrop#developer-integration)
- [Source](https://github.com/LukeMccon/Airdrop)
- [Releases](https://modrinth.com/plugin/airdrop/versions)
- [Report a bug](https://github.com/LukeMccon/Airdrop/issues/new?labels=bug)
- [Request a feature](https://github.com/LukeMccon/Airdrop/issues/new?labels=enhancement)

## Compatibility

| Server | Java | Required plugins | Optional integrations |
| --- | --- | --- | --- |
| Paper `1.21.11` | Java `21` | None | LuckPerms; VaultUnlocked or Vault with an economy provider |

This is an exact support matrix, not a claim that later Paper versions are
compatible. Airdrop starts without LuckPerms, Vault, or an economy provider.
Standard Bukkit permissions work without LuckPerms. LuckPerms only adds
convenience permission-group integration.

Free packages work when economy support is absent or disabled. Priced player
requests require `economy.enabled: true`, a Vault-compatible bridge, and an
economy provider. If any part is missing, Airdrop rejects the request before it
charges the player or spawns a crate.

Protection plugins do not need a private Airdrop hook. They may cancel Paper's
`EntityChangeBlockEvent` to prevent a falling crate from replacing a block.

## Installation

1. Download the Airdrop build for Paper `1.21.11` from
   [Modrinth releases](https://modrinth.com/plugin/airdrop/versions).
2. Put the JAR in the server's `plugins/` directory.
3. Start or restart Paper with Java `21`.
4. Run `/airdrop version`. It reports the plugin, extension API, Paper target,
   Java runtime, and this documentation link.
5. Run `/airdrop status` as an operator to check readiness, package counts,
   economy discovery, active-drop counts, limits, and the last safe diagnostic.

### Starter package costs 10

When `plugins/Airdrop/packages.yml` is missing at startup, Airdrop creates a
`starter` package with iron armor and bread, priced at `10.0`. Request it in game:

```text
/airdrop starter
```

The starter requires enabled economy support, VaultUnlocked or Vault, and a
compatible economy provider. Without them, Airdrop rejects the request.
Operators can request every package. To let another player request the starter,
grant `airdrop.package.starter` with any Bukkit-compatible permission manager.

Startup creates the starter only when `packages.yml` is missing. Existing files
keep their packages and prices, including any free packages. An existing file
containing `packages: {}` stays empty.

### Paid setup

1. Install VaultUnlocked or Vault and an economy plugin that registers a
   compatible provider.
2. Keep `economy.enabled: true` in `plugins/Airdrop/config.yml`.
3. Start the server and confirm the provider with `/airdrop status`.
4. In game as an operator, run `/airdrop package create premium 10`.
5. Add items in the editor, save the package, and grant
   `airdrop.package.premium` to the intended players.
6. Request it with `/airdrop premium`. Each successful request costs `10` in
   your economy provider's currency.

Choose a price that fits your server's economy. If you want to offer a free
package, explicitly set its price to `0`; free packages work without an economy
plugin.

Airdrop does not turn a missing provider into a free purchase. After a confirmed
charge and a known delivery failure, it makes one best-effort refund attempt.
An ambiguous economy result remains unknown and is not retried automatically,
because a retry could duplicate money or items.

## Commands and permissions

`/drop` and `/ad` are aliases for `/airdrop`. Root help only shows entries that
make sense for the sender. Help and version output remain visible while the
plugin is starting; admin status is also available before readiness so startup
failures can be diagnosed.

| Command | Purpose | Access |
| --- | --- | --- |
| `/airdrop` | Show sender-appropriate help | Everyone, including console |
| `/airdrop <package>` | Request a package at the player's location | Player with `airdrop.package.<package>` or `airdrop.package.all` |
| `/airdrop package <name>` | Inspect a package and its price | Everyone, including console |
| `/airdrop package create <name> <price>` | Create a package and open its editor | In-game player with `airdrop.admin` |
| `/airdrop package delete <name>` | Delete a package | `airdrop.admin`; console is allowed |
| `/airdrop packages` | Open package management | In-game player with `airdrop.admin` |
| `/airdrop reload` | Atomically reload config, locale, packages, and economy discovery | `airdrop.admin`; console is allowed |
| `/airdrop version` | Show plugin, API, Paper, Java, and docs signals | Everyone, including console |
| `/airdrop status` | Show bounded operational state and the last sanitized diagnostic | `airdrop.admin`; console is allowed |

The generated Bukkit permissions are:

| Permission | Meaning | Default |
| --- | --- | --- |
| `airdrop.package.<package>` | Request one package; package identity is case-insensitive | Not granted by Airdrop |
| `airdrop.package.all` | Request every package | False |
| `airdrop.package.*` | Compatibility wildcard whose child is `airdrop.package.all` | False |
| `airdrop.cooldown.bypass` | Bypass only the per-player request cooldown | Operator |
| `airdrop.admin` | Use admin commands and all packages; includes cooldown bypass | Operator |

Cooldown bypass does not bypass falling capacity, landed capacity, a pending
request, a reserved landing position, or the clear-sky check. The server console
is treated as an administrator, but inventory and player-location commands still
require an in-game player.

## Configuration

`plugins/Airdrop/config.yml` controls global behavior. `/airdrop reload`
prepares the main config, locale, package registry, and economy provider before
publishing any of them. A parse or validation failure leaves the complete
previous live state in place.

Drop settings and limits are copied into a request when it begins. A successful
reload therefore affects future requests, not a crate already falling or
landed. Existing expiry deadlines do not change. If a new capacity is below
current use, Airdrop blocks new requests until occupancy falls below the limit;
it does not remove existing crates.

Each anchored row below is part of the administrator contract. "Fallback" is
the value used when a setting is missing, has the wrong type, or falls outside
its accepted range unless the row says otherwise.

| Key | Type | Shipped default | Range or allowed values | Fallback | Reload behavior |
| --- | --- | --- | --- | --- | --- |
| <a id="config-language"></a> `language` | string | `en` | Lowercase ISO-style code such as `en`, or language-region such as `pt-BR` | `en` for blank or unsafe codes | Subsequent messages after a successful full reload |
| <a id="config-drop-parachute-chicken-count"></a> `drop.parachute.chicken-count` | integer | `5` | `1` to `64` inclusive | `5` | Future requests after a successful full reload |
| <a id="config-drop-particles-landing-effects"></a> `drop.particles.landing-effects` | boolean | `true` | `true` or `false`; one-shot particles when the barrel lands | `true` | Future requests after a successful full reload |
| <a id="config-drop-particles-continuous-effects"></a> `drop.particles.continuous-effects` | boolean | `true` | `true` or `false`; repeating landed-crate glow | `true` | Future requests after a successful full reload |
| <a id="config-drop-particles-flare-effects"></a> `drop.particles.flare-effects` | boolean | `true` | `true` or `false`; ground flare while the crate is falling | `true` | Future requests after a successful full reload |
| <a id="config-drop-particles-smoke-enabled"></a> `drop.particles.smoke.enabled` | boolean | `false` | `true` or `false`; repeating smoke above a landed crate | `false` | Future requests after a successful full reload |
| <a id="config-drop-particles-smoke-height"></a> `drop.particles.smoke.height` | integer | `20` | `0` to `128` blocks inclusive | `20` | Future requests after a successful full reload |
| <a id="config-drop-falling-speed"></a> `drop.falling-speed` | finite number | `0.3` | `0.01` to `4.0` blocks per tick inclusive | Legacy `drop.parachute.falling-speed`, then `0.3` | Future requests after a successful full reload |
| <a id="config-drop-height"></a> `drop.height` | integer | `100` | `1` to `320` blocks above the landing surface inclusive | `100` | Future requests after a successful full reload |
| <a id="config-drop-limits-request-cooldown-seconds"></a> `drop.limits.request-cooldown-seconds` | integer | `30` | `1` to `86400` seconds inclusive | `30` | Future player requests after a successful full reload |
| <a id="config-drop-limits-max-falling"></a> `drop.limits.max-falling` | integer | `3` | `1` to `64` inclusive | `3` | Future admission decisions after a successful full reload |
| <a id="config-drop-limits-max-landed"></a> `drop.limits.max-landed` | integer | `10` | `1` to `256` inclusive | `10` | Future admission decisions after a successful full reload |
| <a id="config-drop-limits-landed-lifetime-seconds"></a> `drop.limits.landed-lifetime-seconds` | integer | `600` | `30` to `86400` seconds inclusive | `600` | Future crates; existing deadlines remain unchanged |
| <a id="config-economy-enabled"></a> `economy.enabled` | boolean | `true` | `true` or `false`; false blocks priced player requests | `true` | Provider discovery and future priced requests after a successful full reload |
| <a id="config-logging-debug"></a> `logging.debug` | boolean | `false` | `true` or `false` | `false` | Takes effect when the successful full reload publishes |
| <a id="config-ui-chat-colors-primary"></a> `ui.chat.colors.primary` | string | `BLUE` | Any Bukkit `ChatColor` enum name, case-insensitive | `BLUE` | Subsequent messages after a successful full reload |
| <a id="config-ui-chat-colors-text"></a> `ui.chat.colors.text` | string | `WHITE` | Any Bukkit `ChatColor` enum name, case-insensitive | `WHITE` | Subsequent messages after a successful full reload |
| <a id="config-ui-chat-colors-accent"></a> `ui.chat.colors.accent` | string | `AQUA` | Any Bukkit `ChatColor` enum name, case-insensitive | `AQUA` | Subsequent messages after a successful full reload |
| <a id="config-ui-chat-colors-success"></a> `ui.chat.colors.success` | string | `GREEN` | Any Bukkit `ChatColor` enum name, case-insensitive | `GREEN` | Subsequent messages after a successful full reload |
| <a id="config-ui-chat-colors-warning"></a> `ui.chat.colors.warning` | string | `YELLOW` | Any Bukkit `ChatColor` enum name, case-insensitive | `YELLOW` | Subsequent messages after a successful full reload |
| <a id="config-ui-chat-colors-error"></a> `ui.chat.colors.error` | string | `RED` | Any Bukkit `ChatColor` enum name, case-insensitive | `RED` | Subsequent messages after a successful full reload |
| <a id="config-ui-chat-colors-error-detail"></a> `ui.chat.colors.error-detail` | string | `DARK_RED` | Any Bukkit `ChatColor` enum name, case-insensitive | `DARK_RED` | Subsequent messages after a successful full reload |

Landing particles run once. Continuous glow and landed smoke repeat only while
Airdrop tracks that barrel. They stop when tracking ends because the crate is
emptied, broken, removed, unloaded, disabled, or reaches its configured
lifetime. The smoke setting does not create a trail behind the falling entity.

`landed-lifetime-seconds` is a tracking lifetime, not a promise to delete every
barrel. When a paid barrel expires with items still inside, Airdrop removes its
metadata and effects and leaves it as an ordinary barrel rather than deleting
paid contents.

## Package files

Package definitions live in `plugins/Airdrop/packages.yml`. Prefer the in-game
editor because it writes valid Bukkit item metadata and prevents editor control
items from becoming rewards. Manual definitions use this shape; adjust the
prices and rewards for your server:

<!-- packages-example:start -->
```yaml
packages:
  starter:
    price: 10.0
    items:
      - ==: org.bukkit.inventory.ItemStack
        schema_version: 1
        id: minecraft:bread
        count: 16
      - ==: org.bukkit.inventory.ItemStack
        schema_version: 1
        id: minecraft:torch
        count: 32
  premium:
    price: 25.5
    items:
      - ==: org.bukkit.inventory.ItemStack
        schema_version: 1
        id: minecraft:diamond
        count: 1
```
<!-- packages-example:end -->

The example is parsed by the same Bukkit `YamlConfiguration` and package
materializer used at runtime as part of the automated documentation test.

Package names may contain letters, numbers, underscores, and dashes. Identity
and permissions are case-insensitive, so `Starter` and `starter` conflict.
`all`, `*`, `package`, `packages`, `version`, `status`, `reload`, `create`, and
`delete` are reserved command identities.

Every package needs a numeric, finite, non-negative `price`. `0` is free.
Quoted numbers are strings and are rejected, as are missing prices, negative
numbers, `NaN`, and infinities. A malformed package section, invalid name,
case-insensitive collision, or invalid price rejects the whole candidate. A
failed reload keeps the previous complete registry; Airdrop never substitutes a
zero price for an invalid price.

The required `items` list uses Bukkit's item serialization, including the `==`
type tag. Item ID, count, enchantments, custom names, lore, and other metadata
are represented by the fields Bukkit writes. A missing list, non-item value,
or null entry rejects the whole candidate; item errors identify the package
and entry index. Ordinary reward items keep their custom names, including names
that match editor controls.

Air and item stacks with a non-positive amount are filtered out. Airdrop retains
the first 27 deliverable stacks in YAML order, matching barrel capacity, and
drops later stacks. Free packages may use `items: []`; paid packages must retain
at least one deliverable stack.

Airdrop supports at most 27 configured packages, matching the package browser's
capacity; pagination is not supported. Exceeding that limit or supplying invalid
package data rejects the reload and preserves the previous complete registry.

### Backups make regeneration and rollback predictable

Back up the Airdrop JAR, `config.yml`, `packages.yml`, and custom files under
`lang/` before an upgrade or manual edit. Keep the backup outside
`plugins/Airdrop/` so startup cannot mistake it for a live file.

On startup only, a missing `config.yml` is recreated from the shipped defaults,
a missing `packages.yml` gets the starter package priced at `10.0`, and a
missing bundled `lang/en.yml` is copied into place. `/airdrop reload` does not
regenerate a missing main or package file; it fails and retains the live state.

To regenerate one file safely:

1. Stop Paper and copy the current Airdrop data directory to a backup location.
2. Move only the damaged file out of `plugins/Airdrop/`.
3. Start Paper once and confirm the generated file and `/airdrop status`.
4. Stop Paper, reapply only understood settings or package definitions, then
   start it again.

To roll back, stop Paper, restore a mutually matching JAR and data-directory
backup, then start the server. Do not overwrite live files while Paper is
running. For an upgrade, stop the server, back up the old JAR and data, replace
the JAR, start it, review startup diagnostics and new defaults, and test a
configured package with an administrator account before allowing player requests.
Priced test requests charge the administrator too. Missing English locale keys
are merged from the bundled file. New main-config defaults can be active without
being written into an older `config.yml`, so compare that file with the shipped
template.

## Localization

`language` selects `plugins/Airdrop/lang/<code>.yml`. Codes must match lowercase
two-letter language form such as `en`, optionally followed by a hyphen and an
uppercase region such as `pt-BR`. Unsafe or blank codes fall back to `en`.

The English locale is bundled. A missing local English file is recreated, and
new bundled keys are added without replacing existing translations. For a safe
custom code with no bundled file, Airdrop loads the local file if present and
uses its hard-coded English message default for each missing or blank key.

To create a translation, copy `plugins/Airdrop/lang/en.yml` to a valid code such
as `de.yml`, translate values without changing YAML keys, set `language: de`,
and run `/airdrop reload`. Keep named placeholders such as `{name}`, `{price}`,
`{seconds}`, and `{provider}` intact. Theme placeholders such as `{primary}`,
`{text}`, `{accent}`, `{success}`, `{warning}`, `{error}`, and `{error-detail}`
use the colors from `config.yml`. Literal legacy color codes use `&`, for
example `&6` for gold. Quote YAML values that contain `: `, `#`, or other YAML
syntax.

## Troubleshooting

- **Airdrop does not enable:** confirm Paper `1.21.11` and Java `21` first.
  LuckPerms and economy plugins are optional, so their absence alone should not
  stop Airdrop. Read the first Airdrop startup error; invalid required YAML can
  prevent readiness.
- **Airdrop stays in `STARTING` or reports `FAILED`:** run `/airdrop status` from
  the console or as an operator. It remains available before readiness and
  reports a bounded, sanitized diagnostic without exposing a stack trace.
- **A priced package is unavailable:** confirm `economy.enabled: true`, a loaded
  VaultUnlocked or Vault bridge, and a registered economy provider in
  `/airdrop status`. After installing or changing providers, restart or run
  `/airdrop reload`. Free packages should still work.
- **A player cannot request a package:** inspect it with `/airdrop package
  <name>`, then grant `airdrop.package.<canonical-name>` or
  `airdrop.package.all`. LuckPerms itself is not required.
- **YAML fails to load:** use spaces, not tabs; preserve indentation; quote
  values containing YAML punctuation; and validate the reported file. At
  startup, fix the file and restart. After a failed reload, the previous config,
  locale, packages, and economy state remain active; the invalid disk contents
  are not applied.
- **A package reload fails:** check every package section, name, and price. One
  invalid candidate rejects the complete registry. Fix the first reported
  error and rerun `/airdrop reload`; do not assume the bad package was skipped.
- **The sky is not clear:** Airdrop resolves the highest block at the requested
  X/Z position. Move outside or above roofs and overhangs. Changing
  `drop.height` changes spawn height above the landing surface; it does not make
  an indoor request open to the sky.
- **A request hits a limit:** `/airdrop status` separates pending, falling, and
  landed occupancy. Wait for the relevant crate to retire or raise the bounded
  limit and reload. Lowering a limit never evicts active drops.
- **A reload fails but old behavior continues:** this is the fail-safe contract.
  Airdrop retains the last complete live state. Correct the on-disk file,
  reload again, then confirm readiness and the current diagnostic.
- **A payment result is unknown:** do not repeat the request automatically.
  Check the economy provider's ledger and the Airdrop request UUID before
  deciding whether to compensate a player. Unknown means Airdrop cannot prove
  whether the provider applied the operation.

For a support report, collect `/airdrop version`, `/airdrop status`, the exact
time and request UUID, and the smallest relevant startup/reload log window.
Enable `logging.debug` only while reproducing the problem. Remove access tokens,
IP addresses, player identifiers, unrelated paths, balances, and inventory
contents before sharing logs or configuration. Never publish a full server log
or economy database.

## Developer integration

The supported compatibility boundary is `com.airdropmc.api` and its
`com.airdropmc.api.event` package. Controllers, managers, configuration
wrappers, `Crate`, and `com.airdropmc.events` are implementation details.

### Compile against Airdrop without shading it

Do not shade Airdrop into a consumer plugin. Use the released plugin as a
compile-only dependency, and declare Paper explicitly because Airdrop's Maven
POM deliberately has no transitive dependencies. A `v4.1.0` release tag is
published at Maven version `4.1.0`; the tag prefix is never part of the
consumer coordinate. For Gradle:

```kotlin
repositories {
    maven("https://api.modrinth.com/maven")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("maven.modrinth:airdrop:4.1.0")
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}
```

For Maven, use the same Modrinth coordinate and mark both plugins as provided:

```xml
<repositories>
  <repository>
    <id>modrinth</id>
    <url>https://api.modrinth.com/maven</url>
  </repository>
  <repository>
    <id>papermc</id>
    <url>https://repo.papermc.io/repository/maven-public/</url>
  </repository>
</repositories>
<dependencies>
  <dependency>
    <groupId>maven.modrinth</groupId>
    <artifactId>airdrop</artifactId>
    <version>4.1.0</version>
    <scope>provided</scope>
  </dependency>
  <dependency>
    <groupId>io.papermc.paper</groupId>
    <artifactId>paper-api</artifactId>
    <version>1.21.11-R0.1-SNAPSHOT</version>
    <scope>provided</scope>
  </dependency>
</dependencies>
```

Use a hard dependency when your plugin cannot run without Airdrop:

```yaml
depend: [Airdrop]
```

If the integration is optional, use `softdepend: [Airdrop]` and check plugin
availability before loading any class that references Airdrop API types:

```yaml
softdepend: [Airdrop]
```

Match the plugin release to the documented extension API version. The plugin
and extension API have independent versions; see the
[API version policy](https://github.com/LukeMccon/Airdrop/blob/main/docs/development/api-versioning.md)
and [Airdrop 4.1 migration guide](https://github.com/LukeMccon/Airdrop/blob/main/docs/migration-4.1.md).

### Discover the service, then schedule Bukkit work

A soft dependency controls plugin load order; it does not supply API classes
when Airdrop is absent. Keep Airdrop API fields, method signatures, and event
listeners in a separate integration class. The plugin entry point checks
availability before calling that class. In `ExamplePlugin.java`:

<!-- optional-example:ExamplePlugin.java -->
```java
package dev.airdropmc.example;

import org.bukkit.plugin.java.JavaPlugin;

public final class ExamplePlugin extends JavaPlugin {
	@Override
	public void onEnable() {
		if (getServer().getPluginManager().isPluginEnabled("Airdrop")) {
			AirdropIntegration.enable(this);
		}
	}
}
```

Airdrop registers `AirdropApi` before asynchronous startup begins. In the
separate `AirdropIntegration.java`, load it through Bukkit's `ServicesManager`
and attach to its readiness stage. The missing-service guard is still needed
if registration is unavailable. Service discovery and an enabled plugin do
not imply `ReadinessState.READY`:

<!-- optional-example:AirdropIntegration.java -->
```java
package dev.airdropmc.example;

import com.airdropmc.api.AirdropApi;
import org.bukkit.plugin.java.JavaPlugin;

final class AirdropIntegration {
	private AirdropIntegration() {
	}

	static void enable(JavaPlugin plugin) {
		AirdropApi api = plugin.getServer().getServicesManager().load(AirdropApi.class);
		if (api == null) {
			return;
		}

		api.readiness().whenComplete((ready, failure) -> {
			plugin.getServer().getScheduler().runTask(plugin, () -> {
				if (failure != null) {
					plugin.getLogger().warning("Airdrop did not become ready");
					return;
				}
				plugin.getLogger().info("Airdrop API "
						+ ready.versions().extensionApiVersion() + " is ready");
			});
		});
	}
}
```

These two files are compiled in the consumer fixture and tested with Airdrop
API classes unavailable, a missing service, and readiness success or failure.
If Airdrop or its service is unavailable, the consumer skips the integration
and continues operating.

Airdrop completes its backing readiness transition on the primary server
thread. A continuation attached after completion can still run on the thread
that attached it, so a continuation is not a Bukkit scheduler. Schedule Bukkit
work explicitly, as the example does. Airdrop unregisters the service during
disable; do not retain a provider across plugin reloads.

Calls involving Bukkit `Player`, `Location`, `FallingBlock`, `Block`, or copied
`ItemStack` values require the primary server thread. This includes package
item snapshots and requests. Lifecycle state, versions, UUID lookups, status
primitive fields, `WorldPosition`, and the aggregate active-drop snapshot can
be read off-thread where their Javadocs say so.

### Requests return typed spawn and terminal stages

Player requests enforce permissions, admission limits, and economy rules.
System requests are explicitly unpaid:

```java
DropHandle playerDrop = api.requestPlayerDrop(
        player, "starter", DropRequestOptions.defaults());
DropHandle systemDrop = api.requestSystemDrop(
        targetLocation, "starter", DropRequestOptions.defaults());

UUID requestId = playerDrop.descriptor().requestId();
playerDrop.spawn().thenAccept(spawn -> {
    if (spawn instanceof DropSpawnResult.NotSpawned notSpawned) {
        getLogger().info("Did not spawn: " + notSpawned.outcome().delivery());
    }
});
playerDrop.outcome().thenAccept(outcome -> {
    switch (outcome) {
        case DropOutcome.Rejected rejected -> getLogger().info(
                rejected.rejection().reason().name());
        case DropOutcome.Landed landed -> getLogger().info(
                landed.airdrop().crateId().toString());
        case DropOutcome.Failed failed -> getLogger().info(
                failed.delivery() + "/" + failed.payment());
    }
});
```

`descriptor()` and its request UUID always exist. `context()` is empty until
package, target, and settings resolution succeeds. `spawn()` completes when a
falling crate commits or can no longer spawn; `outcome()` completes once with
the final `DeliveryStatus` and `PaymentStatus`.

Expected pre-spawn rejections are `DropOutcome.Rejected` values with a
`DropRejectionReason`. Later failures use `DropOutcome.Failed` with delivery
status `FAILED`, `CANCELLED`, or `SHUTDOWN` and a final payment status. Neither
path relies on exceptions. Null arguments, off-thread Bukkit access, and other
programmer errors fail immediately. Stage callbacks have the same scheduler
warning as readiness:
schedule any Bukkit work yourself.

### Queries return immutable active-drop snapshots

`activeDrops()` returns the latest immutable aggregate snapshot. Pure UUID
lookups use `findByRequestId(requestId)` and `findByCrateId(crateId)`.
`findByFallingEntity(entity)` and `findByLandedBlock(block)` inspect Bukkit
objects and therefore require the primary server thread.

Falling positions refresh every two server ticks. Fetch a new view to observe
movement; a retained view keeps its original position. Landing-attempt events
capture the entity position when the event fires.

Views expose correlation IDs, phase, package identity, detached positions,
source, and optional recovery data. They never expose a live crate, mutable
registry, controller, or lease. Package snapshots are also immutable, but
their copied item values keep `listPackages()` and `findPackage(name)` on the
primary thread. The supported API intentionally has no package-mutation method.

### Events expose ordering and cancellation boundaries

All supported events are synchronous on the primary thread and carry immutable
snapshots. A resolved successful request has this order:

1. `AirdropRequestEvent` — cancellable before admission, cooldown, payment, or
   entity side effects.
2. `AirdropSpawnedEvent` — after the falling crate and admission state commit.
3. Deprecated `PackageDropEvent` — unsupported post-state adapter outside the
   compatibility boundary.
4. `AirdropLandingAttemptEvent` — cancellable before block, inventory, index,
   lease, or delivery mutation.
5. `AirdropLandedEvent` — after the barrel and landed index commit.
6. Deprecated `PackageLandEvent` — unsupported post-state adapter outside the
   compatibility boundary.
7. `AirdropOutcomeEvent` — exactly once after delivery and payment are final.

Resolution failures fire no request event. Cancelling a request event leaves
no admission, payment, entity, task, or cooldown side effect. Cancelling a
landing attempt removes the falling crate, releases admission, and makes at
most the single documented refund attempt after a confirmed charge.

Protection plugins can cancel `AirdropLandingAttemptEvent` for an Airdrop-only
rule. They can also cancel Paper's `EntityChangeBlockEvent`; Airdrop continues
to honor that independent native protection boundary. Use
`findByFallingEntity` or another supported query for correlation. Inventory
open, close, break, burn, explosion, and hopper behavior stays observable
through Paper events plus an API lookup; Airdrop does not duplicate those
native events.

`PackageRegistryChangedEvent` fires after a successful complete registry
publication. Its immutable change has a monotonic `revision`, a
`PackageRegistryCause`, the complete package map, and `created`, `updated`, and
`deleted` diffs. Failed writes or reloads publish no revision or event.

`AirdropRecoveredEvent` fires after a persisted landed crate enters the active
index. Recovery data and the original request UUID are optional, and recovery
does not synthesize request, spawn, landing, or outcome events.
`AirdropRetiredEvent` fires after a view leaves every active index and includes
a `RetirementReason`. These events let consumers track registry and active-drop
state without depending on mutable implementation maps.

### Treat delivery and payment as separate results

Delivery can be `REJECTED`, `FAILED`, `CANCELLED`, `SHUTDOWN`, or `LANDED`.
Payment can be `NOT_APPLICABLE`, `REJECTED`, `CHARGED`, `REFUNDED`,
`REFUND_FAILED`, or `UNKNOWN`. Free and system requests use `NOT_APPLICABLE`.
A landed paid request can be `CHARGED`; a failed paid request can report a
known refund result.

`UNKNOWN` means Airdrop cannot prove whether an economy callback applied the
operation, often after a timeout or shutdown. Correlate the request UUID with
the provider's ledger, and never retry an `UNKNOWN` payment automatically.
An automatic retry could duplicate money or items. Late or duplicate provider
callbacks cannot produce a second outcome or repeat a withdrawal/refund.

## Project and support

- Canonical project page: [Modrinth](https://modrinth.com/plugin/airdrop)
- Source code: [GitHub](https://github.com/LukeMccon/Airdrop)
- Downloads and release history: [Modrinth versions](https://modrinth.com/plugin/airdrop/versions)
- Bugs: [open a bug report](https://github.com/LukeMccon/Airdrop/issues/new?labels=bug)
- Feature ideas: [open a feature request](https://github.com/LukeMccon/Airdrop/issues/new?labels=enhancement)
- License: [MIT](https://github.com/LukeMccon/Airdrop/blob/main/LICENSE)
