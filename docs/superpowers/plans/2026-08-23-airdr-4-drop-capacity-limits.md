# AIRDR-4 Drop Capacity Limits Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bound request frequency, falling/parachute resources, landed crates, and effect lifetimes while guaranteeing pre-payment admission and exactly-once cleanup.

**Architecture:** A Bukkit-independent admission controller atomically owns player cooldowns, falling permits, future-landed permits, and landing-block reservations. Each admitted crate receives one lifecycle lease that converts from reserved to falling to landed and closes idempotently on every failure or cleanup path. `DropController` is the only crate-construction boundary; `Crate` and `CrateManager` converge all expiry/listener/disable cleanup on the lease.

**Tech Stack:** Java 21, Paper API 1.21.8, Bukkit scheduler and permissions, MockBukkit 3.133.2, JUnit Jupiter, Mockito, Gradle

---

## File Structure

- Create `src/main/java/com/airdropmc/limits/DropLimitSettings.java`: immutable validated settings passed into admission and expiry logic.
- Create `src/main/java/com/airdropmc/limits/DropLocationKey.java`: stable world/block identity for falling and landed reservations.
- Create `src/main/java/com/airdropmc/limits/DropAdmissionController.java`: atomic cooldown/capacity state and idempotent lifecycle leases.
- Create `src/main/java/com/airdropmc/exceptions/DropLimitException.java`: typed rejection reason and cooldown retry duration.
- Create `src/test/java/com/airdropmc/limits/DropAdmissionControllerTest.java`: concurrency, cooldown, transition, reload, and exact-once tests.
- Modify `src/main/java/com/airdropmc/config/ConfigKeys.java`: limit paths, safe defaults/ranges, and a `DropLimitSettings` snapshot.
- Modify `src/main/resources/config.yml`: documented conservative defaults.
- Modify `build.gradle.kts`: generated `airdrop.cooldown.bypass` permission and `airdrop.admin` child.
- Modify `src/main/java/com/airdropmc/helpers/PermissionsHelper.java`: exact cooldown-bypass check.
- Modify `src/main/java/com/airdropmc/lang/MessageKey.java`: typed limit rejection message keys.
- Modify `src/main/resources/lang/en.yml`: localized limit messages.
- Modify `src/test/java/com/airdropmc/config/ConfigKeysTest.java`: defaults and invalid-value fallback.
- Modify `src/test/java/com/airdropmc/config/PluginYmlPermissionsTest.java`: new permission contract and absent legacy alias.
- Modify `src/test/java/com/airdropmc/helpers/PermissionsHelperTest.java`: cooldown-bypass behavior.
- Modify `src/main/java/com/airdropmc/Airdrop.java`: controller lifecycle and shutdown ordering.
- Modify `src/main/java/com/airdropmc/controllers/DropController.java`: calculate target, acquire before charge, spawn under a lease, and refund without leaking capacity.
- Modify `src/main/java/com/airdropmc/packages/Package.java`: return a confirmed withdrawal result before any unrelated player messaging.
- Modify `src/main/java/com/airdropmc/commands/DropCommand.java`: map rejection reasons to localized feedback.
- Modify `src/test/java/com/airdropmc/controllers/DropControllerEconomyFlowTest.java`: pre-charge rejection and every economy/spawn release path.
- Modify `src/main/java/com/airdropmc/Crate.java`: lease ownership, idempotent destruction, landed expiry, and bounded task cleanup.
- Modify `src/main/java/com/airdropmc/helpers/CrateManager.java`: collision-safe registration and remove-by-crate cleanup.
- Modify `src/main/java/com/airdropmc/listeners/FallingCrateListener.java`: fail-closed landing conversion.
- Modify `src/test/java/com/airdropmc/CrateDestroyTest.java`: lease release, expiry, and duplicate cleanup.
- Modify `src/test/java/com/airdropmc/helpers/CrateManagerTest.java`: collision and remove-by-crate behavior.
- Modify `src/test/java/com/airdropmc/listeners/FallingCrateListenerTest.java`: transition and failed-landing release.
- Modify `src/test/java/com/airdropmc/commands/CmdAirdropLifecycleSafetyTest.java`: disable/reload state preservation.
- Modify `README.md`: new limit configuration and permission.

## Task 1: Add the configuration and permission contract

**Files:**
- Create: `src/main/java/com/airdropmc/limits/DropLimitSettings.java`
- Modify: `src/main/java/com/airdropmc/config/ConfigKeys.java`
- Modify: `src/main/resources/config.yml`
- Modify: `build.gradle.kts`
- Modify: `src/main/java/com/airdropmc/helpers/PermissionsHelper.java`
- Modify: `src/test/java/com/airdropmc/config/ConfigKeysTest.java`
- Modify: `src/test/java/com/airdropmc/config/PluginYmlPermissionsTest.java`
- Modify: `src/test/java/com/airdropmc/helpers/PermissionsHelperTest.java`

- [ ] **Step 1: Write failing default, fallback, and permission tests**

Add assertions with these exact values and relationships:

```java
@Test
void getDropLimitSettings_usesConservativeDefaults() {
	YamlConfiguration values = new YamlConfiguration();
	Config config = mock(Config.class);
	when(config.getConfig()).thenReturn(values);
	setAirdropConfig(config);

	DropLimitSettings limits = ConfigKeys.getDropLimitSettings();

	assertEquals(Duration.ofSeconds(30), limits.requestCooldown());
	assertEquals(3, limits.maxFalling());
	assertEquals(10, limits.maxLanded());
	assertEquals(Duration.ofSeconds(600), limits.landedLifetime());
}

@Test
void getDropLimitSettings_rejectsUnsafeValues() {
	YamlConfiguration values = new YamlConfiguration();
	values.set("drop.limits.request-cooldown-seconds", 0);
	values.set("drop.limits.max-falling", 65);
	values.set("drop.limits.max-landed", -1);
	values.set("drop.limits.landed-lifetime-seconds", 86_401);
	Config config = mock(Config.class);
	when(config.getConfig()).thenReturn(values);
	setAirdropConfig(config);

	assertEquals(new DropLimitSettings(Duration.ofSeconds(30), 3, 10, Duration.ofSeconds(600)),
			ConfigKeys.getDropLimitSettings());
}
```

Add parameterized boundary cases for the inclusive minimum/maximum of each key and one value immediately below/above every boundary. Capture `AirdropLogger` output (or a package-visible validation helper result) and assert each invalid configured value falls back with a warning naming the key and fallback.

Extend `PluginYmlPermissionsTest` to load these exact sections:

```java
Map<?, ?> cooldownBypass = castMap(permissions.get("airdrop.cooldown.bypass"), "airdrop.cooldown.bypass");
Map<?, ?> adminChildren = castMap(admin.get("children"), "airdrop.admin.children");
assertEquals("op", String.valueOf(cooldownBypass.get("default")));
assertEquals("true", String.valueOf(adminChildren.get("airdrop.cooldown.bypass")));
assertNull(permissions.get("airdrop.limits.bypass"));
```

Add permission behavior:

```java
@Test
void hasCooldownBypass_checksOnlyNarrowPermission() {
	Player player = mock(Player.class);
	when(player.hasPermission("airdrop.cooldown.bypass")).thenReturn(true);
	assertTrue(PermissionsHelper.hasCooldownBypass(player));
	verify(player).hasPermission("airdrop.cooldown.bypass");
}
```

- [ ] **Step 2: Run the contract tests and verify RED**

Run:

```bash
./gradlew test --tests com.airdropmc.config.ConfigKeysTest --tests com.airdropmc.config.PluginYmlPermissionsTest --tests com.airdropmc.helpers.PermissionsHelperTest
```

Expected: compilation/assertion failures because the settings type, config keys, and permission do not exist.

- [ ] **Step 3: Implement the settings record and validated getters**

Create the immutable type:

```java
package com.airdropmc.limits;

import java.time.Duration;
import java.util.Objects;

public record DropLimitSettings(
		Duration requestCooldown,
		int maxFalling,
		int maxLanded,
		Duration landedLifetime) {
	public DropLimitSettings {
		Objects.requireNonNull(requestCooldown, "requestCooldown");
		Objects.requireNonNull(landedLifetime, "landedLifetime");
		if (requestCooldown.isZero() || requestCooldown.isNegative()
				|| maxFalling < 1 || maxLanded < 1
				|| landedLifetime.isZero() || landedLifetime.isNegative()) {
			throw new IllegalArgumentException("Drop limits must be positive");
		}
	}

	public long landedLifetimeTicks() {
		return Math.multiplyExact(landedLifetime.getSeconds(), 20L);
	}
}
```

Add `ConfigKeys` paths/defaults and sanitize using inclusive ranges:

```java
public static final String DROP_REQUEST_COOLDOWN_SECONDS = "drop.limits.request-cooldown-seconds";
public static final String DROP_MAX_FALLING = "drop.limits.max-falling";
public static final String DROP_MAX_LANDED = "drop.limits.max-landed";
public static final String DROP_LANDED_LIFETIME_SECONDS = "drop.limits.landed-lifetime-seconds";

private static final int DEFAULT_REQUEST_COOLDOWN_SECONDS = 30;
private static final int DEFAULT_MAX_FALLING = 3;
private static final int DEFAULT_MAX_LANDED = 10;
private static final int DEFAULT_LANDED_LIFETIME_SECONDS = 600;

public static DropLimitSettings getDropLimitSettings() {
	FileConfiguration config = getConfig();
	int cooldown = bounded(DROP_REQUEST_COOLDOWN_SECONDS,
			config.getInt(DROP_REQUEST_COOLDOWN_SECONDS, DEFAULT_REQUEST_COOLDOWN_SECONDS),
			1, 86_400, DEFAULT_REQUEST_COOLDOWN_SECONDS);
	int maxFalling = bounded(DROP_MAX_FALLING,
			config.getInt(DROP_MAX_FALLING, DEFAULT_MAX_FALLING), 1, 64, DEFAULT_MAX_FALLING);
	int maxLanded = bounded(DROP_MAX_LANDED,
			config.getInt(DROP_MAX_LANDED, DEFAULT_MAX_LANDED), 1, 256, DEFAULT_MAX_LANDED);
	int lifetime = bounded(DROP_LANDED_LIFETIME_SECONDS,
			config.getInt(DROP_LANDED_LIFETIME_SECONDS, DEFAULT_LANDED_LIFETIME_SECONDS),
			30, 86_400, DEFAULT_LANDED_LIFETIME_SECONDS);
	return new DropLimitSettings(Duration.ofSeconds(cooldown), maxFalling, maxLanded, Duration.ofSeconds(lifetime));
}

private static int bounded(String key, int value, int minimum, int maximum, int fallback) {
	if (value >= minimum && value <= maximum) return value;
	AirdropLogger.warning("Invalid " + key + " value " + value + "; using " + fallback);
	return fallback;
}
```

Add the four documented YAML defaults:

```yaml
  limits:
    request-cooldown-seconds: 30
    max-falling: 3
    max-landed: 10
    landed-lifetime-seconds: 600
```

- [ ] **Step 4: Generate the permission and implement the narrow helper**

Add this permission before `airdrop.admin`, and add it to the admin children list:

```kotlin
register("airdrop.cooldown.bypass") {
    description = "Bypasses only the per-player airdrop request cooldown"
    default = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.OP
}

register("airdrop.admin") {
    description = "Allows full administrative access to Airdrop commands and GUIs"
    default = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.OP
    children = listOf("airdrop.package.all", "airdrop.package.*", "airdrop.cooldown.bypass")
}
```

Implement:

```java
private static final String AIRDROP_COOLDOWN_BYPASS = "airdrop.cooldown.bypass";

public static boolean hasCooldownBypass(Player player) {
	return player.hasPermission(AIRDROP_COOLDOWN_BYPASS);
}
```

The generated permission's `default: op` supplies operator access. Do not add a separate `isOp()` branch; keeping the application check on the exact node preserves permission-plugin overrides.

- [ ] **Step 5: Run contract tests and commit**

Run the Step 2 command. Expected: PASS.

```bash
git add build.gradle.kts src/main/java/com/airdropmc/config/ConfigKeys.java src/main/java/com/airdropmc/helpers/PermissionsHelper.java src/main/java/com/airdropmc/limits/DropLimitSettings.java src/main/resources/config.yml src/test/java/com/airdropmc/config/ConfigKeysTest.java src/test/java/com/airdropmc/config/PluginYmlPermissionsTest.java src/test/java/com/airdropmc/helpers/PermissionsHelperTest.java
git commit -m "AIRDR-4: define drop limit configuration"
```

## Task 2: Build atomic admission and lifecycle leases

**Files:**
- Create: `src/main/java/com/airdropmc/limits/DropLocationKey.java`
- Create: `src/main/java/com/airdropmc/limits/DropAdmissionController.java`
- Create: `src/main/java/com/airdropmc/exceptions/DropLimitException.java`
- Create: `src/test/java/com/airdropmc/limits/DropAdmissionControllerTest.java`

- [ ] **Step 1: Write failing admission tests**

Cover exact capacity, future-landed overcommit, location collision, concurrent acquisition, cooldown commit, bypass, reload lowering, shutdown, and repeated close. Use a mutable `LongSupplier` and direct settings:

```java
private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
private static final DropLocationKey LOCATION = new DropLocationKey(
		UUID.fromString("10000000-0000-0000-0000-000000000001"), 10, 65, 10);
private static final DropLocationKey OTHER_LOCATION = new DropLocationKey(
		UUID.fromString("10000000-0000-0000-0000-000000000001"), 20, 65, 20);
private static final DropLocationKey THIRD_LOCATION = new DropLocationKey(
		UUID.fromString("10000000-0000-0000-0000-000000000001"), 30, 65, 30);
private static final DropLimitSettings LIMITS = new DropLimitSettings(
		Duration.ofSeconds(30), 2, 3, Duration.ofSeconds(600));

private final AtomicLong clock = new AtomicLong();

@Test
void acquirePlayer_reservesFallingFutureLandedAndLocationAtomically() throws Exception {
	DropAdmissionController controller = new DropAdmissionController(clock::get);
	DropAdmissionController.Lease lease = controller.acquirePlayer(PLAYER, false, LOCATION, LIMITS);

	assertEquals(new Snapshot(1, 1, 1, 1, 0, true), controller.snapshot());
	lease.commitSpawn();
	lease.markLanded();
	assertEquals(new Snapshot(0, 1, 1, 1, 0, true), controller.snapshot());
	lease.close();
	lease.close();
	assertEquals(new Snapshot(0, 0, 0, 0, 0, true), controller.snapshot());
}

@Test
void commitSpawn_startsCooldownOnlyAfterSuccessfulSpawn() throws Exception {
	DropAdmissionController controller = new DropAdmissionController(clock::get);
	DropAdmissionController.Lease failed = controller.acquirePlayer(PLAYER, false, LOCATION, LIMITS);
	failed.close();
	assertDoesNotThrow(() -> controller.acquirePlayer(PLAYER, false, LOCATION, LIMITS).close());

	DropAdmissionController.Lease successful = controller.acquirePlayer(PLAYER, false, OTHER_LOCATION, LIMITS);
	successful.commitSpawn();
	DropLimitException rejection = assertThrows(DropLimitException.class,
			() -> controller.acquirePlayer(PLAYER, false, THIRD_LOCATION, LIMITS));
	assertEquals(Reason.COOLDOWN, rejection.getReason());
	assertEquals(30, rejection.getRetryAfterSeconds());
}

@Test
void closedOrClearedLease_cannotCommitSpawnOrRepopulateCooldowns() throws Exception {
	DropAdmissionController controller = new DropAdmissionController(clock::get);
	DropAdmissionController.Lease closed = controller.acquirePlayer(PLAYER, false, LOCATION, LIMITS);
	closed.close();
	assertThrows(IllegalStateException.class, closed::commitSpawn);

	DropAdmissionController.Lease cleared = controller.acquirePlayer(PLAYER, false, OTHER_LOCATION, LIMITS);
	controller.clear();
	assertThrows(IllegalStateException.class, cleared::commitSpawn);
	assertEquals(new Snapshot(0, 0, 0, 0, 0, true), controller.snapshot());
}

@Test
void clearRacingWithCommitSpawn_neverLeavesCooldownOrCapacity() throws Exception {
	DropAdmissionController controller = new DropAdmissionController(clock::get);
	DropAdmissionController.Lease lease = controller.acquirePlayer(PLAYER, false, LOCATION, LIMITS);
	ExecutorService executor = Executors.newFixedThreadPool(2);
	try {
		CountDownLatch start = new CountDownLatch(1);
		Future<?> commit = executor.submit(() -> {
			await(start);
			try { lease.commitSpawn(); } catch (IllegalStateException clearedFirst) { }
		});
		Future<?> clear = executor.submit(() -> { await(start); controller.clear(); });
		start.countDown();
		commit.get(5, TimeUnit.SECONDS);
		clear.get(5, TimeUnit.SECONDS);
		assertEquals(new Snapshot(0, 0, 0, 0, 0, true), controller.snapshot());
	} finally {
		executor.shutdownNow();
		controller.clear();
	}
}

@Test
void cooldown_usesWrapSafeElapsedTime() throws Exception {
	clock.set(Long.MAX_VALUE - Duration.ofSeconds(10).toNanos());
	DropAdmissionController controller = new DropAdmissionController(clock::get);
	DropAdmissionController.Lease lease = controller.acquirePlayer(PLAYER, false, LOCATION, LIMITS);
	lease.commitSpawn();
	clock.addAndGet(Duration.ofSeconds(31).toNanos());
	assertDoesNotThrow(() -> controller.acquirePlayer(PLAYER, false, OTHER_LOCATION, LIMITS).close());
}

@Test
void bypass_skipsCooldownButNeverCapacity() throws Exception {
	DropAdmissionController controller = new DropAdmissionController(clock::get);
	controller.acquirePlayer(PLAYER, true, LOCATION, LIMITS);
	controller.acquirePlayer(PLAYER, true, OTHER_LOCATION, LIMITS);
	DropLimitException rejection = assertThrows(DropLimitException.class,
			() -> controller.acquirePlayer(PLAYER, true, THIRD_LOCATION, LIMITS));
	assertEquals(Reason.FALLING_CAPACITY, rejection.getReason());
}
```

Add the concurrent acquisition case:

```java
@Test
void concurrentAcquire_neverOversubscribes() throws Exception {
	DropAdmissionController controller = new DropAdmissionController(clock::get);
	DropLimitSettings twoSlots = new DropLimitSettings(
			Duration.ofSeconds(30), 2, 20, Duration.ofSeconds(600));
	ExecutorService executor = Executors.newFixedThreadPool(20);
	try {
		CountDownLatch ready = new CountDownLatch(20);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> attempts = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			int coordinate = i;
			attempts.add(executor.submit(() -> {
				ready.countDown();
				start.await();
				try {
					controller.acquireSystem(new DropLocationKey(LOCATION.worldId(), coordinate, 65, coordinate), twoSlots);
					return true;
				} catch (DropLimitException expected) {
					return false;
				}
			}));
		}
		assertTrue(ready.await(5, TimeUnit.SECONDS));
		start.countDown();
		assertEquals(2, attempts.stream().filter(future -> get(future)).count());
		assertEquals(2, controller.snapshot().falling());
		assertEquals(2, controller.snapshot().landedClaims());
	} finally {
		executor.shutdownNow();
		controller.clear();
	}
}

private static boolean get(Future<Boolean> future) {
	try {
		return future.get(5, TimeUnit.SECONDS);
	} catch (InterruptedException interrupted) {
		Thread.currentThread().interrupt();
		throw new AssertionError(interrupted);
	} catch (ExecutionException | TimeoutException failure) {
		throw new AssertionError(failure);
	}
}

private static void await(CountDownLatch latch) {
	try {
		latch.await();
	} catch (InterruptedException interrupted) {
		Thread.currentThread().interrupt();
		throw new AssertionError(interrupted);
	}
}
```

- [ ] **Step 2: Run the admission test and verify RED**

Run:

```bash
./gradlew test --tests com.airdropmc.limits.DropAdmissionControllerTest
```

Expected: compilation fails because admission classes do not exist.

- [ ] **Step 3: Create the key and typed exception**

```java
package com.airdropmc.limits;

import java.util.UUID;
import org.bukkit.Location;

public record DropLocationKey(UUID worldId, int x, int y, int z) {
	public static DropLocationKey from(Location location) {
		if (location == null || location.getWorld() == null) {
			throw new IllegalArgumentException("Drop location must have a world");
		}
		return new DropLocationKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
	}
}
```

```java
package com.airdropmc.exceptions;

public class DropLimitException extends Exception {
	public enum Reason {
		REQUEST_PENDING, COOLDOWN, FALLING_CAPACITY, LANDED_CAPACITY, LOCATION_RESERVED, SHUTTING_DOWN
	}

	private final Reason reason;
	private final long retryAfterSeconds;

	public DropLimitException(Reason reason) {
		this(reason, 0);
	}

	public DropLimitException(Reason reason, long retryAfterSeconds) {
		super("Drop rejected: " + reason);
		this.reason = reason;
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public Reason getReason() { return reason; }
	public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
```

- [ ] **Step 4: Implement the synchronized controller and lease state machine**

Implement the complete synchronized controller:

```java
package com.airdropmc.limits;

import com.airdropmc.exceptions.DropLimitException;
import com.airdropmc.exceptions.DropLimitException.Reason;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class DropAdmissionController {
	public enum LeaseState { RESERVED, FALLING, LANDED, CLOSED }
	public record Snapshot(int falling, int landedClaims, int locations, int pending, int cooldowns, boolean accepting) {}
	private final LongSupplier nanoTime;
	private record Cooldown(long startedAt, long durationNanos) {}
	private final Map<UUID, Cooldown> cooldowns = new HashMap<>();
	private final Set<UUID> pending = new HashSet<>();
	private final Set<DropLocationKey> locations = new HashSet<>();
	private final Set<Lease> liveLeases = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
	private int falling;
	private int landedClaims;
	private boolean accepting = true;

	public DropAdmissionController() { this(System::nanoTime); }
	DropAdmissionController(LongSupplier nanoTime) { this.nanoTime = nanoTime; }

	public synchronized Lease acquirePlayer(UUID playerId, boolean cooldownBypass,
			DropLocationKey location, DropLimitSettings settings) throws DropLimitException {
		if (playerId == null) throw new IllegalArgumentException("playerId is required");
		return acquire(playerId, cooldownBypass, location, settings);
	}
	public synchronized Lease acquireSystem(DropLocationKey location, DropLimitSettings settings)
			throws DropLimitException { return acquire(null, true, location, settings); }
	public synchronized void stopAccepting() { accepting = false; }
	public synchronized void clear() {
		for (Lease lease : new ArrayList<>(liveLeases)) release(lease);
		cooldowns.clear();
		pending.clear();
		locations.clear();
		falling = 0;
		landedClaims = 0;
	}
	public synchronized Snapshot snapshot() { return new Snapshot(falling, landedClaims, locations.size(), pending.size(), cooldowns.size(), accepting); }

	private Lease acquire(UUID playerId, boolean cooldownBypass, DropLocationKey location,
			DropLimitSettings settings) throws DropLimitException {
		if (location == null || settings == null) throw new IllegalArgumentException("location and settings are required");
		long now = nanoTime.getAsLong();
		if (!accepting) throw new DropLimitException(Reason.SHUTTING_DOWN);
		cooldowns.entrySet().removeIf(entry -> elapsed(now, entry.getValue()) >= entry.getValue().durationNanos());
		if (!cooldownBypass && pending.contains(playerId)) throw new DropLimitException(Reason.REQUEST_PENDING);
		Cooldown cooldown = cooldownBypass ? null : cooldowns.get(playerId);
		if (cooldown != null) {
			long remaining = cooldown.durationNanos() - elapsed(now, cooldown);
			throw new DropLimitException(Reason.COOLDOWN,
					Math.max(1, (remaining + 999_999_999L) / 1_000_000_000L));
		}
		if (falling >= settings.maxFalling()) throw new DropLimitException(Reason.FALLING_CAPACITY);
		if (landedClaims >= settings.maxLanded()) throw new DropLimitException(Reason.LANDED_CAPACITY);
		if (locations.contains(location)) throw new DropLimitException(Reason.LOCATION_RESERVED);

		Lease lease = new Lease(playerId, cooldownBypass, location, settings.requestCooldown());
		liveLeases.add(lease);
		falling++;
		landedClaims++;
		locations.add(location);
		if (!cooldownBypass) pending.add(playerId);
		return lease;
	}

	private static long elapsed(long now, Cooldown cooldown) {
		return now - cooldown.startedAt();
	}

	private synchronized void commitSpawn(Lease lease) {
		if (lease.state != LeaseState.RESERVED) throw new IllegalStateException("Lease is not reserved");
		lease.state = LeaseState.FALLING;
		lease.requestCommitted = true;
		if (lease.playerId == null || lease.cooldownBypass) return;
		pending.remove(lease.playerId);
		cooldowns.put(lease.playerId, new Cooldown(nanoTime.getAsLong(), lease.cooldown.toNanos()));
	}

	private synchronized void markLanded(Lease lease) {
		if (lease.state != LeaseState.FALLING) throw new IllegalStateException("Lease is not falling");
		lease.state = LeaseState.LANDED;
		falling--;
	}

	private synchronized void release(Lease lease) {
		if (lease.state == LeaseState.CLOSED) return;
		if (lease.state == LeaseState.RESERVED || lease.state == LeaseState.FALLING) falling--;
		landedClaims--;
		locations.remove(lease.location);
		if (lease.playerId != null && !lease.requestCommitted) pending.remove(lease.playerId);
		liveLeases.remove(lease);
		lease.state = LeaseState.CLOSED;
	}

	public final class Lease implements AutoCloseable {
		private final UUID playerId;
		private final boolean cooldownBypass;
		private final DropLocationKey location;
		private final Duration cooldown;
		private LeaseState state = LeaseState.RESERVED;
		private boolean requestCommitted;

		private Lease(UUID playerId, boolean cooldownBypass, DropLocationKey location, Duration cooldown) {
			this.playerId = playerId;
			this.cooldownBypass = cooldownBypass;
			this.location = location;
			this.cooldown = cooldown;
		}

		public void commitSpawn() { DropAdmissionController.this.commitSpawn(this); }
		public void markLanded() { DropAdmissionController.this.markLanded(this); }
		public DropLocationKey location() { return location; }
		public boolean owns(DropLocationKey candidate) { return location.equals(candidate); }
		public LeaseState state() { synchronized (DropAdmissionController.this) { return state; } }
		@Override public void close() { DropAdmissionController.this.release(this); }
	}
}
```

- [ ] **Step 5: Run admission tests and commit**

Run the Step 2 command. Expected: PASS, including the 20-request concurrency case.

```bash
git add src/main/java/com/airdropmc/exceptions/DropLimitException.java src/main/java/com/airdropmc/limits/DropAdmissionController.java src/main/java/com/airdropmc/limits/DropLocationKey.java src/test/java/com/airdropmc/limits/DropAdmissionControllerTest.java
git commit -m "AIRDR-4: add atomic drop admission leases"
```

## Task 3: Route every drop through pre-payment admission

AIRDR-3's committed design replaces this synchronous paid `DropController` path. Keep the charge-confirmation return value and admitted spawn helper as narrow seams, avoid introducing a second payment abstraction here, and rebase AIRDR-3 on these admission guarantees if that work starts concurrently.

**Files:**
- Modify: `src/main/java/com/airdropmc/Airdrop.java`
- Modify: `src/main/java/com/airdropmc/controllers/DropController.java`
- Modify: `src/main/java/com/airdropmc/packages/Package.java`
- Modify: `src/main/java/com/airdropmc/commands/DropCommand.java`
- Modify: `src/main/java/com/airdropmc/lang/MessageKey.java`
- Modify: `src/main/resources/lang/en.yml`
- Modify: `src/test/java/com/airdropmc/controllers/DropControllerEconomyFlowTest.java`

- [ ] **Step 1: Write failing controller ordering and release tests**

Extend `DropControllerEconomyFlowTest` with a real controller injected into `Airdrop`, an explicit enabled economy config/provider, an enabled MockBukkit plugin where scheduling is exercised, and direct snapshots. Reset every modified static field in `@AfterEach`, even if an assertion fails:

```java
@Test
void playerDrop_capacityRejectionOccursBeforeChargeOrItems() throws Exception {
	DropAdmissionController admission = installAdmission();
	installLimitConfig(1, 10);
	DropAdmissionController.Lease occupied = admission.acquireSystem(otherKey(), limits(1, 10));
	PlayerMock player = operatorAtClearSky();
	Package pkg = affordablePackage(player);

	DropLimitException rejection = assertThrows(DropLimitException.class,
			() -> DropController.playerInitiatedDropPackage(pkg, player, options()));

	assertEquals(Reason.FALLING_CAPACITY, rejection.getReason());
	verify(pkg, never()).chargeUser(player);
	verify(pkg, never()).getItems();
	occupied.close();
}

@Test
void playerDrop_chargeFailureReleasesAllReservationsAndStartsNoCooldown() throws Exception {
	DropAdmissionController admission = installAdmission();
	PlayerMock player = operatorAtClearSky();
	Package pkg = packageWhoseChargeFails(player);

	assertThrows(CannotAffordException.class,
			() -> DropController.playerInitiatedDropPackage(pkg, player, options()));

	assertEquals(new Snapshot(0, 0, 0, 0, 0, true), admission.snapshot());
}

@Test
void playerDrop_spawnFailureReleasesAndAttemptsRefundWithoutCooldown() throws Exception {
	DropAdmissionController admission = installAdmission();
	PlayerMock player = operatorAtClearSky();
	Package pkg = affordablePackage(player);
	when(pkg.getItems()).thenReturn(List.of());
	when(pkg.chargeUser(player)).thenReturn(true);
	configureWorldSpawnToThrow(new IllegalStateException("spawn failed"));

	assertThrows(IllegalStateException.class,
			() -> DropController.playerInitiatedDropPackage(pkg, player, options()));

	assertEquals(0, admission.snapshot().falling());
	assertEquals(0, admission.snapshot().landedClaims());
	verify(economy).deposit(player, pkg.getPrice());
}

@Test
void playerDrop_payloadFailureBeforeChargeReleasesLeaseWithoutRefund() throws Exception {
	DropAdmissionController admission = installAdmission();
	PlayerMock player = operatorAtClearSky();
	Package pkg = affordablePackage(player);
	when(pkg.getItems()).thenThrow(new IllegalStateException("payload failed"));

	assertThrows(IllegalStateException.class,
			() -> DropController.playerInitiatedDropPackage(pkg, player, options()));

	assertEquals(new Snapshot(0, 0, 0, 0, 0, true), admission.snapshot());
	verify(pkg, never()).chargeUser(player);
	verify(economy, never()).deposit(any(), anyDouble());
}

@Test
void playerDrop_chargeConfirmationMessageFailureDoesNotRollbackSuccessfulDrop() throws Exception {
	DropAdmissionController admission = installAdmission();
	PlayerMock player = operatorAtClearSky();
	Package pkg = affordablePackage(player);
	when(pkg.chargeUser(player)).thenReturn(true);
	try (MockedStatic<ChatHandler> chat = mockStatic(ChatHandler.class, CALLS_REAL_METHODS)) {
		chat.when(() -> ChatHandler.send(player, MessageKey.DROP_CHARGED,
				Map.of("amount", "10.0"))).thenThrow(new IllegalStateException("feedback failed"));
		assertDoesNotThrow(() -> DropController.playerInitiatedDropPackage(pkg, player, options()));
	}

	assertEquals(1, admission.snapshot().falling());
	verify(economy, never()).deposit(any(), anyDouble());
}
```

Add these exact test helpers so the snippets use the production singleton/config boundary:

```java
private DropAdmissionController installAdmission() throws Exception {
	DropAdmissionController admission = new DropAdmissionController();
	Field field = Airdrop.class.getDeclaredField("dropAdmissionController");
	field.setAccessible(true);
	field.set(null, admission);
	return admission;
}

private void installLimitConfig(int maxFalling, int maxLanded) throws Exception {
	YamlConfiguration values = new YamlConfiguration();
	values.set(ConfigKeys.DROP_REQUEST_COOLDOWN_SECONDS, 30);
	values.set(ConfigKeys.DROP_MAX_FALLING, maxFalling);
	values.set(ConfigKeys.DROP_MAX_LANDED, maxLanded);
	values.set(ConfigKeys.DROP_LANDED_LIFETIME_SECONDS, 600);
	Config config = mock(Config.class);
	when(config.getConfig()).thenReturn(values);
	Field field = Airdrop.class.getDeclaredField("configuration");
	field.setAccessible(true);
	field.set(null, config);
}

private DropLimitSettings limits(int maxFalling, int maxLanded) {
	return new DropLimitSettings(Duration.ofSeconds(30), maxFalling, maxLanded, Duration.ofSeconds(600));
}

private DropLocationKey otherKey() {
	return new DropLocationKey(world.getUID(), 50, 65, 50);
}

private PlayerMock operatorAtClearSky() {
	PlayerMock player = server.addPlayer();
	player.setOp(true);
	player.teleport(new Location(world, 0, 120, 0));
	return player;
}

private Package affordablePackage(Player player) throws Exception {
	Package pkg = mock(Package.class);
	when(pkg.getName()).thenReturn("starter");
	when(pkg.getPrice()).thenReturn(10.0);
	when(pkg.canAfford(player)).thenReturn(true);
	when(pkg.getItems()).thenReturn(List.of());
	when(pkg.chargeUser(player)).thenReturn(true);
	return pkg;
}

private Package packageWhoseChargeFails(Player player) throws Exception {
	Package pkg = affordablePackage(player);
	when(pkg.chargeUser(player)).thenThrow(new CannotAffordException(player.getName(), 10.0));
	return pkg;
}

private DropOptions options() {
	return DropOptions.createDefault().withDropHeight(20);
}

private void configureWorldSpawnToThrow(RuntimeException failure) {
	doThrow(failure).when(world).spawn(
			any(Location.class), eq(FallingBlock.class), ArgumentMatchers.<Consumer<FallingBlock>>any());
}
```

Create `world` as a Mockito spy around the registered `WorldMock` so `configureWorldSpawnToThrow` retains normal MockBukkit behavior until the spawn boundary. The economy fixture must set `economy.enabled: true`, install a provider whose withdrawal succeeds, and configure the plugin singleton before invoking production code. Add a programmatic-drop test that fills capacity, calls `DropController.dropPackage`, and observes `DropLimitException` before `pkg.getItems()`. Exercise post-charge spawn failure with this falling-entity boundary; do not pretend `getItems()` failed after charging because payload retrieval deliberately happens before withdrawal.

- [ ] **Step 2: Run controller tests and verify RED**

Run:

```bash
./gradlew test --tests com.airdropmc.controllers.DropControllerEconomyFlowTest
```

Expected: compilation failures for the new exception signature, boolean `chargeUser`, and missing controller singleton.

- [ ] **Step 3: Install the admission controller in the plugin lifecycle**

Add to `Airdrop`:

```java
private static DropAdmissionController dropAdmissionController;

// onEnable, after configuration defaults are available:
dropAdmissionController = new DropAdmissionController();

public static DropAdmissionController getDropAdmissionController() {
	return dropAdmissionController;
}
```

Do not reset this field during `/airdrop reload`; live leases and cooldowns must remain.

- [ ] **Step 4: Refactor DropController around one admitted helper**

Add a private target record:

```java
private record DropTarget(Location spawnLocation, DropLocationKey landingKey) {}

private static DropTarget getDropTarget(World world, Location requested, DropOptions options)
		throws SkyNotClearException {
	Location ground = world.getHighestBlockAt(requested.getBlockX(), requested.getBlockZ()).getLocation()
			.add(HALF_BLOCK, ZERO_BLOCKS, HALF_BLOCK);
	if (requested.getBlockY() < ground.getBlockY()) throw new SkyNotClearException(requested);
	Location spawn = ground.clone().add(ZERO_BLOCKS, options.getDropHeight(), ZERO_BLOCKS);
	Location intendedBarrel = ground.clone().add(ZERO_BLOCKS, 1, ZERO_BLOCKS);
	return new DropTarget(spawn, DropLocationKey.from(intendedBarrel));
}
```

Change `Package.chargeUser(Player)` to return `false` when economy is disabled and `true` immediately after a successful provider withdrawal. Remove `DROP_CHARGED` messaging from that method. The return value confirms only the local transaction result; do not claim that it resolves an ambiguous third-party provider failure (AIRDR-3 owns that integration).

Change every public drop signature to include `DropLimitException`. For player requests, validate permission/economy/affordability and target first, acquire, then materialize the payload before payment:

```java
DropAdmissionController admission = requireAdmissionController();
DropAdmissionController.Lease lease = admission.acquirePlayer(
		player.getUniqueId(), PermissionsHelper.hasCooldownBypass(player), target.landingKey(),
		ConfigKeys.getDropLimitSettings());
boolean charged = false;
try {
	List<ItemStack> items = pkg.getItems();
	charged = pkg.chargeUser(player);
	dropPackageAtLocation(items, world, target.spawnLocation(), options, lease);
} catch (CannotAffordException | EconomyUnavailableException failure) {
	lease.close();
	throw failure;
} catch (RuntimeException failure) {
	lease.close();
	if (charged) attemptRefundOnDropFailure(pkg, player, failure);
	throw failure;
}
if (charged) sendChargeConfirmationBestEffort(pkg, player);
```

`sendChargeConfirmationBestEffort` sends `DROP_CHARGED` after the committed drop and catches/logs feedback failures without destroying the crate or refunding a successful drop.

For programmatic drops, call `acquireSystem`, retrieve the payload, and use the same helper. From lease acquisition until crate ownership is established, every statement is inside one cleanup boundary. Implement the helper so payload/construction/spawn/event failure cannot leak a lease:

```java
private static Crate dropPackageAtLocation(List<ItemStack> items, World world, Location spawn, DropOptions options,
		DropAdmissionController.Lease lease) {
	Crate crate = null;
	try {
		crate = createCrate(spawn.clone(), world, items, options, lease);
		crate.dropCrate();
		Bukkit.getPluginManager().callEvent(new PackageDropEvent(crate, world, crate.getDropLocation()));
		lease.commitSpawn();
		return crate;
	} catch (RuntimeException failure) {
		if (crate != null) CrateManager.removeCrateAndDestroy(crate);
		else lease.close();
		throw failure;
	}
}
```

Paper logs and swallows exceptions thrown by ordinary event listeners, so success at this boundary means `callEvent()` returned. Do not promise rollback for a third-party listener exception that Paper does not expose. A listener that intentionally needs to veto a drop would require a separately designed cancellable/result contract.

- [ ] **Step 5: Add typed localized feedback**

Add these keys and defaults:

```java
ERROR_DROP_REQUEST_PENDING("errors.drop-request-pending", "A drop request is already being processed"),
ERROR_DROP_COOLDOWN("errors.drop-cooldown", "Wait {seconds} seconds before requesting another airdrop"),
ERROR_DROP_FALLING_LIMIT("errors.drop-falling-limit", "Too many airdrops are currently falling; try again shortly"),
ERROR_DROP_LANDED_LIMIT("errors.drop-landed-limit", "Too many landed airdrops are active; try again later"),
ERROR_DROP_LOCATION_RESERVED("errors.drop-location-reserved", "An airdrop already owns this landing location"),
ERROR_DROP_SHUTTING_DOWN("errors.drop-shutting-down", "Airdrops are unavailable while the plugin is shutting down"),
```

Catch `DropLimitException` in `DropCommand` and use an exhaustive switch. Only the cooldown case supplies `Map.of("seconds", String.valueOf(e.getRetryAfterSeconds()))`; every other case sends its fixed message.

- [ ] **Step 6: Run controller tests and commit**

Run the Step 2 command plus `./gradlew test --tests com.airdropmc.commands.*`. Expected: PASS.

```bash
git add src/main/java/com/airdropmc/Airdrop.java src/main/java/com/airdropmc/commands/DropCommand.java src/main/java/com/airdropmc/controllers/DropController.java src/main/java/com/airdropmc/packages/Package.java src/main/java/com/airdropmc/lang/MessageKey.java src/main/resources/lang/en.yml src/test/java/com/airdropmc/controllers/DropControllerEconomyFlowTest.java
git commit -m "AIRDR-4: admit drops before economy withdrawal"
```

## Task 4: Make crate registration and destruction collision-safe and idempotent

**Files:**
- Modify: `src/main/java/com/airdropmc/Crate.java`
- Modify: `src/main/java/com/airdropmc/helpers/CrateManager.java`
- Modify: `src/test/java/com/airdropmc/CrateDestroyTest.java`
- Modify: `src/test/java/com/airdropmc/helpers/CrateManagerTest.java`

- [ ] **Step 1: Write failing collision, remove-by-value, and duplicate-destroy tests**

```java
@Test
void addLandedCrate_rejectsCollisionWithoutReplacingOwner() {
	Location location = new Location(world, 10, 64, 10);
	assertTrue(CrateManager.addCrate(location, first));
	assertFalse(CrateManager.addCrate(location, second));
	assertSame(first, CrateManager.getCrate(location));
}

@Test
void removeCrateAndDestroy_byCrateRemovesEveryLookupAndDestroysOnce() {
	CrateManager.addCrate(fallingBlock, crate);
	CrateManager.addCrate(location, crate);
	assertTrue(CrateManager.removeCrateAndDestroy(crate));
	assertFalse(CrateManager.hasCrate(fallingBlock));
	assertNull(CrateManager.getCrate(location));
	verify(crate).destroy();
}

@Test
void destroy_closesLeaseAndCancelsResourcesOnlyOnce() {
	crate.destroy();
	crate.destroy();
	assertEquals(0, admission.snapshot().falling());
	verify(fallingBlock, times(1)).remove();
}
```

- [ ] **Step 2: Run crate/manager tests and verify RED**

Run:

```bash
./gradlew test --tests com.airdropmc.CrateDestroyTest --tests com.airdropmc.helpers.CrateManagerTest
```

Expected: compilation/assertion failures because registration returns `void`, no remove-by-crate overload exists, and `Crate` has no lease/idempotence guard.

- [ ] **Step 3: Implement collision-safe manager operations**

Change both registration methods to return `boolean` and never replace:

```java
public static synchronized boolean addCrate(FallingBlock block, Crate crate) {
	return crateMap.putIfAbsent(block, crate) == null;
}

public static synchronized boolean addCrate(Location location, Crate crate) {
	if (location == null || crate == null) return false;
	DropLocationKey key = DropLocationKey.from(location);
	return landedCrateMap.putIfAbsent(key, crate) == null;
}
```

Change `landedCrateMap` and all chunk/world iteration to use the shared `DropLocationKey`; remove the equivalent private `BlockKey`. Reject null entity/crate inputs in the falling overload rather than admitting invalid map entries.

Update `Crate.dropCrate()` to enforce the returned invariant:

```java
if (!CrateManager.addCrate(fallingCrate, this)) {
	throw new IllegalStateException("Falling crate entity is already tracked");
}
```

Add remove-by-value cleanup:

```java
public static synchronized boolean removeCrateAndDestroy(Crate crate) {
	if (crate == null) return false;
	boolean removed = crateMap.entrySet().removeIf(entry -> entry.getValue() == crate);
	removed |= landedCrateMap.entrySet().removeIf(entry -> entry.getValue() == crate);
	crate.destroy();
	return removed;
}
```

Preserve the existing location/entity overload semantics and make `clearAll()` deduplicate crates before destruction as it does today.

- [ ] **Step 4: Give Crate one lease and a terminal destroy boundary**

Change the constructor to require `DropAdmissionController.Lease`, add `private boolean destroyed`, and make destruction synchronized:

```java
public Crate(Location location, World world, List<ItemStack> contents, DropOptions options,
		DropAdmissionController.Lease lease) {
	this.dropLocation = location.clone();
	this.world = world;
	this.contents = cloneContents(contents);
	this.state = State.FALLING;
	this.options = Objects.requireNonNull(options);
	this.lease = Objects.requireNonNull(lease);
	this.parachuteSystem = new ParachuteSystem(world, options);
}

public synchronized void destroy() {
	if (destroyed) return;
	destroyed = true;
	cleanupResource("falling crate entity", () -> {
		if (state == State.FALLING && fallingCrate != null && !fallingCrate.isDead()) {
			fallingCrate.setGravity(true);
			fallingCrate.remove();
		}
	});
	cleanupResource("parachute system", () -> {
		if (parachuteSystem != null) parachuteSystem.cancel();
	});
	stopEffects();
	cleanupResource("landed expiry", () -> {
		if (expiryTask != null && !expiryTask.isCancelled()) expiryTask.cancel();
	});
	expiryTask = null;
	cleanupResource("landing effect", () -> {
		if (landingEffectTask != null && !landingEffectTask.isCancelled()) landingEffectTask.cancel();
	});
	landingEffectTask = null;
	cleanupResource("flare effect", () -> {
		if (flareEffect != null && !flareEffect.isCancelled()) flareEffect.cancel();
	});
	cleanupResource("landed barrel", () -> {
		if (state == State.LANDED && blockChest != null && blockChest.getType() == Material.BARREL) {
			blockChest.setType(Material.AIR);
		} else if (state == State.LANDED && landedLocation != null
				&& landedLocation.getBlock().getType() == Material.BARREL) {
			landedLocation.getBlock().setType(Material.AIR);
		}
	});
	lease.close();
}

private void cleanupResource(String resource, Runnable cleanup) {
	try {
		cleanup.run();
	} catch (RuntimeException failure) {
		AirdropLogger.log(Level.WARNING, "Failed to clean up crate " + resource, failure);
	}
}
```

Update `stopEffects()` to clear task ownership after cancelling:

```java
public synchronized void stopEffects() {
	cleanupResource("glow task", () -> {
		if (glowTask != null && !glowTask.isCancelled()) glowTask.cancel();
	});
	cleanupResource("smoke task", () -> {
		if (smokeTask != null && !smokeTask.isCancelled()) smokeTask.cancel();
	});
	glowTask = null;
	smokeTask = null;
}
```

- [ ] **Step 5: Update constructor call sites/tests, run, and commit**

Update every test crate construction to acquire a real system lease. For tests that call `land` directly, call `lease.commitSpawn()` first.

Run the Step 2 command and `./gradlew test`. Expected: PASS.

```bash
git add src/main/java/com/airdropmc/Crate.java src/main/java/com/airdropmc/helpers/CrateManager.java src/test/java/com/airdropmc/CrateDestroyTest.java src/test/java/com/airdropmc/helpers/CrateManagerTest.java src/test/java/com/airdropmc/ParachuteSystemTest.java
git commit -m "AIRDR-4: make crate cleanup idempotent"
```

## Task 5: Convert landing capacity and expire landed crates

**Files:**
- Modify: `src/main/java/com/airdropmc/Crate.java`
- Modify: `src/main/java/com/airdropmc/listeners/FallingCrateListener.java`
- Modify: `src/test/java/com/airdropmc/CrateDestroyTest.java`
- Modify: `src/test/java/com/airdropmc/listeners/FallingCrateListenerTest.java`
- Modify: `src/test/java/com/airdropmc/tasks/RenderPackageLandedTaskTest.java`

- [ ] **Step 1: Write failing landing conversion and expiry tests**

Capture the scheduled expiry runnable with Mockito or advance MockBukkit ticks:

```java
@Test
void land_convertsLeaseAndSchedulesBoundedExpiry() {
	crate.land(block);
	assertEquals(0, admission.snapshot().falling());
	assertEquals(1, admission.snapshot().landedClaims());
	verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(12_000L));
}

@Test
void expiry_removesCrateStopsEffectsAndReleasesLandedClaim() {
	crate.land(block);
	expiryRunnable.run();
	assertNull(CrateManager.getCrate(block.getLocation()));
	assertEquals(0, admission.snapshot().landedClaims());
	verify(glowTask).cancel();
	verify(smokeTask).cancel();
}

@Test
void failedLandedRegistrationDestroysNewCrateWithoutReplacingExistingOwner() {
	CrateManager.addCrate(block.getLocation(), existingCrate);
	assertThrows(IllegalStateException.class, () -> crate.land(block));
	assertSame(existingCrate, CrateManager.getCrate(block.getLocation()));
	assertEquals(0, admission.snapshot().landedClaims());
}

@Test
void land_rejectsActualBlockThatDoesNotMatchReservedLocation() {
	Block unexpected = world.getBlockAt(block.getX() + 1, block.getY(), block.getZ());
	assertThrows(IllegalStateException.class, () -> crate.land(unexpected));
	assertNull(CrateManager.getCrate(unexpected.getLocation()));
	assertEquals(Material.AIR, unexpected.getType());
}

@Test
void land_rejectsUnavailablePluginInsteadOfCreatingImmortalCrate() {
	disablePluginSingleton();
	assertThrows(IllegalStateException.class, () -> crate.land(block));
	assertNull(CrateManager.getCrate(block.getLocation()));
	assertEquals(Material.AIR, block.getType());
}
```

In `FallingCrateListenerTest`, make the falling entity coordinates differ from the event target and assert that `EntityChangeBlockEvent#getBlock()` is passed to `land`. Add rollback coverage for a reserved/event target mismatch. Restore the plugin singleton in `@AfterEach`.

- [ ] **Step 2: Run landing tests and verify RED**

Run:

```bash
./gradlew test --tests com.airdropmc.CrateDestroyTest --tests com.airdropmc.listeners.FallingCrateListenerTest
```

Expected: failures because landing does not convert the lease or schedule expiry.

- [ ] **Step 3: Convert only after collision-safe landed registration**

Within `Crate.land`, validate the actual event block against the reserved key and require an enabled plugin before registration or block mutation. Then reserve the landed lookup before changing crate state or touching the block. This ensures a mismatch or scheduling-unavailable path fails closed, and collision rollback cannot destroy another crate's barrel:

```java
Location candidate = block.getLocation().clone();
DropLocationKey actualKey = DropLocationKey.from(candidate);
if (!lease.owns(actualKey)) {
	throw new IllegalStateException("Landed block does not match the reserved location");
}
Airdrop plugin = getEnabledPlugin();
if (plugin == null) {
	throw new IllegalStateException("Cannot land crate while plugin is unavailable");
}
if (!CrateManager.addCrate(candidate, this)) {
	throw new IllegalStateException("Another crate already owns the landed location");
}
this.blockChest = block;
this.landedLocation = candidate;
this.state = State.LANDED;
blockChest.setType(Material.BARREL);
BlockState barrelState = blockChest.getState();
if (!(barrelState instanceof Barrel barrel)) {
	throw new IllegalStateException("Failed to create barrel at landed location");
}
int overflowStackCount = 0;
for (ItemStack item : contents) {
	Map<Integer, ItemStack> overflow = barrel.getInventory().addItem(item);
	for (ItemStack remaining : overflow.values()) {
		if (remaining == null || remaining.getType().isAir()) continue;
		overflowStackCount++;
		world.dropItemNaturally(landedLocation.clone().add(0.5, 0.5, 0.5), remaining);
	}
}
if (overflowStackCount > 0) {
	AirdropLogger.warning("Dropped " + overflowStackCount
			+ " overflow item stack(s) at a landed crate because barrel inventory was full");
}
lease.markLanded();
scheduleExpiry(plugin);
```

Keep `FallingCrateListener` rollback through `CrateManager.removeCrateAndDestroy(crate)` so partial landed registration and the lease are removed together. Do not perform another capacity check during landing.

- [ ] **Step 4: Schedule and cancel landed expiry**

After successful transition and plugin validation, schedule expiry before optional effects. If scheduler registration throws (including because the plugin was disabled between validation and scheduling), let the listener's rollback destroy the barrel and release the lease:

```java
private void scheduleExpiry(Airdrop plugin) {
	long ticks = ConfigKeys.getDropLimitSettings().landedLifetimeTicks();
	expiryTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
		expiryTask = null;
		CrateManager.removeCrateAndDestroy(this);
	}, ticks);
}
```

Call it for every landed crate regardless of whether visual effects are enabled. Ensure `destroy()` cancels a still-pending expiry task and that opening a crate stops continuous effects without cancelling expiry.

Store the `BukkitTask` returned by the one-shot landing effect as `landingEffectTask` so destruction before its next tick cancels every task owned by the crate.

Update `FallingCrateListener` to use the event's authoritative block throughout:

```java
Block landingBlock = e.getBlock();
Location landingLocation = landingBlock.getLocation();
World world = landingBlock.getWorld();
try {
	landedCrate.land(landingBlock);
} catch (RuntimeException landFailure) {
	CrateManager.removeCrateAndDestroy(landedCrate);
	throw landFailure;
}
Bukkit.getPluginManager().callEvent(
		new PackageLandEvent(landedCrate, world, landingLocation, landingBlock));
```

Keep cancelling the change event and removing the tracked falling entity before conversion. `Entity#getLocation()` is not an acceptable substitute for `EntityChangeBlockEvent#getBlock()`.

- [ ] **Step 5: Run landing/listener tests and commit**

Run the Step 2 command plus all listener/task tests. Expected: PASS.

```bash
git add src/main/java/com/airdropmc/Crate.java src/main/java/com/airdropmc/listeners/FallingCrateListener.java src/test/java/com/airdropmc/CrateDestroyTest.java src/test/java/com/airdropmc/listeners/FallingCrateListenerTest.java src/test/java/com/airdropmc/tasks/RenderPackageLandedTaskTest.java
git commit -m "AIRDR-4: expire landed crates safely"
```

## Task 6: Preserve reload state and harden plugin shutdown

**Files:**
- Modify: `src/main/java/com/airdropmc/Airdrop.java`
- Modify: `src/test/java/com/airdropmc/commands/CmdAirdropReloadTest.java`
- Modify: `src/test/java/com/airdropmc/commands/CmdAirdropLifecycleSafetyTest.java`

- [ ] **Step 1: Write failing reload and disable ordering tests**

```java
@Test
void reload_preservesAdmissionInstanceAndLiveState() throws Exception {
	DropAdmissionController admission = Airdrop.getDropAdmissionController();
	DropAdmissionController.Lease lease = admission.acquirePlayer(
			player.getUniqueId(), false, new DropLocationKey(world.getUID(), 0, 65, 0),
			new DropLimitSettings(Duration.ofSeconds(30), 3, 10, Duration.ofSeconds(600)));
	lease.commitSpawn();
	lease.close();
	new CmdAirdrop().onCommand(player, command, "airdrop", new String[]{"reload"});
	assertSame(admission, Airdrop.getDropAdmissionController());
	assertEquals(1, admission.snapshot().cooldowns());
}

@Test
void onDisable_stopsAdmissionsAndLeavesZeroCapacity() throws Exception {
	Airdrop plugin = mock(Airdrop.class, CALLS_REAL_METHODS);
	DropAdmissionController admission = new DropAdmissionController();
	Field controllerField = Airdrop.class.getDeclaredField("dropAdmissionController");
	controllerField.setAccessible(true);
	controllerField.set(null, admission);
	DropAdmissionController.Lease lease = admission.acquireSystem(
			new DropLocationKey(world.getUID(), 0, 65, 0),
			new DropLimitSettings(Duration.ofSeconds(30), 3, 10, Duration.ofSeconds(600)));
	FallingBlock fallingBlock = mock(FallingBlock.class);
	Crate crate = mock(Crate.class);
	CrateManager.addCrate(fallingBlock, crate);
	plugin.onDisable();
	assertFalse(admission.snapshot().accepting());
	assertEquals(0, admission.snapshot().falling());
	assertEquals(0, admission.snapshot().landedClaims());
	assertTrue(CrateManager.getCrateMap().isEmpty());
	verify(crate).destroy();
}
```

- [ ] **Step 2: Run lifecycle tests and verify RED**

Run:

```bash
./gradlew test --tests com.airdropmc.commands.CmdAirdropReloadTest --tests com.airdropmc.commands.CmdAirdropLifecycleSafetyTest
```

Expected: disable assertions fail until shutdown is reordered and admission is cleared.

- [ ] **Step 3: Reorder shutdown explicitly**

Implement the start of `onDisable` exactly in this order:

```java
DropAdmissionController admission = dropAdmissionController;
if (admission != null) admission.stopAccepting();
CrateManager.clearAll();
if (admission != null) admission.clear();
Bukkit.getScheduler().cancelTasks(this);
PackageManager.clear();
```

After existing listener/GUI cleanup, set `dropAdmissionController = null` with the other static fields. Keep `/airdrop reload` unchanged with respect to admission; dynamic `ConfigKeys.getDropLimitSettings()` snapshots apply new caps only to future acquisitions and expiry scheduling.

- [ ] **Step 4: Run lifecycle and full tests, then commit**

Run the Step 2 command and `./gradlew test`. Expected: PASS.

```bash
git add src/main/java/com/airdropmc/Airdrop.java src/test/java/com/airdropmc/commands/CmdAirdropLifecycleSafetyTest.java src/test/java/com/airdropmc/commands/CmdAirdropReloadTest.java
git commit -m "AIRDR-4: clear drop leases on shutdown"
```

## Task 7: Document, verify, and exercise the complete acceptance matrix

**Files:**
- Modify: `README.md`
- Modify: relevant tests listed in Tasks 1-6 if full-suite findings expose missing cleanup assertions.

- [ ] **Step 1: Document configuration, semantics, and permission**

Add the `drop.limits` YAML block to the README configuration example and validation list. Add `airdrop.cooldown.bypass` under permissions with this text:

```markdown
- `airdrop.cooldown.bypass`
  - Bypasses only the per-player request cooldown
  - Does not bypass falling or landed capacity, package permission, or economy charging
```

State that active crates/cooldowns survive `/airdrop reload`, lower caps block new requests without deleting existing crates, and active state is intentionally not persisted across a full restart.

- [ ] **Step 2: Run targeted acceptance tests**

Run:

```bash
./gradlew test --tests com.airdropmc.limits.DropAdmissionControllerTest --tests com.airdropmc.controllers.DropControllerEconomyFlowTest --tests com.airdropmc.CrateDestroyTest --tests com.airdropmc.helpers.CrateManagerTest --tests com.airdropmc.listeners.* --tests com.airdropmc.commands.CmdAirdropLifecycleSafetyTest
```

Expected: PASS with cases for capacity rejection before charge, future-landed reservation, exact-once close, reconnect UUID cooldown, reload preservation, location collision, failed spawn/refund, world cleanup, expiry, and disable.

- [ ] **Step 3: Run clean build and inspect generated manifest**

Run:

```bash
./gradlew clean build
```

Expected: `BUILD SUCCESSFUL`.

Run:

```bash
rg -n "airdrop.cooldown.bypass|airdrop.limits.bypass|request-cooldown|max-falling|max-landed|landed-lifetime" build/resources/main/plugin.yml build/resources/main/config.yml README.md
```

Expected: the cooldown permission and four config keys are present; `airdrop.limits.bypass` has no match.

- [ ] **Step 4: Perform the Paper runtime matrix**

Run:

```bash
./gradlew runServer
```

On Paper 1.21.8, verify normal/bypass rapid requests, falling and landed saturation, same-block duplicate requests, balance before/after rejection, reconnect during cooldown, reload with lower limits, landed expiry, world unload, and plugin disable. Record any case that cannot be automated as manual evidence in the PR testing notes; do not represent an unperformed runtime case as passing.

- [ ] **Step 5: Commit documentation and any acceptance-test refinements**

```bash
git add README.md src/test/java
git commit -m "AIRDR-4: document and verify drop limits"
```

## Plan Self-Review Checklist

- Spec coverage: Tasks 1-7 cover configuration, narrow permission, atomic pre-payment admission, future-landed reservation, programmatic callers, collision safety, idempotent cleanup, expiry, reconnect/reload/disable, localized feedback, and documentation.
- Placeholder scan: every code-producing step identifies exact APIs, values, commands, and expected outcomes; no deferred implementation markers remain.
- Type consistency: `DropLimitSettings`, `DropLocationKey`, `DropLimitException.Reason`, `DropAdmissionController.Lease/Snapshot`, `commitSpawn`, `markLanded`, and `close` use the same names in every task.
