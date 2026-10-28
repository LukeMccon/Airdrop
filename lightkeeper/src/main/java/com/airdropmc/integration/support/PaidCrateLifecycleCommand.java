package com.airdropmc.integration.support;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.FallingBlock;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** AIRDR-87: token-scoped observations of real chunk listeners and the scheduled expiry task. */
public final class PaidCrateLifecycleCommand implements CommandExecutor {
	private static final String LIFETIME_PATH = "drop.limits.landed-lifetime-seconds";
	private static final String CONTINUOUS_EFFECTS_PATH = "drop.particles.continuous-effects";
	private static final List<String> TASK_FIELDS =
			List.of("expiryTask", "landingEffectTask", "glowTask", "smokeTask");
	private final Map<String, Scenario> scenarios = new HashMap<>();

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof ConsoleCommandSender) || args.length < 3) {
			return false;
		}
		String action = args[0];
		String token = args[1];
		String probe = args[2];
		if (!token.matches("[A-Za-z0-9_-]{1,80}") || !probe.matches("[A-Za-z0-9_-]{1,80}")) {
			return false;
		}
		String prefix = "AIRDR_87_LIFECYCLE token=" + token + " probe=" + probe + " action=" + action;
		try {
			require(Bukkit.isPrimaryThread(), "fixture command must run on the server thread");
			if (action.equals("begin")) {
				require(args.length == 8, "begin requires world, x, y, z and persistence|expiry");
				begin(token, args);
			} else {
				require(args.length == 3, "observation takes only token and probe");
				if (action.equals("cleanup")) {
					cleanup(token);
				} else {
					Scenario scenario = Objects.requireNonNull(scenarios.get(token), "unknown scenario token");
					switch (action) {
						case "capture" -> capture(scenario);
						case "suspended" -> assertSuspended(scenario);
						case "recovered" -> assertRecovered(scenario);
						case "expired" -> assertExpired(scenario);
						default -> throw new IllegalArgumentException("unknown fixture action");
					}
				}
			}
			Scenario scenario = scenarios.get(token);
			String identity = scenario == null || scenario.original == null ? ""
					: " requestId=" + scenario.requestId + " crateId=" + scenario.crateId
							+ " deadline=" + scenario.deadline;
			sender.sendMessage(prefix + " status=OK" + identity);
		} catch (ReflectiveOperationException | RuntimeException failure) {
			// A correlated failure is not command acceptance and must fail the integration assertion.
			sender.sendMessage(prefix + " status=FAILED detail=" + failure);
		}
		return true;
	}

	private void begin(String token, String[] args) throws ReflectiveOperationException {
		require(!scenarios.containsKey(token), "token already in use");
		require(scenarios.size() < 4, "clear old fixture tokens before starting more scenarios");
		boolean expiry = args[7].equals("expiry");
		require(expiry || args[7].equals("persistence"), "unknown scenario mode");
		World world = Objects.requireNonNull(Bukkit.getWorld(args[3]), "test world");
		Location location = new Location(world,
				Integer.parseInt(args[4]), Integer.parseInt(args[5]), Integer.parseInt(args[6]));
		require(location.getBlock().getType() == Material.AIR, "landing site must start empty");
		Scenario scenario = new Scenario(location, expiry);
		assertAdmission(scenario, 0);
		require(scenarios.values().stream().noneMatch(other -> other.config != null),
				"only one temporary config override may be active");
		scenarios.put(token, scenario);
		// Setup waits for reload publication before mutating this exact live configuration.
		Object wrapper = scenario.plugin.getClass().getMethod("getConfiguration").invoke(null);
		scenario.config = (FileConfiguration) value(wrapper, "getConfig");
		scenario.previousLifetime = scenario.config.get(LIFETIME_PATH, null);
		scenario.previousContinuousEffects = scenario.config.get(CONTINUOUS_EFFECTS_PATH, null);
		scenario.config.set(CONTINUOUS_EFFECTS_PATH, true);
		if (expiry) {
			scenario.config.set(LIFETIME_PATH, 30);
			require(lifetimeSeconds(scenario) == 30, "live lifetime must resolve to the supported 30-second minimum");
		}
	}

	private static void capture(Scenario scenario) throws ReflectiveOperationException {
		require(scenario.original == null, "capture is one-shot");
		Barrel barrel = loadedBarrel(scenario);
		Object crate = Objects.requireNonNull(crateAt(scenario), "landed crate");
		require(Boolean.TRUE.equals(field(crate, "paid")), "scenario must exercise a paid crate");
		assertPremiumContents(barrel.getInventory().getContents());
		if (!scenario.expiry) {
			// Controlled partial-content setup, NOT a claim of vanilla player extraction.
			barrel.getInventory().setItem(0, null);
			ItemStack bread = Objects.requireNonNull(barrel.getInventory().getItem(4)).clone();
			bread.setAmount(1);
			// Decorate the remaining stack without adding or removing any further items.
			ItemMeta metadata = Objects.requireNonNull(bread.getItemMeta());
			metadata.displayName(Component.text("AIRDR-87 remaining bread"));
			metadata.getPersistentDataContainer().set(
					Objects.requireNonNull(NamespacedKey.fromString("airdrop_lightkeeper:paid_lifecycle")),
					PersistentDataType.STRING, "remaining-paid-stack");
			require(bread.setItemMeta(metadata), "remaining stack must accept custom metadata");
			barrel.getInventory().setItem(4, bread);
			require(bread.equals(barrel.getInventory().getItem(4)),
					"named PDC-bearing remaining stack must be present before the exact snapshot");
		}
		scenario.original = crate;
		scenario.contents = cloneContents(barrel.getInventory().getContents());
		scenario.serializedContents = serializeContents(scenario.contents);
		scenario.crateId = (String) value(crate, "getCrateId");
		scenario.requestId = (UUID) Objects.requireNonNull(value(crate, "getRequestId"));
		scenario.deadline = (long) value(crate, "getExpiresAtMillis");
		scenario.persistence = Objects.requireNonNull(persistence(crate, barrel), "paid persistence");
		scenario.lease = field(crate, "lease");
		scenario.admissionSnapshot = value(scenario.admission, "snapshot");
		scenario.originalTasks = tasks(crate);
		assertQueuedGlowTask(scenario.originalTasks);
		require(scenario.deadline > System.currentTimeMillis(), "original expiry must still be in the future");
		require(scenario.originalTasks.containsKey("expiryTask"), "original expiry task must exist");
		require(Bukkit.getScheduler().isQueued(scenario.originalTasks.get("expiryTask").getTaskId()),
				"original expiry task must be queued");
		if (scenario.expiry) {
			Object settings = field(crate, "settings");
			require(java.time.Duration.ofSeconds(30).equals(value(settings, "landedLifetime")),
					"request must snapshot the temporary 30-second lifetime");
		}
		assertAdmission(scenario, 1);
		assertLiveIdentity(scenario, crate, barrel);
	}

	private static void assertSuspended(Scenario scenario) throws ReflectiveOperationException {
		require(scenario.original != null, "capture must precede suspension");
		// No block, chunk or inventory read is permitted in this method, including indirectly.
		require(!isChunkLoaded(scenario), "chunk must actually be unloaded before observing suspension");
		assertNoActiveTracking(scenario);
		assertStopped(scenario.original, scenario.originalTasks);
		require(scenario.originalTasks.get("expiryTask").isCancelled(), "unload must cancel old expiry");
		Map<?, ?> suspended = (Map<?, ?>) staticField(scenario.manager, "suspendedLeases");
		require(suspended.size() == 1 && suspended.get(scenario.crateId) == scenario.lease,
				"unloaded crate must retain exactly its original lease");
		assertAdmission(scenario, 1);
		require(scenario.admissionSnapshot.equals(value(scenario.admission, "snapshot")),
				"suspension must preserve admission claims");
		require(!isChunkLoaded(scenario), "runtime bookkeeping inspection must not load the chunk");
	}

	private static void assertRecovered(Scenario scenario) throws ReflectiveOperationException {
		Barrel barrel = loadedBarrel(scenario);
		Object crate = Objects.requireNonNull(crateAt(scenario), "recovered runtime crate");
		require(crate != scenario.original, "recovery must create a new runtime crate");
		require(Boolean.TRUE.equals(field(crate, "recoveredFromPersistence")), "crate must originate from persistence");
		require(field(crate, "lease") == scenario.lease, "recovery must reuse the retained reservation");
		if (scenario.recovered == null) {
			scenario.recovered = crate;
			scenario.recoveredTasks = tasks(crate);
			require(scenario.recoveredTasks.containsKey("expiryTask"), "recovery must schedule expiry");
			require(scenario.recoveredTasks.get("expiryTask") != scenario.originalTasks.get("expiryTask"),
					"recovery must use a new expiry task");
		} else {
			require(crate == scenario.recovered, "repeated load must not replace recovered runtime identity");
			require(tasks(crate).equals(scenario.recoveredTasks), "repeated load must not replace tasks");
		}
		// Recovery resumes expiry only; landed effects start on physical landing, not chunk reload.
		require(Bukkit.getScheduler().isQueued(scenario.recoveredTasks.get("expiryTask").getTaskId()),
				"recovered expiry task must remain queued");
		assertStopped(scenario.original, scenario.originalTasks);
		assertLiveIdentity(scenario, crate, barrel);
		assertExactContents(scenario, barrel);
		assertAdmission(scenario, 1);
		require(scenario.admissionSnapshot.equals(value(scenario.admission, "snapshot")),
				"recovery must not add a second claim");
		require(((Map<?, ?>) staticField(scenario.manager, "suspendedLeases")).isEmpty(),
				"recovery must consume the suspended lease");
	}

	private static void assertExpired(Scenario scenario) throws ReflectiveOperationException {
		require(scenario.expiry && scenario.original != null, "expiry scenario must have been captured");
		require(System.currentTimeMillis() >= scenario.deadline, "expiry must not run before the original deadline");
		// This one-shot command runs after the retiring task's physical cleanup on the server thread.
		assertStopped(scenario.original, scenario.originalTasks);
		Barrel barrel = loadedBarrel(scenario);
		assertExactContents(scenario, barrel);
		require(!barrel.getInventory().isEmpty(), "expiry must retain a nonempty ordinary barrel");
		require(Boolean.FALSE.equals(scenario.original.getClass().getMethod("hasAirdropMarker", Barrel.class)
				.invoke(null, barrel)), "all Airdrop markers must be cleared");
		require(barrel.getPersistentDataContainer().getKeys().stream()
				.noneMatch(key -> key.getNamespace().equals("airdrop")), "no Airdrop PDC keys may survive expiry");
		assertNoActiveTracking(scenario);
		assertAdmission(scenario, 0);
		require(((Map<?, ?>) staticField(scenario.manager, "suspendedLeases")).isEmpty(),
				"expiry must leave no suspended reservation");
	}

	private static void assertLiveIdentity(Scenario scenario, Object crate, Barrel barrel)
			throws ReflectiveOperationException {
		require(scenario.crateId.equals(value(crate, "getCrateId")), "crate identity changed");
		require(scenario.requestId.equals(value(crate, "getRequestId")), "request identity changed");
		require(Long.valueOf(scenario.deadline).equals(value(crate, "getExpiresAtMillis")), "original deadline changed");
		require(scenario.persistence.equals(persistence(crate, barrel)), "persisted identity/deadline/context changed");
		require(Boolean.TRUE.equals(crate.getClass().getMethod("ownsLandedBarrel", Barrel.class).invoke(crate, barrel)),
				"runtime crate must own the exact barrel identity");
		Optional<?> byCrate = (Optional<?>) scenario.manager.getMethod("findByCrateId", UUID.class)
				.invoke(null, UUID.fromString(scenario.crateId));
		Optional<?> byRequest = (Optional<?>) scenario.manager.getMethod("findByRequestId", UUID.class)
				.invoke(null, scenario.requestId);
		require(byCrate.isPresent() && byRequest.isPresent() && byCrate.get() == byRequest.get(),
				"crate and request indexes must contain the same single live view");
		Object view = byCrate.orElseThrow();
		require(UUID.fromString(scenario.crateId).equals(value(view, "crateId")), "API crate identity changed");
		require(Optional.of(scenario.requestId).equals(value(view, "requestId")), "API request identity changed");
		require(Long.valueOf(scenario.deadline).equals(value(view, "expiresAtMillis")), "API deadline changed");
		require(scenario.manager.getMethod("landedCount").invoke(null).equals(1), "exactly one active landed view");
		require(scenario.manager.getMethod("fallingCount").invoke(null).equals(0), "no active falling view");
	}

	private static void assertNoActiveTracking(Scenario scenario) throws ReflectiveOperationException {
		require(crateAt(scenario) == null, "no raw tracking may remain");
		require(scenario.manager.getMethod("landedCount").invoke(null).equals(0), "no active landed view");
		require(scenario.manager.getMethod("fallingCount").invoke(null).equals(0), "no active falling view");
		require(Optional.empty().equals(scenario.manager.getMethod("findByCrateId", UUID.class)
				.invoke(null, UUID.fromString(scenario.crateId))), "crate API identity must be inactive");
		require(Optional.empty().equals(scenario.manager.getMethod("findByRequestId", UUID.class)
				.invoke(null, scenario.requestId)), "request API identity must be inactive");
	}

	private static void assertAdmission(Scenario scenario, int claims) throws ReflectiveOperationException {
		Object snapshot = value(scenario.admission, "snapshot");
		require(value(snapshot, "landedClaims").equals(claims), "unexpected landed admission count");
		require(value(snapshot, "locations").equals(claims), "unexpected location reservation count");
		require(value(snapshot, "falling").equals(0), "unexpected falling admission count");
		require(value(snapshot, "pending").equals(0), "unexpected pending admission count");
	}

	private static void assertQueuedGlowTask(Map<String, BukkitTask> captured) {
		require(captured.containsKey("glowTask"), "continuous effects must create a real glow task");
		require(Bukkit.getScheduler().isQueued(captured.get("glowTask").getTaskId()),
				"repeating glow task must be queued");
	}

	private static void assertStopped(Object crate, Map<String, BukkitTask> captured)
			throws ReflectiveOperationException {
		require(Boolean.TRUE.equals(field(crate, "destroyed")), "old runtime crate must be retired");
		for (String name : TASK_FIELDS) {
			require(field(crate, name) == null, "retired crate still retains " + name);
		}
		require(field(crate, "flareEffect") == null, "retired crate retains flare task");
		require(captured.get("glowTask").isCancelled(), "retirement must cancel the captured repeating glow task");
		for (Map.Entry<String, BukkitTask> entry : captured.entrySet()) {
			int id = entry.getValue().getTaskId();
			require(!Bukkit.getScheduler().isQueued(id) && !Bukkit.getScheduler().isCurrentlyRunning(id),
					"retired task has not completed: " + entry.getKey());
		}
	}

	private static void restore(Scenario scenario) {
		if (scenario.config != null) {
			scenario.config.set(LIFETIME_PATH, scenario.previousLifetime);
			scenario.config.set(CONTINUOUS_EFFECTS_PATH, scenario.previousContinuousEffects);
			require(Objects.equals(scenario.previousLifetime, scenario.config.get(LIFETIME_PATH, null)),
					"original live lifetime must be restored");
			require(Objects.equals(scenario.previousContinuousEffects, scenario.config.get(CONTINUOUS_EFFECTS_PATH, null)),
					"original live continuous-effects setting must be restored");
			scenario.config = null;
			scenario.previousLifetime = null;
			scenario.previousContinuousEffects = null;
		}
	}

	private void cleanup(String token) throws ReflectiveOperationException {
		Scenario scenario = scenarios.get(token);
		if (scenario == null) {
			return;
		}
		try {
			// Restore the live settings even when any subsequent physical cleanup fails.
			restore(scenario);
		} finally {
			try {
				World world = Objects.requireNonNull(scenario.location.getWorld());
				world.loadChunk(scenario.location.getBlockX() >> 4, scenario.location.getBlockZ() >> 4);
				// Also cover a failure between spawning and capture, without touching another location.
				Map<?, ?> falling = (Map<?, ?>) scenario.manager.getMethod("getCrateMap").invoke(null);
				for (Object key : List.copyOf(falling.keySet())) {
					FallingBlock entity = (FallingBlock) key;
					Location drop = (Location) value(falling.get(key), "getDropLocation");
					if (world.equals(drop.getWorld()) && drop.getBlockX() == scenario.location.getBlockX()
							&& drop.getBlockZ() == scenario.location.getBlockZ()) {
						scenario.manager.getMethod("removeCrateAndDestroy", FallingBlock.class).invoke(null, entity);
					}
				}
				scenario.manager.getMethod("removeCrateAndDestroy", Location.class).invoke(null, scenario.location);
				scenario.location.getBlock().setType(Material.AIR, false);
				world.save();
			} finally {
				// Drop every retained inventory clone, runtime object and task reference, even on failure.
				scenarios.remove(token);
			}
		}
	}

	private static long lifetimeSeconds(Scenario scenario) throws ReflectiveOperationException {
		Class<?> keys = scenario.plugin.getClass().getClassLoader().loadClass("com.airdropmc.config.ConfigKeys");
		Object settings = keys.getMethod("getDropLimitSettings").invoke(null);
		return ((java.time.Duration) value(settings, "landedLifetime")).getSeconds();
	}

	private static Object persistence(Object crate, Barrel barrel) throws ReflectiveOperationException {
		return crate.getClass().getMethod("readPaidPersistence", Barrel.class).invoke(null, barrel);
	}

	private static Barrel loadedBarrel(Scenario scenario) {
		require(isChunkLoaded(scenario), "barrel reads require an already loaded chunk");
		require(scenario.location.getBlock().getState() instanceof Barrel, "paid barrel must exist");
		return (Barrel) scenario.location.getBlock().getState();
	}

	private static boolean isChunkLoaded(Scenario scenario) {
		return Objects.requireNonNull(scenario.location.getWorld()).isChunkLoaded(
				scenario.location.getBlockX() >> 4, scenario.location.getBlockZ() >> 4);
	}

	private static Object crateAt(Scenario scenario) throws ReflectiveOperationException {
		return scenario.manager.getMethod("getCrate", Location.class).invoke(null, scenario.location);
	}

	private static void assertExactContents(Scenario scenario, Barrel barrel) {
		ItemStack[] current = barrel.getInventory().getContents();
		require(Arrays.equals(scenario.contents, current),
				"exact cloned slots, stack amounts and metadata must survive");
		require(Arrays.deepEquals(scenario.serializedContents, serializeContents(current)),
				"exact per-slot item serialization must survive");
	}

	private static void assertPremiumContents(ItemStack[] contents) {
		List<Material> materials = List.of(Material.IRON_HELMET, Material.IRON_CHESTPLATE,
				Material.IRON_LEGGINGS, Material.IRON_BOOTS, Material.BREAD);
		require(contents.length == 27, "barrel must have 27 slots");
		for (int slot = 0; slot < contents.length; slot++) {
			ItemStack item = contents[slot];
			if (slot < materials.size()) {
				require(item != null && item.getType() == materials.get(slot)
						&& item.getAmount() == (slot == 4 ? 2 : 1), "unexpected initial premium slot " + slot);
			} else {
				require(item == null || item.getType().isAir(), "unexpected extra premium item at " + slot);
			}
		}
	}

	private static ItemStack[] cloneContents(ItemStack[] items) {
		return Arrays.stream(items).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
	}

	private static byte[][] serializeContents(ItemStack[] items) {
		return Arrays.stream(items).map(item -> item == null ? null : item.serializeAsBytes()).toArray(byte[][]::new);
	}

	private static Map<String, BukkitTask> tasks(Object crate) throws ReflectiveOperationException {
		Map<String, BukkitTask> tasks = new LinkedHashMap<>();
		for (String name : TASK_FIELDS) {
			if (field(crate, name) instanceof BukkitTask task) {
				tasks.put(name, task);
			}
		}
		return Map.copyOf(tasks);
	}

	private static Object field(Object target, String name) throws ReflectiveOperationException {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static Object staticField(Class<?> type, String name) throws ReflectiveOperationException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(null);
	}

	private static Object value(Object target, String method) throws ReflectiveOperationException {
		return target.getClass().getMethod(method).invoke(target);
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException("AIRDR-87: " + message);
		}
	}

	private static final class Scenario {
		private final Location location;
		private final boolean expiry;
		private final Plugin plugin;
		private final Class<?> manager;
		private final Object admission;
		private FileConfiguration config;
		private Object previousLifetime;
		private Object previousContinuousEffects;
		private Object original;
		private Object recovered;
		private Object lease;
		private Object admissionSnapshot;
		private Object persistence;
		private ItemStack[] contents;
		private byte[][] serializedContents;
		private String crateId;
		private UUID requestId;
		private long deadline;
		private Map<String, BukkitTask> originalTasks = Map.of();
		private Map<String, BukkitTask> recoveredTasks = Map.of();

		private Scenario(Location location, boolean expiry) throws ReflectiveOperationException {
			this.location = location;
			this.expiry = expiry;
			plugin = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("Airdrop"), "Airdrop");
			manager = plugin.getClass().getClassLoader().loadClass("com.airdropmc.helpers.CrateManager");
			admission = plugin.getClass().getMethod("getDropAdmissionController").invoke(null);
		}
	}
}
