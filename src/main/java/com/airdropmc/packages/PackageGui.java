package com.airdropmc.packages;

import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.Airdrop;
import com.airdropmc.exceptions.PackageNotFoundException;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.lang.MessageKey;
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
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.Objects;

public class PackageGui extends Gui implements Listener {
    private final Inventory inv;
    private final Package pkg;
    private final String name;
    private UUID viewerId;

    public PackageGui(Package pkg) {

        this.pkg = pkg;
        this.name = pkg.getName();

        int inventorySize = 27;

        // Logic to determine how large to make the inventory
        inv = Bukkit.createInventory(null, inventorySize, pkg.getName());

        initializeItems();
    }

    /**
     * Setup control item blocks
     */
    public void initializeItems() {

        List<ItemStack> itemList = pkg.getItems();
        itemList.forEach(inv::addItem);

        int inventorySize = inv.getSize();

        inv.setItem(inventorySize - 3, createGuiItem(Material.BLUE_WOOL, ChatHandler.get(MessageKey.GUI_BACK), 1));
        inv.setItem(inventorySize - 2, createGuiItem(Material.GREEN_WOOL, ChatHandler.get(MessageKey.GUI_SAVE), 1));
        inv.setItem(inventorySize - 1, createGuiItem(Material.RED_WOOL, ChatHandler.get(MessageKey.GUI_CANCEL), 1));

    }

    public void openInventory(final HumanEntity ent) {
        if (ent instanceof Player p) {
            this.viewerId = p.getUniqueId();
        }
        ent.openInventory(inv);
    }

	@EventHandler
	public void onInventoryClick(final InventoryClickEvent e) {
		if (!e.getInventory().equals(inv)) {
			return;
		}
		if (!(e.getWhoClicked() instanceof Player p)) {
			return;
		}

		final ItemStack clickedItem = e.getCurrentItem();

        if (clickedItem == null || clickedItem.getType().isAir())
            return;

        String itemStackName = getDisplayName(clickedItem);

        String backLabel = ChatHandler.get(MessageKey.GUI_BACK);
        String saveLabel = ChatHandler.get(MessageKey.GUI_SAVE);
        String cancelLabel = ChatHandler.get(MessageKey.GUI_CANCEL);

        if (Objects.equals(itemStackName, backLabel)) {
            this.back(e);
            return;
        }

        if (Objects.equals(itemStackName, saveLabel)) {
            if (PermissionsHelper.isAdmin(p)) {
                this.save(e);
            } else {
                ChatHandler.sendError(p, MessageKey.ADMIN_PACKAGE_SAVE_REQUIRED);
                e.setCancelled(true);
            }
            return;
        }

        if (Objects.equals(itemStackName, cancelLabel)) {
            this.cancel(e);
            return;
        }

        if (!PermissionsHelper.isAdmin(p)) {
            e.setCancelled(true);
        }
    }

    /**
     * Cancel actions that are not done by an admin
     * 
     * @param e inventory interaction
     */
	@EventHandler
	public void onInventoryClick(final InventoryDragEvent e) {
		if (!e.getInventory().equals(inv)) {
			return;
		}
		if (!(e.getWhoClicked() instanceof Player p)) {
			return;
		}
		if (!PermissionsHelper.isAdmin(p)) {
			e.setCancelled(true);
		}
	}

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent e) {
        if (e.getInventory().equals(inv)) {
            HandlerList.unregisterAll(this);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        if (viewerId != null && viewerId.equals(e.getPlayer().getUniqueId())) {
            HandlerList.unregisterAll(this);
        }
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent e) {
        if (viewerId != null && viewerId.equals(e.getPlayer().getUniqueId())) {
            HandlerList.unregisterAll(this);
        }
    }

    public String getName() {
        return this.name;
    }

    public void save(final InventoryClickEvent e) {

        Player p = (Player) e.getWhoClicked();

        ItemStack[] newPackageItems = e.getInventory().getContents();
        List<ItemStack> packageItems = PackageManager.sanitizePackageItems(new ArrayList<>(Arrays.asList(newPackageItems)));
        if (packageItems.size() > PackageManager.MAX_PACKAGE_ITEM_STACKS) {
            ChatHandler.sendError(p, MessageKey.PACKAGES_ITEM_LIMIT,
                    Map.of("max", String.valueOf(PackageManager.MAX_PACKAGE_ITEM_STACKS)));
            e.setCancelled(true);
            return;
        }

        try {
            PackageManager.updatePackageInventory(this.getName(), packageItems);
        } catch (PackageNotFoundException error) {
            ChatHandler.sendError(p, MessageKey.ERROR_PACKAGE_NOT_FOUND,
                    Map.of("name", error.getPackageName()));
            return;
        }

        p.closeInventory();
        ChatHandler.send(p, MessageKey.PACKAGES_SAVED, Map.of("name", this.getName()));
    }

    public void cancel(final InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        p.closeInventory();
        ChatHandler.send(p, MessageKey.PACKAGES_EDIT_CANCELED);
    }

    /**
     * Go back to the packages inventory (showing all packages)
     * 
     * @param e click event
     */
    public void back(final InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        p.closeInventory();
        Airdrop.getPackagesGui().openInventory(p);
    }

    /**
     * Determines if the given ItemStack is a control stack (is not an item in the
     * package)
     * 
     * @param itemstack to check
     * @return is the ItemStack used to control the plugin
     */
    public static boolean isControlItemStack(ItemStack itemstack) {
        return isControlItem(itemstack);
    }

}
