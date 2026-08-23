# AIRDR-8 Package Name Policy Design

## Goal

Ensure every accepted package name maps to one reachable command identity and one unique permission node. Reject package names that conflict with Airdrop commands or the global package permission, and apply the same rules to commands, configuration loading, GUI creation, public APIs, and `PackageManager`.

## Name Policy

Add a focused `PackageNamePolicy` in `com.airdropmc.packages`. It owns the existing syntax rule (`A-Z`, `a-z`, `0-9`, `_`, and `-`), canonicalizes accepted names with `toLowerCase(Locale.ROOT)`, and rejects these case-insensitive reserved identities:

- `all`, because `airdrop.package.all` grants access to every package
- `*`, both because it represents a wildcard and because it fails the normal syntax rule
- `package`, `packages`, `version`, and `reload`, because `CmdAirdrop` routes those values as top-level commands

The policy returns a structured validation result containing either the canonical identity or a rejection reason. Commands can translate the reason into localized operator feedback, while configuration loading and public APIs can produce precise diagnostics from the same operation.

Top-level command names move into a small `AirdropCommandNames` constants class. `CmdAirdrop`, tab completion, and `PackageNamePolicy` consume those constants so command routing and reserved-name validation cannot drift independently.

## Runtime Identity and Persistence

`PackageManager` stores packages under their canonical lowercase identity while each `Package` retains its exact configured/display name. `get`, `has`, inventory updates, and deletion canonicalize caller input before accessing the registry. Consequently, `Starter`, `starter`, and `STARTER` identify the same package, while user-visible names and YAML keys remain unchanged.

Creating a package checks the centralized policy before checking both the canonical runtime map and every raw YAML key for duplicates. Inspecting raw keys prevents malformed entries and fail-closed collision groups—which are intentionally absent from the runtime map—from being shadowed by a newly created case variant. An invalid name throws `IllegalArgumentException` with the rejected name and reason. A case-only collision throws the existing `DuplicatePackageException`. Successful writes use the package's preserved exact name as the YAML key.

Updating or deleting through a differently cased lookup uses the stored package's exact name for the YAML path. This prevents an operation such as `deletePackage("STARTER")` from writing to the wrong configuration path when the stored name is `Starter`.

`getPackages()` returns the preserved names from the validated runtime packages rather than the canonical map keys. GUI labels and completions therefore keep their configured casing without exposing invalid entries.

## Configuration Conflicts

Reload validates every YAML key before reading its package data. Invalid syntax and reserved names are skipped with a warning that includes the exact name and rejection reason.

For legacy keys that differ only by case, loading fails closed: before reading package payloads, the loader groups syntactically valid keys by their `Locale.ROOT` canonical identity. Every member of a group containing multiple exact names is skipped, and a warning lists the conflicting keys. The file is not rewritten automatically. Operators must choose and rename the intended entry explicitly, avoiding silent selection of package contents under a formerly shared permission.

Name validation and collision grouping happen before price and item ingestion. A bad or colliding name therefore produces one name diagnostic without constructing or registering a `Package`, even if one colliding entry also has an invalid payload.

## Commands, GUI, APIs, and Permissions

`PackageController.createPackageCommand` calls the policy before opening `CreatePackageGui`, so invalid or reserved input never creates an editor session. Unsupported characters and reserved identities use separate localized message keys, allowing upgraded servers with an existing `packages.name-invalid` customization to receive the newly added reserved-name diagnostic. Its two public `createPackage` overloads continue through `PackageManager.createPackage`, which enforces the same policy even when callers bypass the command.

`CreatePackageGui` validates with the same policy before saving through `PackageManager`, preserving defense in depth for direct GUI construction. Rejected saves report the matching missing, invalid-character, or reserved-name error to the viewer rather than reporting success.

Drop and package-info commands already resolve through `PackageManager.get`; canonical lookup makes every accepted package reachable regardless of input case. The package browser and tab completers enumerate `getPackages()`, so they display only accepted packages with preserved casing.

`PackageNamePolicy` derives exactly one package-specific node from the canonical identity: `airdrop.package.<canonical-name>`. `PermissionsHelper.hasPermission` checks that node and removes the exact-case legacy fallback. Permission-denial feedback also uses the policy-generated lowercase node rather than appending the display name. It supplies both the new full `{permission}` placeholder and the canonical lowercase `{package}` placeholder so existing customized language files continue rendering accurate denial text. Admin and `airdrop.package.all` behavior remains unchanged. Invalid names fail closed instead of generating ambiguous permission nodes.

## Error Handling

- Command creation: send a localized missing-name, invalid-character, or reserved-name message; do not open a GUI.
- Public API or direct manager creation: throw `IllegalArgumentException` with a precise name-policy reason before persistence.
- Configuration load: skip the invalid or colliding entry, warn clearly, and continue loading independent valid packages.
- Lookup of syntactically invalid or reserved input: behave as not found rather than exposing policy internals to ordinary drop callers.
- Direct GUI save rejection: send the matching localized name-policy message and leave the editor open without mutating live or persisted packages.

## Testing

Focused unit tests will establish the policy contract for valid names, invalid syntax, blank/null values, every reserved name in mixed case, and Turkish-default-locale normalization. Manager tests will cover case-insensitive lookup, duplicate creation against live and skipped raw configuration identities, differently cased update/delete persistence paths, and preserved display names.

Configuration tests will cover reserved keys, invalid syntax, fail-closed case-only collisions before payload validation, warnings, and continued loading of unrelated valid siblings. Controller tests will confirm command rejection occurs before GUI creation and both public API overloads reject the same inputs. GUI-save coverage will confirm a directly constructed invalid editor cannot persist a package.

Permission tests will prove one lowercase `Locale.ROOT` node is checked and displayed, the exact-case fallback is gone, invalid names fail closed, and global/admin access still works. Command tests will prove accepted mixed-case packages are reachable through drop lookup and are exposed once through completion/browser enumeration.

The focused test classes, complete JUnit suite, clean Gradle build, and `git diff --check` form the final verification gate.
