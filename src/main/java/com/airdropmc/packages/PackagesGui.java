package com.airdropmc.packages;

import com.airdropmc.Airdrop;
import com.airdropmc.exceptions.PackageNotFoundException;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.lang.MessageKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared package catalog with a separate permission-filtered inventory for each viewer. */
public class PackagesGui extends Gui implements Listener {
	private final Map<Inventory, BrowserView> views = new IdentityHashMap<>();
	private Airdrop registeredPlugin;
	private BukkitTask refreshTask;
	private boolean retired;

	private static final class BrowserView {
		private final UUID viewerId;
		private final Inventory inventory;
		private List<Package> packages = List.of();
		private long generation;
		private boolean transitioning;

		private BrowserView(Player player, Inventory inventory) {
			this.viewerId = player.getUniqueId();
			this.inventory = inventory;
		}
	}

	public PackagesGui() {
	}

	/** Refreshes every open catalog and invalidates any queued navigation. */
	public void initializeItems() {
		List<Package> packages = PackageManager.getPackagesForDisplay();
		for (BrowserView view : List.copyOf(views.values())) {
			Player player = Bukkit.getPlayer(view.viewerId);
			if (isViewing(player, view)) {
				render(view, permittedPackages(player, packages));
			}
		}
	}

	public void closeAndUnregister() {
		if (retired) {
			return;
		}
		retired = true;
		for (BrowserView view : List.copyOf(views.values())) {
			Player player = Bukkit.getPlayer(view.viewerId);
			if (isViewing(player, view)) {
				player.closeInventory();
			}
		}
		views.clear();
		unregisterIfIdle();
	}

	public void openInventory(final HumanEntity entity) {
		if (retired || !(entity instanceof Player player)) {
			return;
		}
		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin == null || !plugin.isEnabled() || !Airdrop.isReady()) {
			ChatHandler.sendError(player, MessageKey.ERROR_PLUGIN_NOT_READY);
			return;
		}

		Inventory inventory = Bukkit.createInventory(null, PackageManager.MAX_PACKAGES,
				ChatHandler.get(MessageKey.GUI_PACKAGES_TITLE));
		BrowserView view = new BrowserView(player, inventory);
		render(view, permittedPackages(player, PackageManager.getPackagesForDisplay()));
		if (registeredPlugin == null) {
			Bukkit.getPluginManager().registerEvents(this, plugin);
			registeredPlugin = plugin;
		}
		views.put(inventory, view);
		try {
			InventoryView opened = player.openInventory(inventory);
			if (opened == null || opened.getTopInventory() != inventory || !isViewing(player, view)
					|| retired || views.get(inventory) != view
					|| plugin != Airdrop.getPluginInstance() || !plugin.isEnabled() || !Airdrop.isReady()) {
				discardView(player, view);
				return;
			}
			List<Package> current = permittedPackages(player, PackageManager.getPackagesForDisplay());
			if (!view.packages.equals(current)) {
				render(view, current);
			}
			if (refreshTask == null) {
				refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOpenViews, 1L, 1L);
			}
		} catch (RuntimeException failure) {
			discardView(player, view);
			throw failure;
		}
	}

	private void discardView(Player player, BrowserView view) {
		if (isViewing(player, view)) {
			player.closeInventory();
		}
		views.remove(view.inventory);
		unregisterIfIdle();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryClick(final InventoryClickEvent event) {
		BrowserView view = views.get(event.getView().getTopInventory());
		if (view == null) {
			return;
		}
		event.setCancelled(true);
		if (!(event.getWhoClicked() instanceof Player player)
				|| !view.viewerId.equals(player.getUniqueId())
				|| !isViewing(player, view)
				|| event.getClickedInventory() != view.inventory
				|| view.transitioning
				|| event.getClick() != ClickType.LEFT
				|| event.getAction() != InventoryAction.PICKUP_ALL
				|| hasCursorItem(event.getCursor())) {
			return;
		}

		int slot = event.getSlot();
		if (slot < 0 || slot >= view.packages.size()) {
			return;
		}
		Package expected = view.packages.get(slot);
		if (!expected.getName().equals(getPackageIconMarker(event.getCurrentItem()))
				|| !PermissionsHelper.hasPermission(player, expected.getName())) {
			return;
		}
		Airdrop plugin = registeredPlugin;
		if (plugin == null || plugin != Airdrop.getPluginInstance() || !plugin.isEnabled() || !Airdrop.isReady()) {
			return;
		}
		long generation = view.generation;
		view.transitioning = true;
		Bukkit.getScheduler().runTask(plugin, () -> openPackage(view, slot, expected, generation, plugin));
	}

	private void openPackage(BrowserView view, int slot, Package expected, long generation, Airdrop plugin) {
		Player player = Bukkit.getPlayer(view.viewerId);
		if (views.get(view.inventory) != view || view.generation != generation) {
			return;
		}
		view.transitioning = false;
		if (!isViewing(player, view) || plugin != Airdrop.getPluginInstance()
				|| !plugin.isEnabled() || !Airdrop.isReady() || hasCursorItem(player.getItemOnCursor())
				|| !PermissionsHelper.hasPermission(player, expected.getName())
				|| slot >= view.packages.size() || view.packages.get(slot) != expected
				|| !expected.getName().equals(getPackageIconMarker(view.inventory.getItem(slot)))) {
			return;
		}
		try {
			if (PackageManager.get(expected.getName()) != expected) {
				return;
			}
			if (!new PackageGui(expected).openInventory(player)) {
				ChatHandler.sendError(player, MessageKey.GUI_PACKAGE_OPEN_ERROR);
			}
		} catch (PackageNotFoundException missing) {
			// The periodic refresh removes deleted packages without opening a stale view.
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryClick(final InventoryDragEvent event) {
		if (views.containsKey(event.getView().getTopInventory())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onInventoryClose(final InventoryCloseEvent event) {
		BrowserView view = views.get(event.getInventory());
		if (view != null && view.viewerId.equals(event.getPlayer().getUniqueId())) {
			views.remove(event.getInventory());
			unregisterIfIdle();
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		views.values().removeIf(view -> view.viewerId.equals(event.getPlayer().getUniqueId()));
		unregisterIfIdle();
	}

	private void refreshOpenViews() {
		if (registeredPlugin != Airdrop.getPluginInstance() || !Airdrop.isReady()) {
			closeAndUnregister();
			return;
		}
		List<Package> packages = PackageManager.getPackagesForDisplay();
		for (BrowserView view : List.copyOf(views.values())) {
			Player player = Bukkit.getPlayer(view.viewerId);
			if (!isViewing(player, view)) {
				views.remove(view.inventory);
				continue;
			}
			List<Package> permitted = permittedPackages(player, packages);
			if (!view.packages.equals(permitted)) {
				render(view, permitted);
			}
		}
		unregisterIfIdle();
	}

	private void render(BrowserView view, List<Package> packages) {
		view.generation++;
		view.transitioning = false;
		view.packages = packages;
		view.inventory.clear();
		for (int slot = 0; slot < packages.size(); slot++) {
			Package pkg = packages.get(slot);
			String price = pkg.getPrice() == 0.0 ? ChatHandler.get(MessageKey.GUI_CATALOG_FREE)
					: ChatHandler.get(MessageKey.GUI_PACKAGE_PRICE, Map.of("price", String.valueOf(pkg.getPrice())));
			view.inventory.setItem(slot, createPackageIcon(pkg.getName(), pkg.getName(), price,
					ChatHandler.get(MessageKey.GUI_CATALOG_OPEN), ChatHandler.get(MessageKey.GUI_PACKAGE_AVAILABILITY)));
		}
		if (packages.isEmpty()) {
			view.inventory.setItem(13, createGuiItem(Material.BARRIER,
					ChatHandler.get(MessageKey.GUI_CATALOG_EMPTY), 1));
		}
	}

	private void unregisterIfIdle() {
		if (!views.isEmpty()) {
			return;
		}
		if (refreshTask != null) {
			refreshTask.cancel();
			refreshTask = null;
		}
		if (registeredPlugin != null) {
			HandlerList.unregisterAll(this);
			registeredPlugin = null;
		}
	}

	private static List<Package> permittedPackages(Player player, List<Package> packages) {
		return packages.stream().filter(pkg -> PermissionsHelper.hasPermission(player, pkg.getName())).toList();
	}

	private static boolean isViewing(Player player, BrowserView view) {
		return player != null && player.isOnline() && player.getOpenInventory().getTopInventory() == view.inventory;
	}

	private static boolean hasCursorItem(ItemStack cursor) {
		return cursor != null && !cursor.getType().isAir();
	}
}
