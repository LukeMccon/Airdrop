# Airdrop 4.0 release notes: Safer drops and more control

Airdrop 4.0 makes package editing, crate handling, and economy transactions safer while giving server owners more control over each drop.

These release notes cover changes since Airdrop 3.2.0, the latest stable 3.x release.

## Server owners and players get safer, more configurable drops

### Configure each drop in more detail

- Configure the drop height, falling speed, and number of parachute chickens.
- Toggle landing, continuous glow, flare, and smoke effects independently.
- Set the height of the smoke trail.
- Out-of-range numeric settings fall back to safe defaults.

### Cooldowns and capacity limits keep drops under control

- Set a per-player request cooldown to reduce drop spam.
- Set limits for falling and landed crates. Each falling crate reserves a landed slot before it spawns.
- Prevent multiple drops from claiming the same landing location.
- Automatically stop tracking old landed crates. Airdrop removes unpaid crates and empty paid barrels, but converts non-empty paid barrels into ordinary barrels so their contents remain available.
- Grant `airdrop.cooldown.bypass` to let trusted players request another drop as soon as their previous request finishes. The one-pending-request rule and the falling, landed, and location limits still apply.

### Package editing protects inventories and saved data

- The rebuilt package editor protects player inventories from accidental item duplication or loss.
- Each drop and editor session receives isolated package contents, so neither can alter future drops.
- Package saves are transactional. If a write fails, Airdrop keeps the previous package data and reports the error instead of leaving a partial update.
- The editor accepts the barrel's full capacity of 27 item stacks and tells the player when the barrel is full.
- Package prices must be finite, non-negative numbers.
- Package names are case-insensitive and may contain letters, numbers, underscores, and dashes. Names used by commands and permissions, including `all`, `package`, `packages`, `version`, and `reload`, are reserved.
- Tab completion suggests configured package names, and the package-selection GUI preserves their display names.

### Economy operations complete before crates spawn

- Airdrop now supports VaultUnlocked's asynchronous API natively and retains original Vault as a compatibility fallback.
- Server owners can disable economy support in `config.yml` to make every drop free.
- Airdrop reserves drop capacity and landing space before charging the player.
- A crate spawns only after the economy provider confirms payment.
- If a known failure prevents a paid crate from landing while Airdrop remains active, the plugin makes one best-effort refund attempt.
- Airdrop can recover landed paid barrels after graceful chunk saves, world unloads, or server shutdowns without recreating their inventories.

Airdrop does not automatically refund every paid-drop failure because doing so could duplicate money or items. If shutdown begins while a confirmed paid crate is still falling, Airdrop removes the crate without issuing a refund. A charge may also complete without delivery if an economy request times out without a definitive result or a withdrawal completes as shutdown begins. Airdrop does not automatically retry or refund those transactions.

### Language and color settings make messages easier to customize

- Language files now live under `plugins/Airdrop/lang/`, with English included by default.
- Configure colors for normal text, accents, success messages, warnings, and errors.
- Command feedback is clearer and more consistent.

### Reload configuration without restarting the server

- Use `/airdrop reload` to reload `config.yml`, the selected language, `packages.yml`, and the economy provider together.
- If a reload fails, Airdrop keeps the current working configuration active.
- Updated limits apply only to future drops; active crates and existing cooldowns remain unchanged.

### Crates now clean up or recover more predictably

- Airdrop respects protection plugins that cancel a crate's landing.
- Cancelled block breaks, burns, and explosions no longer cause Airdrop to lose track of a crate.
- Crate tracking remains consistent across chunk unloads, world unloads, plugin shutdowns, and failed landings.
- Airdrop removes falling crates and unpaid landed crates, including their remaining contents, when their chunk or world unloads or when the plugin shuts down.
- Eligible paid barrels remain recoverable after a graceful unload or server shutdown. A hot plugin disable removes active crates instead of making them recoverable.
- Empty crates are cleaned up after hoppers drain their contents.
- Empty ordinary barrels are no longer mistaken for Airdrop crates and removed.
- Items that overflow a crate remain at the landing location instead of being silently deleted.
- Several parachute cleanup bugs, stuck falling entities, and mutable-location errors have been fixed.
- Landing, glow, and smoke effects now run on Paper's server thread.
- Package and crate state tracking has been improved to prevent stale entries, duplicate locations, and cleanup races.

### Commands and permissions are stricter and more consistent

- Existing commands remain available through `/airdrop`, `/drop`, and `/ad`.
- Package permissions are consistently case-insensitive.
- The plugin now declares its permission nodes and inheritance directly.
- Package creation, deletion, and the package-management GUI require administrative permission.
- The new `airdrop.cooldown.bypass` permission bypasses the request cooldown.
- `airdrop.admin` includes package access and the cooldown bypass.

### Check requirements and package data before upgrading

- Airdrop 4.0 requires **Paper 1.21.11 or newer** and **Java 21**.
- LuckPerms remains required, but Airdrop no longer requires EssentialsX.
- VaultUnlocked is preferred for paid packages, while original Vault remains available as a legacy fallback. Existing Vault-based servers do not need to switch. Airdrop does not bundle either bridge, so keep VaultUnlocked or Vault installed alongside a compatible economy plugin.
- Without an economy provider, Airdrop still starts. Free packages remain available, but packages priced above zero are blocked. Set `economy.enabled: false` to make every package free.
- Back up `plugins/Airdrop/` before upgrading. The overall `packages.yml` structure has not changed, but Airdrop will reject invalid package names or prices when it loads the file.
- Review package contents before editing or saving them in version 4. Airdrop loads only the first 27 eligible item stacks. It also excludes items whose custom display names match localized editor controls such as `Save`, `Cancel`, `Back`, or `Help`. Saving the package later persists this reduced set.
- Stable version 3.2 did not create a `config.yml`. Version 4 creates one on first startup with a 30-second request cooldown and server-wide falling, landed, and lifetime limits. Configuration files from the 3.3 and 3.4 development builds remain compatible, and missing version 4 settings use their default values at runtime.

## Plugin developers must update integrations for 4.0

Version 4 intentionally changes parts of Airdrop's API. Integrations that use Airdrop internals must be rebuilt and may require source changes:

- `DropController` now handles package affordability checks and charging asynchronously. Returning from a paid-drop call no longer means that payment and delivery have finished.
- Constructing a `Crate` now requires a drop-admission lease.
- Public drop methods may reject requests with `DropLimitException`.
- `CrateManager` replaces the 3.x `CrateList` API. Its `addCrate(...)` methods report whether collision-safe registration succeeded.
- New `DropOptions` overloads let integrations customize individual drops.
- New `PackageDropEvent` and `PackageLandEvent` hooks expose the drop and landing lifecycle.
