package com.airdropmc.packages;

import com.airdropmc.Airdrop;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.lang.MessageKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public abstract class PackageEditorGui extends Gui implements Listener {
	private static final int INVENTORY_SIZE = 36;

	private final Inventory inventory;
	private final String name;
	private final PackageEditorSession session;
	private final boolean supportsBackControl;

	protected PackageEditorGui(String name, boolean supportsBackControl) {
		this.name = name;
		this.supportsBackControl = supportsBackControl;
		this.inventory = Bukkit.createInventory(null, INVENTORY_SIZE, name);
		this.session = new PackageEditorSession(inventory);
	}

	protected void initializeEditorItems(List<ItemStack> initialItems) {
		initialItems.forEach(inventory::addItem);

		int inventorySize = inventory.getSize();
		int helpSlot = inventorySize - (supportsBackControl ? 4 : 3);
		inventory.setItem(helpSlot, createGuiItem(
				Material.BOOK,
				ChatHandler.get(MessageKey.GUI_HELP),
				1,
				ChatHandler.get(MessageKey.GUI_EDITOR_HELP_ADD_STACK),
				ChatHandler.get(MessageKey.GUI_EDITOR_HELP_ADD_ONE),
				ChatHandler.get(MessageKey.GUI_EDITOR_HELP_REMOVE_STACK),
				ChatHandler.get(MessageKey.GUI_EDITOR_HELP_REMOVE_ONE)));
		if (supportsBackControl) {
			inventory.setItem(
					inventorySize - 3,
					createGuiItem(Material.BLUE_WOOL, ChatHandler.get(MessageKey.GUI_BACK), 1));
		}
		inventory.setItem(
				inventorySize - 2,
				createGuiItem(Material.GREEN_WOOL, ChatHandler.get(MessageKey.GUI_SAVE), 1));
		inventory.setItem(
				inventorySize - 1,
				createGuiItem(Material.RED_WOOL, ChatHandler.get(MessageKey.GUI_CANCEL), 1));
	}

	public boolean openInventory(final Player player) {
		if (!session.bind(player)) {
			return false;
		}

		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin == null || !plugin.isEnabled()) {
			return retire();
		}

		try {
			Bukkit.getPluginManager().registerEvents(this, plugin);
			InventoryView view = player.openInventory(inventory);
			if (view == null || view.getTopInventory() != inventory || !session.activate(view.getTopInventory())) {
				return retire();
			}
			return true;
		} catch (RuntimeException error) {
			retire();
			throw error;
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryClick(final InventoryClickEvent event) {
		Inventory top = event.getView().getTopInventory();
		if (!(event.getWhoClicked() instanceof Player player) || !session.protects(player, top)) {
			return;
		}

		event.setCancelled(true);
		if (!session.canProcess(player, top)) {
			return;
		}

		Inventory clickedInventory = event.getClickedInventory();
		if (clickedInventory == null) {
			return;
		}

		boolean controlSlot = clickedInventory == inventory && isControlSlot(event.getSlot());
		PackageEditorInteraction.VirtualAction action = PackageEditorInteraction.classify(
				event.getClick(), event.getAction(), event.getCursor(), controlSlot);
		if (action == PackageEditorInteraction.VirtualAction.DENY) {
			return;
		}

		ItemStack clickedItem = event.getCurrentItem();
		if (clickedItem == null || clickedItem.getType().isAir()) {
			return;
		}

		if (action == PackageEditorInteraction.VirtualAction.CONTROL) {
			handleControl(event, player);
			return;
		}

		if (!PermissionsHelper.isAdmin(player)) {
			return;
		}

		if (clickedInventory == inventory) {
			if (isEditablePackageSlot(event.getSlot())) {
				removeFromPackage(event.getSlot(), clickedItem, action);
			}
			return;
		}

		if (clickedInventory == player.getInventory()) {
			addItemToPackage(
					player,
					clickedItem,
					action == PackageEditorInteraction.VirtualAction.SINGLE_ITEM);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryClick(final InventoryDragEvent event) {
		if (session.protects(event.getWhoClicked(), event.getView().getTopInventory())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onInventoryClose(final InventoryCloseEvent event) {
		if (session.protects(event.getPlayer(), event.getInventory())) {
			retire();
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		if (session.viewerId() != null && session.viewerId().equals(event.getPlayer().getUniqueId())) {
			retire();
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerKick(PlayerKickEvent event) {
		if (session.viewerId() == null || !session.viewerId().equals(event.getPlayer().getUniqueId())
				|| !session.beginExitTransition()) {
			return;
		}
		scheduleKickObservation(event.getPlayer());
	}

	public String getName() {
		return name;
	}

	public void save(final InventoryClickEvent event) {
		Player player = (Player) event.getWhoClicked();
		if (!validateSave(player)) {
			return;
		}

		List<ItemStack> packageItems = PackageManager.sanitizePackageItems(editableItems());
		if (packageItems.size() > PackageManager.MAX_PACKAGE_ITEM_STACKS) {
			ChatHandler.sendError(player, MessageKey.PACKAGES_ITEM_LIMIT,
					Map.of("max", String.valueOf(PackageManager.MAX_PACKAGE_ITEM_STACKS)));
			event.setCancelled(true);
			return;
		}

		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin == null || !Airdrop.isReady()) {
			ChatHandler.sendError(player, MessageKey.ERROR_PLUGIN_NOT_READY);
			return;
		}
		if (!session.beginSave()) {
			return;
		}

		try {
			CompletionStage<Boolean> save = persist(plugin, cloneItems(packageItems));
			if (save == null) {
				handleSaveCompletion(player, false, null);
				return;
			}
			save.whenComplete((committed, failure) -> handleSaveCompletion(player, committed, failure));
		} catch (RuntimeException failure) {
			handleSaveCompletion(player, false, failure);
		}
	}

	public void cancel(final InventoryClickEvent event) {
		Player player = (Player) event.getWhoClicked();
		scheduleTransition(player, player::closeInventory);
		ChatHandler.send(player, cancelMessage());
	}

	protected boolean validateSave(Player player) {
		return true;
	}

	protected abstract CompletionStage<Boolean> persist(Airdrop plugin, List<ItemStack> items);

	protected abstract MessageKey saveSuccessMessage();

	protected abstract MessageKey cancelMessage();

	protected boolean handleSpecificSaveFailure(Player player, Throwable failure) {
		return false;
	}

	protected void navigateBack(InventoryClickEvent event) {
		// Create editors do not have a back control.
	}

	protected void scheduleTransition(Player player, Runnable viewChange) {
		if (!session.beginTransition()) {
			return;
		}
		scheduleTransitionTask(player, viewChange);
	}

	private void handleControl(InventoryClickEvent event, Player player) {
		int slot = event.getSlot();
		if (supportsBackControl && slot == inventory.getSize() - 3) {
			navigateBack(event);
		} else if (slot == inventory.getSize() - 2) {
			if (PermissionsHelper.isAdmin(player)) {
				save(event);
			} else {
				ChatHandler.sendError(player, MessageKey.ADMIN_PACKAGE_SAVE_REQUIRED);
			}
		} else if (slot == inventory.getSize() - 1) {
			cancel(event);
		}
	}

	private void handleSaveCompletion(Player player, Boolean committed, Throwable failure) {
		if (failure == null && Boolean.TRUE.equals(committed)) {
			if (!session.completeSave()) {
				return;
			}
			scheduleTransitionTask(player, player::closeInventory);
			ChatHandler.send(player, saveSuccessMessage(), Map.of("name", getName()));
			return;
		}

		if (!session.failSave()) {
			return;
		}

		Throwable cause = unwrapCompletionException(failure);
		if (handleSpecificSaveFailure(player, cause)) {
			return;
		}
		ChatHandler.sendError(player, MessageKey.ERROR_PACKAGE_SAVE_FAILED);
	}

	private boolean retire() {
		if (!session.retire()) {
			return false;
		}
		HandlerList.unregisterAll(this);
		return false;
	}

	private void scheduleTransitionTask(Player player, Runnable viewChange) {
		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin == null || !plugin.isEnabled()) {
			retire();
			return;
		}

		Bukkit.getScheduler().runTask(plugin, () -> {
			if (session.state() != PackageEditorSession.State.TRANSITIONING) {
				return;
			}
			if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) {
				retire();
				return;
			}

			viewChange.run();
			if (session.state() == PackageEditorSession.State.TRANSITIONING
					&& (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory)) {
				retire();
			}
		});
	}

	private void scheduleKickObservation(Player player) {
		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin == null || !plugin.isEnabled()) {
			retire();
			return;
		}

		Bukkit.getScheduler().runTask(plugin, () -> {
			if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inventory) {
				retire();
			}
		});
	}

	private void removeFromPackage(
			int slot,
			ItemStack clickedItem,
			PackageEditorInteraction.VirtualAction action) {
		if (action == PackageEditorInteraction.VirtualAction.SINGLE_ITEM && clickedItem.getAmount() > 1) {
			ItemStack updated = clickedItem.clone();
			updated.setAmount(clickedItem.getAmount() - 1);
			inventory.setItem(slot, updated);
			return;
		}

		inventory.setItem(slot, null);
	}

	private int getFirstControlSlot() {
		return PackageManager.MAX_PACKAGE_ITEM_STACKS;
	}

	private List<ItemStack> editableItems() {
		List<ItemStack> items = new ArrayList<>(PackageManager.MAX_PACKAGE_ITEM_STACKS);
		for (int slot = 0; slot < PackageManager.MAX_PACKAGE_ITEM_STACKS; slot++) {
			ItemStack item = inventory.getItem(slot);
			if (item != null && !item.getType().isAir()) {
				items.add(item.clone());
			}
		}
		return items;
	}

	private static List<ItemStack> cloneItems(List<ItemStack> items) {
		List<ItemStack> clonedItems = new ArrayList<>(items.size());
		for (ItemStack item : items) {
			clonedItems.add(item.clone());
		}
		return clonedItems;
	}

	private static Throwable unwrapCompletionException(Throwable failure) {
		Throwable cause = failure;
		while (cause instanceof CompletionException && cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause;
	}

	private boolean isEditablePackageSlot(int slot) {
		return slot >= 0 && slot < getFirstControlSlot();
	}

	private boolean isControlSlot(int slot) {
		int controlCount = supportsBackControl ? 4 : 3;
		return slot >= inventory.getSize() - controlCount && slot < inventory.getSize();
	}

	private void addItemToPackage(Player player, ItemStack itemToCopy, boolean singleItem) {
		int requestedAmount = singleItem ? 1 : itemToCopy.getAmount();
		int remaining = requestedAmount;

		for (int slot = 0; slot < getFirstControlSlot() && remaining > 0; slot++) {
			ItemStack existing = inventory.getItem(slot);
			if (existing == null || existing.getType().isAir() || !existing.isSimilar(itemToCopy)) {
				continue;
			}

			int space = existing.getMaxStackSize() - existing.getAmount();
			if (space <= 0) {
				continue;
			}

			int toAdd = Math.min(space, remaining);
			existing.setAmount(existing.getAmount() + toAdd);
			inventory.setItem(slot, existing);
			remaining -= toAdd;
		}

		while (remaining > 0) {
			int targetSlot = findFirstEmptyEditableSlot();
			if (targetSlot == -1) {
				ChatHandler.sendError(player, MessageKey.PACKAGES_ITEM_LIMIT,
						Map.of("max", String.valueOf(getFirstControlSlot())));
				return;
			}

			int stackAmount = Math.min(itemToCopy.getMaxStackSize(), remaining);
			ItemStack toInsert = itemToCopy.clone();
			toInsert.setAmount(stackAmount);
			inventory.setItem(targetSlot, toInsert);
			remaining -= stackAmount;
		}
	}

	private int findFirstEmptyEditableSlot() {
		for (int slot = 0; slot < getFirstControlSlot(); slot++) {
			ItemStack existing = inventory.getItem(slot);
			if (existing == null || existing.getType().isAir()) {
				return slot;
			}
		}
		return -1;
	}
}
