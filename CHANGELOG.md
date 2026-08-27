# Airdrop 4.0 — Pre-release Changelog

> This is a draft for testing. Airdrop 4.0 has not been released yet.
>
> Compared with the latest stable 3.x release, Airdrop 3.2.0.

Airdrop 4.0 is a major update focused on giving server owners more control over drops while making packages, crates, and economy transactions much safer and more reliable.

## Highlights

### More control over every airdrop

- Configure drop height, falling speed, and the number of parachute chickens.
- Toggle landing, continuous glow, flare, and smoke effects independently.
- Configure the height of the smoke trail.
- Out-of-range numeric drop settings now fall back to safe defaults.

### New cooldowns and server-wide limits

- Add a per-player request cooldown to reduce drop spam.
- Limit how many crates may be falling or claiming landed capacity at once. Falling crates reserve a landed slot before they spawn.
- Prevent multiple drops from claiming the same landing location.
- Automatically stop tracking old landed crates. Unpaid crates and empty paid barrels are removed, while non-empty paid barrels become ordinary barrels so their contents are preserved.
- Grant `airdrop.cooldown.bypass` to let trusted players skip the cooldown and request another drop while an earlier request is still pending. Falling, landed, and location limits still apply.

### Safer package creation and editing

- Package editor interactions have been rebuilt to protect player inventories from accidental duplication or loss while editing.
- Package contents are isolated so one drop or editor session cannot alter future drops.
- Package saves are now transactional: a failed write keeps the previous package data and reports the failure instead of leaving a partial update.
- The package editor accepts up to the barrel's 27-stack capacity and provides feedback when capacity is reached.
- Package prices must be valid, finite, and non-negative.
- Package names are now case-insensitive and may contain letters, numbers, underscores, and dashes. Command and permission names such as `all`, `package`, `packages`, `version`, and `reload` are reserved.
- Command tab completion now suggests configured package names, and GUI package selection preserves their display names.

### More reliable economy handling

- Add native asynchronous support for VaultUnlocked, with original Vault retained as a compatibility fallback.
- Economy support can now be disabled in `config.yml` for servers that want free drops.
- Drop capacity and landing space are reserved before a player is charged.
- A crate is spawned only after the economy provider confirms payment.
- While Airdrop remains active, a confirmed payment receives one best-effort refund attempt when a known failure prevents the crate from landing.
- Landed paid barrels can be recovered after graceful chunk saves, world unloads, or server shutdowns without recreating their inventories.

Paid-drop handling is deliberately fail-closed to prevent money or item duplication. A confirmed paid crate that is still falling when shutdown begins is removed without an automatic refund. An ambiguous economy timeout, or a withdrawal completing as shutdown begins, can also result in a charge without delivery; Airdrop does not automatically retry or refund those ambiguous operations.

### Localization and appearance

- Add language files under `plugins/Airdrop/lang/`, starting with English.
- Add configurable colors for normal text, accents, success messages, warnings, and errors.
- Improve command feedback with clearer, consistent messages.

### New configuration reload command

- Add `/airdrop reload` to reload `config.yml`, the selected language, `packages.yml`, and the economy provider together.
- A failed reload keeps the current working configuration active.
- Updated limits apply to future drops without deleting active crates or resetting existing cooldowns.

## Reliability and bug fixes

- Respect protection plugins that cancel a crate's landing.
- Respect cancelled block breaks, burns, and explosions without losing track of a crate.
- Keep crate tracking consistent across chunk unloads, world unloads, plugin shutdowns, and failed landings.
- Falling crates and unpaid landed crates are removed when their chunk or world unloads or Airdrop shuts down, including any remaining contents. Eligible paid barrels are recoverable only after a graceful unload or server shutdown.
- Clean up empty crates after their contents are drained by hoppers.
- Stop empty ordinary barrels from being mistaken for Airdrop crates and removed.
- Preserve overflowing items at the landing location instead of silently deleting them.
- Fix several parachute cleanup issues, stuck falling entities, and mutable-location errors.
- Schedule landing, glow, and smoke effect work on Paper's server thread.
- Improve package and crate state tracking to prevent stale entries, duplicate locations, and cleanup races.
- Remove active falling crates during shutdown and prepare eligible landed paid barrels for recovery during a graceful server stop. Hot plugin disable removes active crates instead of making them recoverable.

## Commands and permissions

- All existing commands continue to use `/airdrop`, `/drop`, or `/ad`.
- Package permissions are now consistently case-insensitive.
- Permission nodes and inheritance are declared directly by the plugin.
- Package creation, deletion, and the package-management GUI now require administrative permission.
- New permission: `airdrop.cooldown.bypass`.
- `airdrop.admin` includes package access and cooldown bypass.

## Upgrading from 3.x

- Airdrop 4.0 requires **Paper 1.21.8 or newer** and **Java 21**.
- LuckPerms remains required.
- EssentialsX is no longer required by Airdrop.
- VaultUnlocked is preferred for paid packages, but original Vault remains supported as the legacy fallback. Existing Vault-based servers do not need to switch. Airdrop does not bundle either bridge, so keep VaultUnlocked or Vault installed with a compatible economy plugin. Without an economy provider, Airdrop still starts: packages priced above zero are blocked and free packages remain available. Set `economy.enabled: false` to make all packages free.
- Back up `plugins/Airdrop/` before upgrading. Existing `packages.yml` data uses the same overall structure, but invalid package names or prices must be fixed before Airdrop can load the file.
- Review existing package contents before editing or saving them in version 4. Only the first 27 eligible item stacks are loaded. Items whose custom display names match localized editor controls such as `Save`, `Cancel`, `Back`, or `Help` are excluded; saving the package later persists that reduced set.
- Stable version 3.2 did not create a `config.yml`; version 4 creates one on first startup with a 30-second request cooldown and server-wide falling, landed, and lifetime limits. Existing configuration files from 3.3/3.4 development builds remain compatible, and missing version 4 settings use their defaults at runtime.

## For plugin developers

Version 4 contains intentional API compatibility changes. Integrations using Airdrop internals must be rebuilt and may require source changes:

- Package affordability checks and charging are now asynchronous and handled by `DropController`; returning from a paid drop call does not mean payment and delivery have finished.
- `Crate` construction now requires a drop-admission lease.
- Public drop methods can reject requests with `DropLimitException`.
- The 3.x `CrateList` API has been replaced by `CrateManager`. Its `addCrate(...)` methods report whether collision-safe registration succeeded.
- New `DropOptions` overloads allow integrations to customize individual drops.
- New `PackageDropEvent` and `PackageLandEvent` hooks expose drop and landing lifecycle events.
