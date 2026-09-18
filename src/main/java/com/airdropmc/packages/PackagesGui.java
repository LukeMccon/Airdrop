package com.airdropmc.packages;

import com.airdropmc.Airdrop;
import com.airdropmc.exceptions.PackageNotFoundException;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.lang.MessageKey;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI that shows available packages within Airdrop.
 */
public class PackagesGui extends Gui implements Listener {
	private static final int INVENTORY_SIZE = PackageManager.MAX_PACKAGES;

	private final Inventory inv;
	private List<String> packageNamesBySlot = List.of();
	private boolean listenerRegistered;

	public PackagesGui() {
		inv = Bukkit.createInventory(null, INVENTORY_SIZE, ChatHandler.get(MessageKey.GUI_PACKAGES_TITLE));
		initializeItems();
	}

	public void initializeItems() {
		List<Package> packages = PackageManager.getPackagesForDisplay();
		if (packages.size() > inv.getSize()) {
			throw new IllegalStateException(
					"Cannot display " + packages.size() + " packages in " + inv.getSize() + " slots");
		}

		List<String> names = new ArrayList<>(packages.size());
		List<ItemStack> icons = new ArrayList<>(packages.size());
		for (Package pkg : packages) {
			names.add(pkg.getName());
			icons.add(packageGuiItem(pkg));
		}

		packageNamesBySlot = List.of();
		inv.clear();
		for (int slot = 0; slot < icons.size(); slot++) {
			inv.setItem(slot, icons.get(slot));
		}
		packageNamesBySlot = List.copyOf(names);
	}

	public void closeAndUnregister() {
		for (HumanEntity viewer : List.copyOf(inv.getViewers())) {
			if (viewer.getOpenInventory().getTopInventory() == inv) {
				viewer.closeInventory();
			}
		}
		if (listenerRegistered) {
			HandlerList.unregisterAll(this);
			listenerRegistered = false;
		}
	}

	/**
	 * Creates an ItemStack that represents one configured package.
	 *
	 * @param pkg package the ItemStack references
	 * @return created ItemStack
	 */
	private ItemStack packageGuiItem(Package pkg) {
		return createPackageIcon(
				pkg.getName(),
				pkg.getName(),
				ChatHandler.get(
						MessageKey.GUI_PACKAGE_PRICE,
						Map.of("price", String.valueOf(pkg.getPrice()))));
	}

	public void openInventory(final HumanEntity entity) {
		ensureListenerRegistered();
		entity.openInventory(inv);
	}

	@EventHandler
	public void onInventoryClick(final InventoryClickEvent event) {
		if (event.getView().getTopInventory() != inv) {
			return;
		}

		event.setCancelled(true);
		if (!(event.getWhoClicked() instanceof Player player)
				|| event.getClickedInventory() != inv
				|| !PermissionsHelper.isAdmin(player)
				|| event.getClick() != ClickType.LEFT
				|| event.getAction() != InventoryAction.PICKUP_ALL) {
			return;
		}

		ItemStack cursor = event.getCursor();
		if (cursor != null && !cursor.getType().isAir()) {
			return;
		}

		int slot = event.getSlot();
		List<String> displayedNames = packageNamesBySlot;
		if (slot < 0 || slot >= displayedNames.size()) {
			return;
		}

		String expectedPackageName = displayedNames.get(slot);
		String markedPackageName = getPackageIconMarker(event.getCurrentItem());
		if (!expectedPackageName.equals(markedPackageName)) {
			return;
		}

		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin == null || !plugin.isEnabled()) {
			ChatHandler.sendError(player, MessageKey.PACKAGES_CREATE_OPEN_ERROR);
			return;
		}

		UUID viewerId = player.getUniqueId();
		Bukkit.getScheduler().runTask(plugin, () -> openEditor(viewerId, slot, expectedPackageName));
	}

	private void openEditor(UUID viewerId, int slot, String packageName) {
		Player player = Bukkit.getPlayer(viewerId);
		if (player == null || !player.isOnline()) {
			return;
		}
		if (player.getOpenInventory().getTopInventory() != inv || !PermissionsHelper.isAdmin(player)) {
			return;
		}

		List<String> displayedNames = packageNamesBySlot;
		if (slot < 0
				|| slot >= displayedNames.size()
				|| !packageName.equals(displayedNames.get(slot))
				|| !packageName.equals(getPackageIconMarker(inv.getItem(slot)))) {
			return;
		}

		try {
			PackageGui editor = new PackageGui(PackageManager.get(packageName));
			if (!editor.openInventory(player)) {
				ChatHandler.sendError(player, MessageKey.PACKAGES_CREATE_OPEN_ERROR);
			}
		} catch (PackageNotFoundException error) {
			ChatHandler.sendError(player, MessageKey.ERROR_PACKAGE_NOT_FOUND,
					Map.of("name", error.getPackageName()));
		}
	}

	@EventHandler
	public void onInventoryClick(final InventoryDragEvent event) {
		if (event.getInventory() == inv) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onInventoryClose(final InventoryCloseEvent event) {
		if (event.getInventory() != inv) {
			return;
		}
		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin == null || !plugin.isEnabled()) {
			unregisterListenerIfIdle();
			return;
		}
		Bukkit.getScheduler().runTask(plugin, this::unregisterListenerIfIdle);
	}

	private void ensureListenerRegistered() {
		if (listenerRegistered) {
			return;
		}
		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin == null || !plugin.isEnabled()) {
			return;
		}
		Bukkit.getPluginManager().registerEvents(this, plugin);
		listenerRegistered = true;
	}

	private void unregisterListenerIfIdle() {
		if (!inv.getViewers().isEmpty()) {
			return;
		}
		if (listenerRegistered) {
			HandlerList.unregisterAll(this);
			listenerRegistered = false;
		}
	}
}
