package com.airdropmc.packages;

import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.exceptions.DuplicatePackageException;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class CreatePackageGui extends Gui implements Listener {
    private final Inventory inv;
    private final String name;
    private final double price;
    private final PlayerInventorySnapshot inventorySnapshot = new PlayerInventorySnapshot();
    private UUID viewerId;

	public CreatePackageGui(String name, double price) {

		this.name = name;
		this.price = price;

        int inventorySize = 36;

        inv = Bukkit.createInventory(null, inventorySize, name);

        initializeItems();
    }

    /**
     * Setup control item blocks
     */
    public void initializeItems() {
        int inventorySize = inv.getSize();

        // Add a save an cancel ItemStack to the package
        inv.setItem(inventorySize - 3, createGuiItem(
                Material.BOOK,
                ChatHandler.get(MessageKey.GUI_HELP),
                1,
                ChatHandler.get(MessageKey.GUI_EDITOR_HELP_ADD_STACK),
                ChatHandler.get(MessageKey.GUI_EDITOR_HELP_ADD_ONE),
                ChatHandler.get(MessageKey.GUI_EDITOR_HELP_REMOVE_STACK),
                ChatHandler.get(MessageKey.GUI_EDITOR_HELP_REMOVE_ONE)));
        inv.setItem(inventorySize - 2, createGuiItem(Material.GREEN_WOOL, ChatHandler.get(MessageKey.GUI_SAVE), 1));
        inv.setItem(inventorySize - 1, createGuiItem(Material.RED_WOOL, ChatHandler.get(MessageKey.GUI_CANCEL), 1));
    }

    public void openInventory(final HumanEntity ent) {
        if (ent instanceof Player p) {
            this.viewerId = p.getUniqueId();
            this.inventorySnapshot.capture(p);
        }
        ent.openInventory(inv);
    }

	@EventHandler
	public void onInventoryClick(final InventoryClickEvent e) {
		InventoryView view = e.getView();
		if (!view.getTopInventory().equals(inv)) {
			return;
		}
		if (!(e.getWhoClicked() instanceof Player p)) {
			return;
		}

        e.setCancelled(true);

		Inventory clickedInventory = e.getClickedInventory();
		if (clickedInventory == null) {
			return;
		}

		final ItemStack clickedItem = e.getCurrentItem();

        if (clickedItem == null || clickedItem.getType().isAir()) {
            return;
        }

        String itemStackName = getDisplayName(clickedItem);

        String saveLabel = ChatHandler.get(MessageKey.GUI_SAVE);
        String cancelLabel = ChatHandler.get(MessageKey.GUI_CANCEL);
        String helpLabel = ChatHandler.get(MessageKey.GUI_HELP);

        if (clickedInventory.equals(inv) && Objects.equals(itemStackName, saveLabel)) {
            if (PermissionsHelper.isAdmin(p)) {
                this.save(e);
            } else {
                ChatHandler.sendError(p, MessageKey.ADMIN_PACKAGE_SAVE_REQUIRED);
            }
            return;
        }

        if (clickedInventory.equals(inv) && Objects.equals(itemStackName, cancelLabel)) {
            this.cancel(e);
            return;
        }

        if (clickedInventory.equals(inv) && Objects.equals(itemStackName, helpLabel)) {
            return;
        }

        if (!PermissionsHelper.isAdmin(p)) {
            return;
        }

        if (clickedInventory.equals(inv)) {
            if (isEditablePackageSlot(e.getSlot())) {
                if (e.isRightClick() && clickedItem.getAmount() > 1) {
                    ItemStack updated = clickedItem.clone();
                    updated.setAmount(clickedItem.getAmount() - 1);
                    inv.setItem(e.getSlot(), updated);
                } else {
                    inv.setItem(e.getSlot(), null);
                }
            }
            return;
        }

        if (clickedInventory.equals(p.getInventory())) {
            addItemToPackage(p, clickedItem, e.isRightClick());
        }
    }

    /**
     * Handle when a player drags an item
     * 
     * @param e drag event
     */
	@EventHandler
	public void onInventoryClick(final InventoryDragEvent e) {
		if (e.getInventory().equals(inv)) {
			e.setCancelled(true);
		}
	}

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent e) {
        if (e.getInventory().equals(inv) && e.getPlayer() instanceof Player p) {
            inventorySnapshot.restore(p);
            HandlerList.unregisterAll(this);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        if (viewerId != null && viewerId.equals(e.getPlayer().getUniqueId())) {
            inventorySnapshot.restore(e.getPlayer());
            HandlerList.unregisterAll(this);
        }
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent e) {
        if (viewerId != null && viewerId.equals(e.getPlayer().getUniqueId())) {
            inventorySnapshot.restore(e.getPlayer());
            HandlerList.unregisterAll(this);
        }
    }

    public String getName() {
        return this.name;
    }

    /**
     * When the save control ItemStack is clicked, create the package
     * 
     * @param e event from clicking save
     */
    public void save(final InventoryClickEvent e) {

        Player p = (Player) e.getWhoClicked();

        ItemStack[] newPackageItems = inv.getContents();
        List<ItemStack> packageItems = PackageManager.sanitizePackageItems(new ArrayList<>(Arrays.asList(newPackageItems)));
        if (packageItems.size() > PackageManager.MAX_PACKAGE_ITEM_STACKS) {
            ChatHandler.sendError(p, MessageKey.PACKAGES_ITEM_LIMIT,
                    Map.of("max", String.valueOf(PackageManager.MAX_PACKAGE_ITEM_STACKS)));
            e.setCancelled(true);
            return;
        }

        inventorySnapshot.restore(p);
        p.closeInventory();

        Package pkg = new Package(this.name, this.price, packageItems);
        try {
            PackageManager.createPackage(pkg);
        } catch (DuplicatePackageException error) {
            ChatHandler.sendError(p, MessageKey.ERROR_PACKAGE_EXISTS,
                    Map.of("name", error.getPackageName()));
            return;
        }

        ChatHandler.send(p, MessageKey.PACKAGES_CREATED, Map.of("name", this.getName()));
    }

    /**
     * When the cancel control ItemStack is clicked, create the package
     * 
     * @param e event from clicking cancel
     */
    public void cancel(final InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        inventorySnapshot.restore(p);
        p.closeInventory();
        ChatHandler.send(p, MessageKey.PACKAGES_CREATE_CANCELED);
    }

    private int getFirstControlSlot() {
        return inv.getSize() - 3;
    }

    private boolean isEditablePackageSlot(int slot) {
        return slot >= 0 && slot < getFirstControlSlot();
    }

    private void addItemToPackage(Player player, ItemStack itemToCopy, boolean singleItem) {
        int requestedAmount = singleItem ? 1 : itemToCopy.getAmount();
        int remaining = requestedAmount;

        for (int slot = 0; slot < getFirstControlSlot() && remaining > 0; slot++) {
            ItemStack existing = inv.getItem(slot);
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(itemToCopy)) {
                continue;
            }

            int space = existing.getMaxStackSize() - existing.getAmount();
            if (space <= 0) {
                continue;
            }

            int toAdd = Math.min(space, remaining);
            existing.setAmount(existing.getAmount() + toAdd);
            inv.setItem(slot, existing);
            remaining -= toAdd;
        }

        while (remaining > 0) {
            int targetSlot = findFirstEmptyEditableSlot();
            if (targetSlot == -1) {
                ChatHandler.sendError(player, MessageKey.PACKAGES_ITEM_LIMIT,
                        Map.of("max", String.valueOf(PackageManager.MAX_PACKAGE_ITEM_STACKS)));
                return;
            }

            int stackAmount = Math.min(itemToCopy.getMaxStackSize(), remaining);
            ItemStack toInsert = itemToCopy.clone();
            toInsert.setAmount(stackAmount);
            inv.setItem(targetSlot, toInsert);
            remaining -= stackAmount;
        }
    }

    private int findFirstEmptyEditableSlot() {
        for (int slot = 0; slot < getFirstControlSlot(); slot++) {
            ItemStack existing = inv.getItem(slot);
            if (existing == null || existing.getType().isAir()) {
                return slot;
            }
        }
        return -1;
    }

}
