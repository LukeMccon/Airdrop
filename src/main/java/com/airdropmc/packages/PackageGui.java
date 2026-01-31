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
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.Objects;

public class PackageGui extends Gui implements Listener {
    private final Inventory inv;
    private final Package pkg;
    private final String name;

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
        ent.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();

        if (!e.getInventory().equals(inv))
            return;

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
        Player p = (Player) e.getWhoClicked();
        if (e.getInventory().equals(inv) && !PermissionsHelper.isAdmin(p)) {
            e.setCancelled(true);
        }
    }

    public String getName() {
        return this.name;
    }

    public void save(final InventoryClickEvent e) {

        Player p = (Player) e.getWhoClicked();

        ItemStack[] newPackageItems = e.getInventory().getContents();
        try {
            PackageManager.updatePackageInventory(this.getName(), new ArrayList<>(Arrays.asList(newPackageItems)));
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
