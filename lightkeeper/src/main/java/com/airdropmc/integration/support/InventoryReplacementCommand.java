package com.airdropmc.integration.support;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Exercises Paper's location-based inventory holder resolution without depending on the plugin JAR. */
public final class InventoryReplacementCommand implements CommandExecutor {
	private static final String SNAPSHOT_METHOD = "snapshot";
	private static final String GET_OPENED_METHOD = "getOpened";

	private final Map<UUID, CapturedView> capturedViews = new HashMap<>();

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length != 7 && args.length != 3) {
			return false;
		}
		Player player = Objects.requireNonNull(Bukkit.getPlayerExact(args[1]), "test player");
		try {
			if (args.length == 7 && args[0].equals("capture")) {
				World world = Objects.requireNonNull(Bukkit.getWorld(args[2]), "test world");
				Block block = world.getBlockAt(
						Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]));
				capture(player, block);
				sender.sendMessage(args[6]);
				return true;
			}
			if (args.length == 3 && args[0].equals("verify")) {
				verifyReplacementAndNormalClose(player);
				sender.sendMessage(args[2]);
				return true;
			}
			return false;
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("AIRDR-49 could not inspect the running Airdrop plugin", failure);
		}
	}

	private void capture(Player player, Block block) {
		require(block.getType() == Material.AIR, "capture location must initially be air");
		block.setType(Material.BARREL, false);
		Inventory inventory = ((Barrel) block.getState()).getInventory();
		InventoryView view = player.openInventory(inventory);
		require(view != null && view.getTopInventory().equals(inventory), "ordinary barrel must open");
		capturedViews.put(player.getUniqueId(), new CapturedView(block.getLocation(), view));
		player.closeInventory();
		block.setType(Material.AIR, false);
	}

	private void verifyReplacementAndNormalClose(Player player) throws ReflectiveOperationException {
		CapturedView captured = Objects.requireNonNull(
				capturedViews.remove(player.getUniqueId()), "captured ordinary barrel view");
		Location location = captured.location();
		Block block = location.getBlock();
		require(block.getState() instanceof Barrel, "tracked replacement barrel must exist");
		Barrel replacement = (Barrel) block.getState();
		Inventory currentInventory = replacement.getInventory();
		Inventory staleInventory = captured.view().getTopInventory();
		require(staleInventory.getHolder() instanceof Barrel,
				"Paper must resolve the stale inventory holder to the replacement barrel");
		Barrel staleHolder = (Barrel) staleInventory.getHolder();
		require(staleHolder.getInventory().equals(currentInventory),
				"stale holder must resolve the replacement inventory");
		require(!staleInventory.equals(currentInventory),
				"stale and replacement inventories must have different backing containers");

		Plugin airdrop = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("Airdrop"), "Airdrop");
		Class<?> manager = airdrop.getClass().getClassLoader().loadClass("com.airdropmc.helpers.CrateManager");
		Object crate = Objects.requireNonNull(crateAt(manager, location), "tracked replacement crate");
		require(Boolean.TRUE.equals(crate.getClass().getMethod("ownsLandedBarrel", Barrel.class)
				.invoke(crate, staleHolder)), "stale holder must carry the replacement's valid identity");
		Object admission = airdrop.getClass().getMethod("getDropAdmissionController").invoke(null);
		Object before = publicValue(admission, SNAPSHOT_METHOD);
		require(publicValue(before, "landedClaims").equals(1), "one landed lease must be active");
		require(publicValue(before, "locations").equals(1), "one landing location must be reserved");
		require(Boolean.FALSE.equals(publicValue(crate, GET_OPENED_METHOD)), "replacement must be unopened");
		Map<String, BukkitTask> tasks = activeTasks(crate);
		require(tasks.containsKey("expiryTask"), "replacement expiry task must be active");

		currentInventory.clear();
		for (int attempt = 0; attempt < 2; attempt++) {
			Bukkit.getPluginManager().callEvent(new InventoryCloseEvent(captured.view()));
			assertReplacementSurvives(manager, location, crate, currentInventory, admission, before, tasks);
		}
		Bukkit.getPluginManager().callEvent(new InventoryOpenEvent(captured.view()));
		require(Boolean.FALSE.equals(publicValue(crate, GET_OPENED_METHOD)),
				"stale inventory open must not mark the replacement opened");
		assertReplacementSurvives(manager, location, crate, currentInventory, admission, before, tasks);

		InventoryView currentView = player.openInventory(currentInventory);
		require(currentView != null && currentView.getTopInventory().equals(currentInventory),
				"current tracked barrel must open normally");
		require(Boolean.TRUE.equals(publicValue(crate, GET_OPENED_METHOD)),
				"normal open must mark the tracked crate opened");
		player.closeInventory();
		require(block.getType() == Material.AIR, "normal empty close must remove the owned barrel");
		require(crateAt(manager, location) == null, "normal empty close must remove tracking");
		Object after = publicValue(admission, SNAPSHOT_METHOD);
		require(publicValue(after, "landedClaims").equals(0), "normal close must release the landed lease");
		require(publicValue(after, "locations").equals(0), "normal close must release the location");
		for (Map.Entry<String, BukkitTask> entry : tasks.entrySet()) {
			require(entry.getValue().isCancelled(), "normal close must cancel " + entry.getKey());
		}
	}

	private static void assertReplacementSurvives(
			Class<?> manager, Location location, Object crate, Inventory inventory,
			Object admission, Object before, Map<String, BukkitTask> tasks
	) throws ReflectiveOperationException {
		require(location.getBlock().getType() == Material.BARREL, "stale event removed the replacement barrel");
		require(crateAt(manager, location) == crate, "stale event removed replacement tracking");
		require(((Barrel) location.getBlock().getState()).getInventory().equals(inventory),
				"stale event replaced the current inventory");
		require(inventory.isEmpty(), "stale event changed replacement contents");
		require(before.equals(publicValue(admission, SNAPSHOT_METHOD)), "stale event released the replacement lease");
		for (Map.Entry<String, BukkitTask> entry : tasks.entrySet()) {
			require(field(crate, entry.getKey()) == entry.getValue() && !entry.getValue().isCancelled(),
					"stale event cancelled or replaced " + entry.getKey());
		}
	}

	private static Map<String, BukkitTask> activeTasks(Object crate) throws ReflectiveOperationException {
		Map<String, BukkitTask> tasks = new LinkedHashMap<>();
		for (String name : List.of("expiryTask", "landingEffectTask", "glowTask", "smokeTask")) {
			Object value = field(crate, name);
			// Completed one-shot tasks can remain uncancelled after leaving Paper's scheduler.
			if (value instanceof BukkitTask task && Bukkit.getScheduler().isQueued(task.getTaskId())) {
				tasks.put(name, task);
			}
		}
		return tasks;
	}

	private static Object field(Object target, String name) throws ReflectiveOperationException {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static Object publicValue(Object target, String method) throws ReflectiveOperationException {
		return target.getClass().getMethod(method).invoke(target);
	}

	private static Object crateAt(Class<?> manager, Location location) throws ReflectiveOperationException {
		return manager.getMethod("getCrate", Location.class).invoke(null, location);
	}

	private static void require(boolean accepted, String message) {
		if (!accepted) {
			throw new IllegalStateException("AIRDR-49: " + message);
		}
	}

	private record CapturedView(Location location, InventoryView view) {
	}
}
