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

### Free quick start

When `plugins/Airdrop/packages.yml` is absent on the first startup, Airdrop
creates a free `starter` package with iron armor and bread. Request it in game:

```text
/airdrop starter
```

Operators can use every package. To let another player request the starter,
grant `airdrop.package.starter` with any Bukkit-compatible permission manager.
This path needs neither LuckPerms nor an economy plugin.

First-start provisioning occurs only when `packages.yml` is absent. An existing
file containing `packages: {}` is an intentionally empty registry and is not
silently populated.

### Paid setup

1. Install VaultUnlocked or Vault and an economy plugin that registers a
   compatible provider.
2. Keep `economy.enabled: true` in `plugins/Airdrop/config.yml`.
3. Start the server and confirm the provider with `/airdrop status`.
4. In game as an operator, run `/airdrop package create premium 10`.
5. Add items in the editor, save the package, and grant
   `airdrop.package.premium` to the intended players.

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
<a id="config-language"></a> | `language` | string | `en` | Lowercase ISO-style code such as `en`, or language-region such as `pt-BR` | `en` for blank or unsafe codes | Subsequent messages after a successful full reload |
<a id="config-drop-parachute-chicken-count"></a> | `drop.parachute.chicken-count` | integer | `5` | `1` to `64` inclusive | `5` | Future requests after a successful full reload |
<a id="config-drop-particles-landing-effects"></a> | `drop.particles.landing-effects` | boolean | `true` | `true` or `false`; one-shot particles when the barrel lands | `true` | Future requests after a successful full reload |
<a id="config-drop-particles-continuous-effects"></a> | `drop.particles.continuous-effects` | boolean | `true` | `true` or `false`; repeating landed-crate glow | `true` | Future requests after a successful full reload |
<a id="config-drop-particles-flare-effects"></a> | `drop.particles.flare-effects` | boolean | `true` | `true` or `false`; ground flare while the crate is falling | `true` | Future requests after a successful full reload |
<a id="config-drop-particles-smoke-enabled"></a> | `drop.particles.smoke.enabled` | boolean | `false` | `true` or `false`; repeating smoke above a landed crate | `false` | Future requests after a successful full reload |
<a id="config-drop-particles-smoke-height"></a> | `drop.particles.smoke.height` | integer | `20` | `0` to `128` blocks inclusive | `20` | Future requests after a successful full reload |
<a id="config-drop-falling-speed"></a> | `drop.falling-speed` | finite number | `0.3` | `0.01` to `4.0` blocks per tick inclusive | Legacy `drop.parachute.falling-speed`, then `0.3` | Future requests after a successful full reload |
<a id="config-drop-height"></a> | `drop.height` | integer | `100` | `1` to `320` blocks above the landing surface inclusive | `100` | Future requests after a successful full reload |
<a id="config-drop-limits-request-cooldown-seconds"></a> | `drop.limits.request-cooldown-seconds` | integer | `30` | `1` to `86400` seconds inclusive | `30` | Future player requests after a successful full reload |
<a id="config-drop-limits-max-falling"></a> | `drop.limits.max-falling` | integer | `3` | `1` to `64` inclusive | `3` | Future admission decisions after a successful full reload |
<a id="config-drop-limits-max-landed"></a> | `drop.limits.max-landed` | integer | `10` | `1` to `256` inclusive | `10` | Future admission decisions after a successful full reload |
<a id="config-drop-limits-landed-lifetime-seconds"></a> | `drop.limits.landed-lifetime-seconds` | integer | `600` | `30` to `86400` seconds inclusive | `600` | Future crates; existing deadlines remain unchanged |
<a id="config-economy-enabled"></a> | `economy.enabled` | boolean | `true` | `true` or `false`; false blocks priced player requests | `true` | Provider discovery and future priced requests after a successful full reload |
<a id="config-logging-debug"></a> | `logging.debug` | boolean | `false` | `true` or `false` | `false` | Takes effect when the successful full reload publishes |
<a id="config-ui-chat-colors-primary"></a> | `ui.chat.colors.primary` | string | `BLUE` | Any Bukkit `ChatColor` enum name, case-insensitive | `BLUE` | Subsequent messages after a successful full reload |
<a id="config-ui-chat-colors-text"></a> | `ui.chat.colors.text` | string | `WHITE` | Any Bukkit `ChatColor` enum name, case-insensitive | `WHITE` | Subsequent messages after a successful full reload |
<a id="config-ui-chat-colors-accent"></a> | `ui.chat.colors.accent` | string | `AQUA` | Any Bukkit `ChatColor` enum name, case-insensitive | `AQUA` | Subsequent messages after a successful full reload |
<a id="config-ui-chat-colors-success"></a> | `ui.chat.colors.success` | string | `GREEN` | Any Bukkit `ChatColor` enum name, case-insensitive | `GREEN` | Subsequent messages after a successful full reload |
<a id="config-ui-chat-colors-warning"></a> | `ui.chat.colors.warning` | string | `YELLOW` | Any Bukkit `ChatColor` enum name, case-insensitive | `YELLOW` | Subsequent messages after a successful full reload |
<a id="config-ui-chat-colors-error"></a> | `ui.chat.colors.error` | string | `RED` | Any Bukkit `ChatColor` enum name, case-insensitive | `RED` | Subsequent messages after a successful full reload |
<a id="config-ui-chat-colors-error-detail"></a> | `ui.chat.colors.error-detail` | string | `DARK_RED` | Any Bukkit `ChatColor` enum name, case-insensitive | `DARK_RED` | Subsequent messages after a successful full reload |

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
items from becoming rewards. Manual definitions use this shape:

<!-- packages-example:start -->
```yaml
packages:
  starter:
    price: 0.0
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

The `items` list uses Bukkit's item serialization, including the `==` type tag.
Item ID, count, enchantments, custom names, lore, and other metadata are
represented by the fields Bukkit writes. Non-item values, null entries, air,
and localized editor-control items are filtered out. After filtering, Airdrop
retains the first 27 stacks in YAML order, matching barrel capacity, and drops
later stacks. Structural, name, and price errors reject the candidate; an
unusable item entry is filtered instead.

### Backups make regeneration and rollback predictable

Back up the Airdrop JAR, `config.yml`, `packages.yml`, and custom files under
`lang/` before an upgrade or manual edit. Keep the backup outside
`plugins/Airdrop/` so startup cannot mistake it for a live file.

On startup only, a missing `config.yml` is recreated from the shipped defaults,
a missing `packages.yml` gets the free starter package, and a missing bundled
`lang/en.yml` is copied into place. `/airdrop reload` does not regenerate a
missing main or package file; it fails and retains the live state.

To regenerate one file safely:

1. Stop Paper and copy the current Airdrop data directory to a backup location.
2. Move only the damaged file out of `plugins/Airdrop/`.
3. Start Paper once and confirm the generated file and `/airdrop status`.
4. Stop Paper, reapply only understood settings or package definitions, then
   start it again.

To roll back, stop Paper, restore a mutually matching JAR and data-directory
backup, then start the server. Do not overwrite live files while Paper is
running. For an upgrade, stop the server, back up the old JAR and data, replace
the JAR, start it, review startup diagnostics and new defaults, and test one free
drop before enabling paid traffic. Missing English locale keys are merged from
the bundled file. New main-config defaults can be active without being written
into an older `config.yml`, so compare that file with the shipped template.

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

Do not shade Airdrop into a consumer plugin. Declare Airdrop as `depend` or
`softdepend`, compile against the matching Airdrop artifact, and discover
`com.airdropmc.api.AirdropApi` through Bukkit's `ServicesManager`:

```java
AirdropApi api = getServer().getServicesManager().load(AirdropApi.class);
if (api == null) {
    return;
}
api.readiness().thenAccept(ready -> getLogger().info(
        "Airdrop API " + ready.versions().extensionApiVersion() + " is ready"));
```

The supported compatibility boundary is `com.airdropmc.api`. Controllers,
managers, configuration wrappers, `Crate`, and legacy events are implementation
details. The API exposes immutable package and status snapshots, typed
asynchronous request handles and outcomes, active-drop queries, and synchronous
lifecycle events under `com.airdropmc.api.event`.

Calls involving Bukkit `Player`, `Location`, `Entity`, `Block`, or copied item
values require the primary server thread. Pure version, readiness state, UUID,
and detached aggregate fields may be read off-thread. A stage continuation is
not an implicit Bukkit scheduler; schedule Bukkit work on the primary thread.

Normal event order is request, spawned, landing attempt, landed, then terminal
outcome. Request and landing-attempt events are cancellable before their
respective side effects. Treat delivery and payment as separate states, retain
the request UUID for correlation, and do not retry an outcome whose payment is
unknown.

Protection plugins should cancel Paper's `EntityChangeBlockEvent` before
Airdrop commits the landing. Query the supported API for correlation instead of
depending on mutable internal classes.

## Project and support

- Canonical project page: [Modrinth](https://modrinth.com/plugin/airdrop)
- Source code: [GitHub](https://github.com/LukeMccon/Airdrop)
- Downloads and release history: [Modrinth versions](https://modrinth.com/plugin/airdrop/versions)
- Bugs: [open a bug report](https://github.com/LukeMccon/Airdrop/issues/new?labels=bug)
- Feature ideas: [open a feature request](https://github.com/LukeMccon/Airdrop/issues/new?labels=enhancement)
- License: [MIT](https://github.com/LukeMccon/Airdrop/blob/main/LICENSE)
