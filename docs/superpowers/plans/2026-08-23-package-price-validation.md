# Package Price Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent malformed configured prices from registering free or unusable packages.

**Architecture:** Validate raw YAML prices once while populating the runtime package registry. Make registry keys the authoritative package-name set so lookup, commands, completion, and the package browser cannot enumerate rejected configuration entries.

**Tech Stack:** Java 21, Paper/Bukkit `YamlConfiguration`, JUnit 5, Mockito, Gradle

---

### Task 1: Cover raw configured-price validation

**Files:**
- Modify: `src/test/java/com/airdropmc/packages/PackageManagerConfigRobustnessTest.java`

- [ ] **Step 1: Add a configuration fixture helper**

Refactor setup so each test can install a supplied `YamlConfiguration`, while teardown continues clearing `PackageManager` and the static `Airdrop` fields.

- [ ] **Step 2: Write failing invalid-price tests**

Add tests that configure packages with no price, `"10"`, `true`, `Double.NaN`, `Double.POSITIVE_INFINITY`, `-1`, and `new BigDecimal("1e10000")`. Reload, then assert every name is absent from `PackageManager.getPackages()` and `PackageManager.get(name)` throws `PackageNotFoundException`.

- [ ] **Step 3: Write a failing warning-content test**

Use `MockedStatic<AirdropLogger>` around reload and verify warnings contain both the invalid package name and raw value. Verify a missing price is represented as `<missing>`.

- [ ] **Step 4: Write a valid-zero regression test**

Configure one package with integer `0` and another with floating-point `0.0`; assert both appear in `getPackages()` and both load at price `0.0`.

- [ ] **Step 5: Run the focused tests and verify RED**

Run:

```bash
./gradlew test --tests com.airdropmc.packages.PackageManagerConfigRobustnessTest
```

Expected: failures show invalid entries are still exposed or loaded and warning text still describes a `0.0` fallback.

### Task 2: Reject invalid packages at ingestion

**Files:**
- Modify: `src/main/java/com/airdropmc/packages/PackageManager.java`

- [ ] **Step 1: Read and validate the raw price**

Replace `getDouble(..., 0.0)` fallback logic with raw-value validation equivalent to:

```java
Object rawPrice = config.get(pkg + ".price");
if (!(rawPrice instanceof Number number)) {
	logInvalidPrice(name, rawPrice);
	continue;
}
double price = number.doubleValue();
if (!Package.isValidPrice(price)) {
	logInvalidPrice(name, rawPrice);
	continue;
}
```

Add a focused helper that logs `String.valueOf(rawPrice)` or `<missing>` when null. Do not construct or register a rejected package.

- [ ] **Step 2: Make registry enumeration authoritative**

Return an immutable snapshot from the validated map:

```java
return Set.copyOf(packages.keySet());
```

This existing API feeds `has`, `AirdropTabCompleter`, `PackageTabCompletion`, and `PackagesGui`.

- [ ] **Step 3: Run the focused tests and verify GREEN**

Run:

```bash
./gradlew test --tests com.airdropmc.packages.PackageManagerConfigRobustnessTest
```

Expected: all tests in the class pass.

- [ ] **Step 4: Run adjacent package tests**

Run:

```bash
./gradlew test --tests 'com.airdropmc.packages.*'
```

Expected: all package tests pass.

### Task 3: Confirm command and browser consumers use only validated packages

**Files:**
- Modify: `src/test/java/com/airdropmc/commands/TabCompletionPermissionsTest.java`
- Modify: `src/test/java/com/airdropmc/packages/PackagesGuiNavigationTest.java`

- [ ] **Step 1: Write failing command-completion assertions**

Give the tab-completion fixture a valid `starter` price and an invalid `broken` price, reload `PackageManager` during setup, and assert `broken` is absent from results returned by both `AirdropTabCompleter` and `PackageTabCompletion`.

- [ ] **Step 2: Run command-completion tests and verify RED when isolated from Task 2**

Run:

```bash
./gradlew test --tests com.airdropmc.commands.TabCompletionPermissionsTest
```

Expected before the loader fix: invalid `broken` appears in completion. Expected after Task 2: all tests pass.

- [ ] **Step 3: Write a package-browser assertion**

Add an invalid `broken` package to the browser fixture, reload, create `PackagesGui`, and assert the inventory contains only the valid `starter` entry.

- [ ] **Step 4: Run the browser test**

Run:

```bash
./gradlew test --tests com.airdropmc.packages.PackagesGuiNavigationTest
```

Expected: all tests pass and the rejected package creates no browser item.

### Task 4: Verify the complete change

**Files:**
- Review: `src/main/java/com/airdropmc/packages/PackageManager.java`
- Review: `src/test/java/com/airdropmc/packages/PackageManagerConfigRobustnessTest.java`
- Review: `src/test/java/com/airdropmc/commands/TabCompletionPermissionsTest.java`
- Review: `src/test/java/com/airdropmc/packages/PackagesGuiNavigationTest.java`

- [ ] **Step 1: Run the full test suite**

```bash
./gradlew test
```

Expected: `BUILD SUCCESSFUL` with zero failed tests.

- [ ] **Step 2: Run a clean production build**

```bash
./gradlew clean build
```

Expected: `BUILD SUCCESSFUL` and a plugin JAR under `build/libs/`.

- [ ] **Step 3: Inspect the diff against the task base**

```bash
git diff --check
git diff 039243b -- src/main/java src/test/java docs/superpowers
```

Expected: no whitespace errors; only AIRDR-5 documentation, loader behavior, and tests are changed.

- [ ] **Step 4: Commit the implementation**

```bash
git add -f docs/superpowers/specs/2026-08-23-package-price-validation-design.md docs/superpowers/plans/2026-08-23-package-price-validation.md
git add src/main/java/com/airdropmc/packages/PackageManager.java src/test/java/com/airdropmc/packages/PackageManagerConfigRobustnessTest.java src/test/java/com/airdropmc/commands/TabCompletionPermissionsTest.java src/test/java/com/airdropmc/packages/PackagesGuiNavigationTest.java
git commit -m "AIRDR-5: reject invalid configured package prices"
```

## Doublecheck Revision

The verification pass retained raw-object validation and the `Number`/`doubleValue()` conversion strategy. It strengthened the plan with direct tests for both completion implementations and the package browser, and it updates the existing completion fixture to populate the runtime registry before querying it.

Primary references checked:

- Bukkit `ConfigurationSection.get` returns the stored object and returns `null` when no value/default exists: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/configuration/ConfigurationSection.html
- Java `Number.doubleValue()` is the standard polymorphic conversion to `double`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Number.html#doubleValue()
- Java `Double.isFinite` rejects NaN and infinities: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html#isFinite(double)
- `BigDecimal.doubleValue()` may produce infinity when magnitude exceeds the `double` range: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html#doubleValue()
