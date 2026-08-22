# AIRDR-1 Package Save Integrity Design

## Summary

AIRDR-1 prevents package creation, update, and deletion from appearing successful when `packages.yml` cannot be saved. Package changes will be staged separately, written to a temporary file, and published to live memory only after the file replacement succeeds.

This design is intentionally sized for a Minecraft server plugin. It does not introduce a database, transaction framework, background save queue, backup rotation, explicit disk synchronization, or filesystem recovery subsystem.

## Problem

`AbstractConfig.saveConfig()` currently catches `IOException` and returns no result. Its callers therefore cannot distinguish success from failure.

Package mutations also change live state too early:

- Inventory updates mutate the live `Package` before saving.
- Create, update, and delete mutate the live `FileConfiguration` before saving.
- Command and GUI callers send success messages after the void save call.

A failed write can consequently leave the live package state different from `packages.yml`, and the operator is told that an operation succeeded when it did not.

## Goals

- Preserve the previous `packages.yml` when writing the replacement data fails.
- Keep the live `FileConfiguration` and package map unchanged until persistence succeeds.
- Report save failure to callers with a simple boolean result.
- Show operators a localized error instead of a success message.
- Keep package editors open after a failed save so the operator can retry.
- Apply the safer temporary-file save behavior in the shared config abstraction.

## Non-Goals

- Surviving every possible power-loss or filesystem failure scenario.
- Coordinating multiple server processes that write the same file.
- Preserving concurrent manual edits made while the server is running.
- Detecting stale package editors; if two operators edit the same package, the last successful save wins.
- Moving package persistence off the Paper server thread.
- Replacing YAML storage with a database.
- Adding backup rotation, file locking, or a general transaction framework.

## Considered Approaches

### Mutate and roll back

Snapshot the current data, mutate the live objects, save, and restore the snapshot on failure. This requires less staging code but temporarily exposes uncommitted state and contradicts the requirement to change live data only after a successful save.

### Stage, save, then publish

Copy the current YAML, apply the requested mutation to the copy, save the copy, and publish it only after persistence succeeds. This directly enforces the required ordering and keeps failure handling local and testable.

This is the selected approach.

### Full durability subsystem

Add backups, explicit `fsync`, an asynchronous write queue, locks, and recovery logic. This would cover failure modes beyond AIRDR-1 but is disproportionate for an infrequently edited Minecraft plugin configuration.

## Architecture

### `AbstractConfig`

The shared config abstraction will expose a boolean save result. It will support saving a candidate `FileConfiguration` without publishing that candidate before the write succeeds.

The public API will be:

- `boolean saveConfig()` to persist the current configuration.
- `boolean saveConfig(FileConfiguration candidate)` to persist and, on success, publish a staged configuration.

The no-argument method delegates to the candidate overload. Callers may ignore the result during best-effort default-file creation, but package mutations must inspect it.

The save operation will:

1. Create a uniquely named temporary file beside the target config file.
2. Save the complete candidate configuration to the temporary file.
3. Attempt to replace the target with an atomic move.
4. If atomic moves are unsupported, fall back to a normal replacement move.
5. Publish the candidate as the config instance only after the move succeeds.
6. Return `true` on success.
7. On failure, log the exception, delete any remaining temporary file, retain the previous config instance, and return `false`.

No checked persistence exception or custom result type will be added.

### `PackageManager`

Each mutation will create a staged `YamlConfiguration` from the current configuration using Paper's YAML serialization methods. Only the staged copy will be changed before persistence.

- Create stages the new package fields without adding the package to the live map.
- Update stages the new item list without mutating the live `Package`.
- Delete verifies the package exists, then removes it only from the staged YAML.

Each mutation returns `true` after successful persistence and `false` after a save failure. Existing validation and domain exceptions remain unchanged.

After a successful save, the manager updates its package map and refreshes the package-management GUI where applicable. After a failed save, it discards the staged objects and performs no live mutation or GUI refresh.

### Command and GUI Callers

Callers will inspect the boolean mutation result:

- `true`: complete the existing close/restore flow and send the existing success message.
- `false`: send a localized persistence error and do not send success.

Package creation and editing screens will close only after persistence succeeds. A failed save therefore leaves the editor open for retry. Package deletion reports the same localized error to the command sender.

## Data Flow

```text
live YAML and package map
          |
          | copy
          v
staged YAML -- apply requested mutation
          |
          | save complete candidate
          v
sibling temporary file
          |
          | replace packages.yml
          v
publish staged YAML and update package map
```

If copying, serialization, writing, or replacement fails, control exits before the publish step.

## Error Handling

The low-level save operation logs the filename and exception once. Higher-level callers do not duplicate the technical exception in chat.

A new language key will provide an operator-facing message equivalent to:

> Could not save package changes. No changes were made. Check the server log.

Failure leaves the existing package available, suppresses the success message, and keeps GUI editing state available when the operation came from an editor.

The fallback replacement move is a pragmatic compatibility choice. This design does not claim power-loss durability or identical behavior across every network or third-party filesystem.

## Testing

### Config persistence tests

- A successful save writes the candidate and publishes it.
- A forced temporary-file write failure returns `false` and leaves the original target unchanged.
- A failed save does not replace the live config instance.
- Temporary files are cleaned up after success and failure where possible.

### Package mutation tests

For create, update, and delete, force the config save to return `false` and verify:

- The method returns `false`.
- The live package map retains the old data.
- The live `FileConfiguration` retains the old data.
- The file representation remains equal to the live representation.
- The package-management GUI is not refreshed.

Existing success-path tests will be updated to expect `true` and continue verifying that mutations do not trigger a full reload or GUI rebuild.

### Caller tests

- Creation, editing, and deletion do not send success after a failed save.
- Each failure sends the localized persistence error.
- GUI save failure does not close the editor or restore/replace state as if the save succeeded.

## Acceptance Criteria Mapping

- Forced create failure: staged create is discarded and no success is sent.
- Forced update failure: the existing package retains its old item list.
- Forced delete failure: the old package remains available.
- Live/file equality: live state is published only after the candidate file replaces the target.
- Operator feedback: all three paths use the localized persistence error.

## Open Questions

None. The design intentionally accepts best-effort compatibility on filesystems without atomic replacement and does not expand into production-grade durability work.
