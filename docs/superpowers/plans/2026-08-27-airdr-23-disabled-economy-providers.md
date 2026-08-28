# AIRDR-23 Disabled Economy Providers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reject disabled or failing Vault economy adapters during discovery while preserving healthy modern-provider preference, legacy fallback, and the existing paid-drop unavailable response.

**Architecture:** Keep provider health policy in `EconomyProviderDiscovery`. A shared guarded boolean probe will convert false, `RuntimeException`, and `LinkageError` results to unavailable; the existing refresh and paid-drop layers will consume the resulting empty discovery without production changes.

**Tech Stack:** Java 25, Paper/Bukkit services, Vault and VaultUnlocked APIs, JUnit 5, Mockito, MockBukkit, Gradle.

---

### Task 1: Add failing discovery regressions

**Files:**
- Modify: `src/test/java/com/airdropmc/economy/EconomyProviderDiscoveryTest.java`

- [ ] **Step 1: Mark existing healthy mocks enabled**

Add `when(provider.isEnabled()).thenReturn(true)` for the modern and legacy mocks used by the two existing successful-discovery tests.

- [ ] **Step 2: Add disabled and failing provider cases**

Add four focused tests with these arrangements and assertions:

```java
@Test
void disabledLegacyProviderIsUnavailable() {
	net.milkbowl.vault.economy.Economy legacy = mock(net.milkbowl.vault.economy.Economy.class);
	when(legacy.isEnabled()).thenReturn(false);
	server.getServicesManager().register(
			net.milkbowl.vault.economy.Economy.class, legacy, registrar, ServicePriority.Normal);

	assertTrue(EconomyProviderDiscovery.discover(server.getServicesManager()).isEmpty());
}

@Test
void disabledModernProviderFallsBackToHealthyLegacy() {
	net.milkbowl.vault2.economy.Economy modern = healthyModern();
	when(modern.isEnabled()).thenReturn(false);
	server.getServicesManager().register(
			net.milkbowl.vault2.economy.Economy.class, modern, registrar, ServicePriority.Normal);
	net.milkbowl.vault.economy.Economy legacy = healthyLegacy();
	server.getServicesManager().register(
			net.milkbowl.vault.economy.Economy.class, legacy, registrar, ServicePriority.Normal);

	assertInstanceOf(VaultEconomyProvider.class,
			EconomyProviderDiscovery.discover(server.getServicesManager()).orElseThrow());
}

@Test
void modernEnabledLinkageFailureFallsBackToHealthyLegacy() {
	net.milkbowl.vault2.economy.Economy modern = healthyModern();
	when(modern.isEnabled()).thenThrow(new NoClassDefFoundError("provider dependency"));
	server.getServicesManager().register(
			net.milkbowl.vault2.economy.Economy.class, modern, registrar, ServicePriority.Normal);
	net.milkbowl.vault.economy.Economy legacy = healthyLegacy();
	server.getServicesManager().register(
			net.milkbowl.vault.economy.Economy.class, legacy, registrar, ServicePriority.Normal);

	assertInstanceOf(VaultEconomyProvider.class,
			EconomyProviderDiscovery.discover(server.getServicesManager()).orElseThrow());
}

@Test
void legacyEnabledRuntimeFailureIsUnavailable() {
	net.milkbowl.vault.economy.Economy legacy = healthyLegacy();
	when(legacy.isEnabled()).thenThrow(new IllegalStateException("provider unavailable"));
	server.getServicesManager().register(
			net.milkbowl.vault.economy.Economy.class, legacy, registrar, ServicePriority.Normal);

	assertTrue(EconomyProviderDiscovery.discover(server.getServicesManager()).isEmpty());
}
```

Use small `healthyModern()` and `healthyLegacy()` helpers to provide enabled providers and async capability without repeating setup.

- [ ] **Step 3: Run the focused test and verify RED**

Run:

```bash
./gradlew test --tests com.airdropmc.economy.EconomyProviderDiscoveryTest --rerun-tasks
```

Expected: FAIL because current discovery never rejects a disabled legacy provider and still selects an otherwise-capable disabled/failing modern provider.

### Task 2: Guard provider enablement during discovery

**Files:**
- Modify: `src/main/java/com/airdropmc/economy/EconomyProviderDiscovery.java`
- Test: `src/test/java/com/airdropmc/economy/EconomyProviderDiscoveryTest.java`

- [ ] **Step 1: Add a shared guarded probe**

Import `java.util.function.BooleanSupplier` and add:

```java
private static boolean isEnabled(BooleanSupplier enabledCheck) {
	try {
		return enabledCheck.getAsBoolean();
	} catch (LinkageError | RuntimeException ignored) {
		return false;
	}
}
```

- [ ] **Step 2: Apply the probe to both APIs**

Store each registration provider in a local variable. Before constructing the legacy wrapper, require:

```java
if (!isEnabled(economy::isEnabled)) {
	return Optional.empty();
}
```

Apply the same check to the modern provider before calling `supportsAsync()`.

- [ ] **Step 3: Run the focused test and verify GREEN**

Run:

```bash
./gradlew test --tests com.airdropmc.economy.EconomyProviderDiscoveryTest --rerun-tasks
```

Expected: `BUILD SUCCESSFUL` with all discovery cases passing.

### Task 3: Preserve lifecycle health and cover user feedback

**Files:**
- Modify: `src/test/java/com/airdropmc/economy/AirdropEconomyLifecycleTest.java`
- Modify: `src/test/java/com/airdropmc/commands/DropCommandPackageIdentityTest.java`
- Test: `src/test/java/com/airdropmc/controllers/DropControllerEconomyFlowTest.java`

- [ ] **Step 1: Mark lifecycle fixtures healthy**

In both lifecycle provider helpers, add:

```java
when(economy.isEnabled()).thenReturn(true);
```

- [ ] **Step 2: Add direct command-message coverage**

In `DropCommandPackageIdentityTest`, statically stub the controller call to throw `EconomyUnavailableException`, invoke the command, and assert the next plain-text player message contains `Economy provider is unavailable`:

```java
@Test
void unavailableEconomyDisplaysConfiguredMessage() throws Exception {
	PlayerMock player = server.addPlayer();
	Package expected = PackageManager.get("Starter");

	try (MockedStatic<DropController> controller = mockStatic(DropController.class)) {
		controller.when(() -> DropController.playerInitiatedDropPackage(expected, player))
				.thenThrow(new EconomyUnavailableException());

		DropCommand.onCommand(player, new String[]{"Starter"});
	}

	String text = PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
	assertTrue(text.contains("Economy provider is unavailable"), text);
}
```

- [ ] **Step 3: Run affected tests**

Run:

```bash
./gradlew test \
  --tests com.airdropmc.economy.AirdropEconomyLifecycleTest \
  --tests com.airdropmc.commands.DropCommandPackageIdentityTest \
  --tests com.airdropmc.controllers.DropControllerEconomyFlowTest \
  --rerun-tasks
```

Expected: `BUILD SUCCESSFUL`; the lifecycle uses healthy providers, the configured message is shown, and the existing early-rejection test remains green.

### Task 4: Verify and commit AIRDR-23

**Files:**
- Modify: `src/main/java/com/airdropmc/economy/EconomyProviderDiscovery.java`
- Modify: `src/test/java/com/airdropmc/economy/EconomyProviderDiscoveryTest.java`
- Modify: `src/test/java/com/airdropmc/economy/AirdropEconomyLifecycleTest.java`
- Modify: `src/test/java/com/airdropmc/commands/DropCommandPackageIdentityTest.java`

- [ ] **Step 1: Run formatting and complete-suite checks**

Run:

```bash
git diff --check
./gradlew clean test
```

Expected: no whitespace errors and `BUILD SUCCESSFUL`.

- [ ] **Step 2: Review scope**

Confirm the diff changes only discovery health validation, required mock health declarations, command-message coverage, and the approved design/plan artifacts.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/airdropmc/economy/EconomyProviderDiscovery.java \
  src/test/java/com/airdropmc/economy/EconomyProviderDiscoveryTest.java \
  src/test/java/com/airdropmc/economy/AirdropEconomyLifecycleTest.java \
  src/test/java/com/airdropmc/commands/DropCommandPackageIdentityTest.java
git commit -m "AIRDR-23: reject disabled economy providers"
```
