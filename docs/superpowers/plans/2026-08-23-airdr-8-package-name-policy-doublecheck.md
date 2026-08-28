# Verification Report

## Summary

**Text verified:** Initial AIRDR-8 package-name policy implementation plan

**Claims extracted:** 11 total
**Breakdown:**

| Rating | Count |
|--------|-------|
| VERIFIED | 10 |
| PLAUSIBLE | 0 |
| UNVERIFIED | 1 |
| DISPUTED | 0 |
| FABRICATION RISK | 0 |

**Items requiring attention:** 0 claims were disputed or showed fabrication risk. One security-sensitive migration choice was unsupported by AIRDR-8 and has been replaced in the revised plan.

---

## Flagged Items (Review These First)

No claims were disputed or showed fabrication risk.

The adversarial review found that the initial plan's “retain the lexicographically first case variant” rule was not specified by AIRDR-8. Selecting one package could silently choose different contents for an identity that previously shared a permission node. The revised plan now groups raw configured names before payload validation and rejects every member of a case-only collision group.

The review also found an omitted downstream effect: after removing the exact-case permission fallback, the existing denial message would still display the preserved mixed-case package name. The revised plan generates both authorization nodes and denial text through `PackageNamePolicy.permissionNode`.

---

## All Claims

### VERIFIED

#### C1 — Runtime package identity is currently case-sensitive
- **Claim:** `PackageManager.get`, `has`, loading, creation, update, and deletion currently use exact strings as map keys or YAML paths.
- **Source:** `src/main/java/com/airdropmc/packages/PackageManager.java`
- **Notes:** The revised plan canonicalizes map access but resolves the stored package before forming update/delete YAML paths.

#### C2 — More than one per-package permission node is currently checked
- **Claim:** Permission authorization probes a lowercase node and an exact-case fallback.
- **Source:** `src/main/java/com/airdropmc/helpers/PermissionsHelper.java`
- **Notes:** The fallback is removed so each accepted package has one package-specific permission node.

#### C3 — `all` and `*` collide with declared broad permissions
- **Claim:** Those package names form `airdrop.package.all` and `airdrop.package.*`, which are declared as broad permissions by the build configuration.
- **Source:** `build.gradle.kts`
- **Notes:** The name policy checks reserved identities before syntax so `*` receives the correct diagnostic.

#### C4 — The top-level argument commands are `package`, `packages`, `version`, and `reload`
- **Claim:** Those four names are routed specially and also duplicated in completion data.
- **Source:** `src/main/java/com/airdropmc/commands/CmdAirdrop.java` and `src/main/java/com/airdropmc/AirdropTabCompleter.java`
- **Notes:** Shared constants eliminate the duplication that could let routing and validation drift.

#### C5 — Current name validation covers only command creation
- **Claim:** The ASCII name regex is local to `PackageController`; public API overloads pass names into `PackageManager` without that validation.
- **Source:** `src/main/java/com/airdropmc/controllers/PackageController.java`
- **Notes:** Manager-level validation is required for both overloads and direct callers.

#### C6 — Direct GUI save bypasses controller validation
- **Claim:** `CreatePackageGui.save` calls `PackageManager.createPackage` directly.
- **Source:** `src/main/java/com/airdropmc/packages/CreatePackageGui.java`
- **Notes:** The manager is the authoritative backstop, while the GUI catches policy rejection and reports it without success.

#### C7 — Locale-neutral lowercase conversion requires an explicit locale
- **Claim:** Java's no-argument lowercase conversion is locale-sensitive; `Locale.ROOT` avoids the Turkish `TITLE`/dotless-i behavior for locale-independent identifiers.
- **Source:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html#toLowerCase()
- **Notes:** AIRDR-8 also explicitly requires `Locale.ROOT`.

#### C8 — `Locale.ROOT` is language- and country-neutral
- **Claim:** Java defines the root locale as the neutral base locale for locale-sensitive operations.
- **Source:** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Locale.html#ROOT
- **Notes:** This supports canonical command and permission identities independent of server locale.

#### C9 — Configuration key enumeration exposes a `Set` contract
- **Claim:** Paper's `ConfigurationSection.getKeys(false)` returns a `Set<String>` and documents no ordering contract suitable for choosing a collision winner.
- **Source:** https://jd.papermc.io/paper/1.21.8/org/bukkit/configuration/ConfigurationSection.html#getKeys(boolean)
- **Notes:** The revised design does not select a winner; it sorts only to keep diagnostics stable.

#### C10 — Canonical keys require display-name enumeration
- **Claim:** Returning canonical map keys from `getPackages()` would leak lowercase names into GUI and completion consumers.
- **Source:** `src/main/java/com/airdropmc/packages/PackageManager.java`, `src/main/java/com/airdropmc/AirdropTabCompleter.java`, `src/main/java/com/airdropmc/commands/PackageTabCompletion.java`, and `src/main/java/com/airdropmc/packages/PackagesGui.java`
- **Notes:** The revised plan returns preserved names from map values.

### PLAUSIBLE

No claims were rated plausible.

### UNVERIFIED

#### C11 — Retaining the first case-only collision is the correct migration
- **Claim:** The initial plan proposed keeping the lexicographically first otherwise-valid package among names that differ only by case.
- **Notes:** AIRDR-8 allows rejection or migration but does not choose a winner. The independent adversarial review found no authoritative basis for implicit retention, so the revised plan rejects the whole collision group and leaves YAML untouched.

### DISPUTED

No claims were disputed.

### FABRICATION RISK

No claims showed a fabrication pattern.

---

## Internal Consistency

The initial plan was internally consistent, but it omitted one consequence of preserving display casing: the permission-denial message would have recommended an exact-case permission no longer checked by authorization. The revised plan routes both through the canonical permission-node helper.

---

## What Was Not Checked

- No real server `packages.yml` or LuckPerms grant database was inspected.
- MockBukkit cannot prove every behavior of a live Paper server.
- AIRDR-8 does not define an automatic legacy permission migration; the implementation will make the lowercase requirement explicit in diagnostics and handoff notes.

---

## Limitations

- This tool accelerates human verification; it does not replace it.
- Web search results may not include the most recent information or paywalled sources.
- The adversarial review uses the same underlying model that may have produced the original output. It catches many issues but cannot catch all of them.
- A claim rated VERIFIED means a supporting source was found, not that the claim is definitely correct. Sources can be wrong too.
- Claims rated PLAUSIBLE may still be wrong. The absence of contradicting evidence is not proof of accuracy.
