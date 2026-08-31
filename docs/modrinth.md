# Airdrop

Airdrop is a Paper plugin for customizable care packages that descend with a
parachute, land as barrels, and can integrate with permissions, economy, and
protection plugins. It includes in-game package editing, configurable effects,
bounded active-drop limits, localization, and a small read-only Java extension
API.

This file is the canonical Airdrop project description. The same bytes are
published to [the Airdrop Modrinth page](https://modrinth.com/plugin/airdrop).

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
| Paper `1.21.11` | Java `21` | None | LuckPerms; VaultUnlocked or Vault plus an economy provider |

Airdrop starts without LuckPerms or an economy provider. Standard Bukkit
permissions continue to work. Free packages remain usable when economy is
disabled or no provider is available; priced player requests are rejected
without charging or spawning a crate.

## Installation

1. Download the version matching Paper `1.21.11` from
   [Modrinth releases](https://modrinth.com/plugin/airdrop/versions).
2. Put the Airdrop JAR in the server's `plugins/` directory.
3. Start or restart the server with Java `21`.
4. Confirm that Airdrop enables, then run `/airdrop version` for its compatibility
   signals and documentation link.

LuckPerms is optional convenience automation. For priced packages, install
VaultUnlocked or Vault and a compatible economy provider before enabling paid
requests.

### Free quick start

On a first start, Airdrop provisions a free `starter` package. As an operator,
request it once the plugin is ready:

```text
/airdrop starter
```

Grant `airdrop.package.starter` through any Bukkit-compatible permissions
plugin if non-operators should use it. No economy or LuckPerms installation is
needed for this path.

### Paid setup

1. Install VaultUnlocked or Vault and an economy plugin that registers a
   provider.
2. Keep `economy.enabled: true` in `plugins/Airdrop/config.yml`.
3. As an operator in game, run `/airdrop package create premium 10`.
4. Add items in the editor, save it, and grant
   `airdrop.package.premium` to the intended players.

Airdrop never treats a missing provider as a free purchase. Confirmed charges
can receive one best-effort refund after a known delivery failure. Ambiguous
economy timeouts are not retried automatically because doing so could duplicate
money or items.

## Commands and permissions

All commands also accept the `/drop` and `/ad` aliases. Root help is filtered to
what the sender may use.

| Command | Purpose | Access |
| --- | --- | --- |
| `/airdrop` | Show available help | Everyone |
| `/airdrop <package>` | Request a package at the player's location | Player plus `airdrop.package.<package>` or `airdrop.package.all` |
| `/airdrop package <name>` | Inspect a package | Everyone |
| `/airdrop package create <name> <price>` | Create and edit a package | Player plus `airdrop.admin` |
| `/airdrop package delete <name>` | Delete a package | `airdrop.admin` |
| `/airdrop packages` | Open package management | Player plus `airdrop.admin` |
| `/airdrop reload` | Reload configuration, language, packages, and economy discovery | `airdrop.admin` |
| `/airdrop version` | Show plugin, extension API, Paper, Java, and docs signals | Everyone |

`airdrop.cooldown.bypass` bypasses only the per-player request cooldown. It does
not bypass falling or landed capacity limits. Operators receive it by default,
and `airdrop.admin` includes it.

## Configuration

Configuration lives in `plugins/Airdrop/config.yml`. Airdrop validates the
candidate configuration before publishing it; a failed reload retains the
previous live settings.

```yaml
language: en
drop:
  parachute:
    chicken-count: 5
  particles:
    landing-effects: true
    continuous-effects: true
    flare-effects: true
    smoke:
      enabled: false
      height: 20
  falling-speed: 0.3
  height: 100
  limits:
    request-cooldown-seconds: 30
    max-falling: 3
    max-landed: 10
    landed-lifetime-seconds: 600
economy:
  enabled: true
logging:
  debug: false
```

Chat theme colors live below `ui.chat.colors` and use Bukkit `ChatColor` names.
`/airdrop reload` applies new settings to future requests; it does not delete
active crates or reset existing deadlines. Lowering a limit below current use
blocks new drops until occupancy falls below that limit.

Language files live in `plugins/Airdrop/lang/<language>.yml`. Missing keys are
filled from the shipped English defaults. Keep named placeholders intact when
translating messages.

## Package files

Package definitions live in `plugins/Airdrop/packages.yml`. Prefer the in-game
editor so Bukkit item metadata is serialized correctly. A package name may use
letters, numbers, underscores, and dashes. Prices must be finite and
non-negative. Each package holds at most 27 item stacks, matching barrel
capacity.

Back up `config.yml`, `packages.yml`, and custom language files before manual
changes or upgrades. Stop the server before restoring a backup. If a file is
missing, move the remaining copy aside and restart to generate a fresh default,
then reapply only known-good changes.

## Troubleshooting

- **Airdrop is still starting:** wait for startup to finish and inspect the
  server log for the retained configuration or package error.
- **A priced package is unavailable:** verify `economy.enabled`, VaultUnlocked
  or Vault, and an economy provider. Free packages should still work.
- **A player cannot request a package:** grant
  `airdrop.package.<package>` or `airdrop.package.all`; LuckPerms itself is not
  required.
- **The sky is not clear:** move away from overhead blocks or reduce the drop
  height only after checking the surrounding build.
- **Capacity is reached:** wait for falling or landed crates to retire, or
  adjust the configured limits and reload.
- **A reload fails:** the previous configuration remains active. Correct the
  first reported YAML or validation problem and retry `/airdrop reload`.

When reporting a problem, include the Airdrop version, Paper version, Java
version, relevant startup/reload messages, and the smallest redacted
configuration needed to reproduce it. Never post access tokens or private
economy data.

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

The supported compatibility boundary is `com.airdropmc.api`; controllers,
managers, configuration wrappers, `Crate`, and legacy events are internal
implementation details. The API exposes immutable package and status snapshots,
typed asynchronous request handles and outcomes, active-drop queries, and
synchronous lifecycle events under `com.airdropmc.api.event`.

Calls involving Bukkit `Player`, `Location`, `Entity`, `Block`, or copied item
values require the primary server thread. Pure version, readiness state, UUID,
and detached aggregate fields may be read off-thread. A stage continuation is
not an implicit Bukkit scheduler: explicitly schedule Bukkit work on the
primary thread.

Normal event order is request, spawned, landing attempt, landed, then terminal
outcome. Request and landing-attempt events are cancellable before their
respective side effects. Treat delivery and payment as separate states, retain
the request UUID for correlation, and do not retry an outcome whose payment is
reported as unknown.

Protection plugins should cancel Paper's `EntityChangeBlockEvent` before
Airdrop commits the landing. Query the supported API for correlation instead of
depending on Airdrop's mutable internal classes.

## Project and support

- Canonical documentation: [Modrinth](https://modrinth.com/plugin/airdrop)
- Source code: [GitHub](https://github.com/LukeMccon/Airdrop)
- Downloads and release history: [Modrinth versions](https://modrinth.com/plugin/airdrop/versions)
- Bugs: [open a bug report](https://github.com/LukeMccon/Airdrop/issues/new?labels=bug)
- Feature ideas: [open a feature request](https://github.com/LukeMccon/Airdrop/issues/new?labels=enhancement)
- License: [MIT](https://github.com/LukeMccon/Airdrop/blob/main/LICENSE)
