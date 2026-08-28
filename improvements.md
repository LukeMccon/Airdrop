# Airdrop Plugin - Improvement Roadmap

## Critical Priority

### 1. Add Test Coverage (0% currently)

Your build has JUnit 5 configured but no tests exist. Priority areas:

- `DropController` - Core business logic (permissions, economy, sky clearance)
- `PackageManager` - CRUD and persistence operations
- `Crate` state machine transitions (FALLING → LANDED)
- `PermissionsHelper` - Permission checking logic
- Command argument parsing and error handling

**Location:** `src/test/java/` (empty)

### ~~2. Fix Exception Handling Anti-Patterns~~ (DONE)

Fixed in 4.0-beta branch:
- `PackagesConfig.java` now uses proper logging via `plugin.getLogger().log()`
- NPE-catching patterns removed from GUI classes
- No bare `Exception` catches remain

---

## High Priority

### ~~3. Extract Duplicate GUI Logic~~ (DONE)

Base `Gui` class now provides shared methods:
- `getDisplayName()` - Safe display name extraction
- `isControlItem()` - Control item detection
- `createGuiItem()` - GUI item creation

### ~~4. Fix Static Initialization Timing Bug~~ (DONE)

`Package.java` no longer has static Economy field. Now calls `Airdrop.getAirdropEconomy()` directly in methods.

### ~~5. Clean Up Boolean Wrapper Usage~~ (DONE)

All Boolean wrapper patterns replaced with simple boolean checks:
- `DropController.java` - `!pkg.canAfford(player)`
- `PackageGui.java` - `PermissionsHelper.isAdmin(p)` and `!PermissionsHelper.isAdmin(p)`

---

## Medium Priority

### ~~6. Task Lifecycle Management~~ (DONE)

`ParachuteSystem` now stores `BukkitTask` references (`parachuteTask`, `cleanupTask`) and has a `cancel()` method for explicit cleanup.

### ~~7. Consolidate Configuration~~ (DONE)

Extracted `AbstractConfig` base class that both `Config.java` and `PackagesConfig.java` extend.
- Common file loading, saving, and reloading logic in base class
- Subclasses only override `onConfigLoaded()` and `onCreateDefaultConfig()`
- `ConfigKeys.java` remains as typed accessor layer (good pattern)

### ~~8. Add Missing Null Safety~~ (DONE)

`Crate.java` now uses pattern matching with proper error handling:
```java
if (!(state instanceof Barrel barrel)) {
    throw new IllegalStateException("Failed to create barrel at landed location");
}
```

### ~~9. Inconsistent String Comparison~~ (DONE)

All string comparisons now use `Objects.equals()` for null-safety.

---

## Lower Priority

### 10. Documentation Gaps

- No API documentation for custom events (`PackageDropEvent`, `PackageLandEvent`)
- No architecture diagrams
- Limited inline comments in complex logic (ParachuteSystem, Crate state machine)
- No CONTRIBUTING.md for open-source collaboration

### 11. Missing Features

- No pagination for large package lists in GUIs
- No backup mechanism for `packages.yml`
- No validation of package contents (items could be empty)
- Package state not persisted across server restarts

### 12. API Design Improvements

- `Package.canAfford()` returns `Boolean` wrapper instead of primitive `boolean`
- `DropOptions` uses null to indicate "use default" - consider `Optional<T>`
- Static access pattern (`Airdrop.getInstance()`) makes testing/mocking difficult

---

## Summary

| Priority | Issue | Impact | Effort |
|----------|-------|--------|--------|
| **Critical** | Zero test coverage | Core functionality untested | High |
| ~~**Critical**~~ | ~~printStackTrace() & swallowed exceptions~~ | ~~Silent failures~~ | ~~DONE~~ |
| ~~**High**~~ | ~~Duplicate GUI event handling code~~ | ~~300+ lines of duplication~~ | ~~DONE~~ |
| ~~**High**~~ | ~~Static Economy field initialization timing~~ | ~~Potential NPE~~ | ~~DONE~~ |
| ~~**High**~~ | ~~Boolean wrapper comparisons~~ | ~~Inefficient code~~ | ~~DONE~~ |
| ~~**Medium**~~ | ~~No task lifecycle management~~ | ~~Memory leaks~~ | ~~DONE~~ |
| ~~**Medium**~~ | ~~Fragmented configuration~~ | ~~Hard to maintain~~ | ~~DONE~~ |
| ~~**Medium**~~ | ~~Missing null safety~~ | ~~Potential NPE~~ | ~~DONE~~ |
| **Low** | Missing API documentation | Knowledge gaps | Low |
| **Low** | Missing features | Limited functionality | Variable |
