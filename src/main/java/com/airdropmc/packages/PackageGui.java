package com.airdropmc.packages;

import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.Airdrop;
import com.airdropmc.exceptions.PackageNotFoundException;
import com.airdropmc.helpers.PermissionsHelper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

        inv.setItem(inventorySize - 3, createGuiItem(Material.BLUE_WOOL, "Back", 1));
        inv.setItem(inventorySize - 2, createGuiItem(Material.GREEN_WOOL, "Save", 1));
        inv.setItem(inventorySize - 1 ,createGuiItem(Material.RED_WOOL, "Cancel", 1));

    }

    public void openInventory(final HumanEntity ent) {
        ent.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();

        if (!e.getInventory().equals(inv)) return;

        final ItemStack clickedItem = e.getCurrentItem();

        if (clickedItem == null || clickedItem.getType().isAir()) return;

        String itemStackName = "";

        try {
            itemStackName = Objects.requireNonNull(clickedItem.getItemMeta()).getDisplayName();
        } catch (NullPointerException err) {
            ChatHandler.logMessage(err.getMessage());
        }

        switch(itemStackName){

            case "Back":
                this.back(e);
                break;

            case "Save":
                if (Boolean.TRUE.equals(PermissionsHelper.isAdmin(p))) {
                    this.save(e);
                } else {
                    ChatHandler.sendErrorMessage(p,"Must be admin to save edits to a package a package ");
                    e.setCancelled(true);
                }
                break;

            case "Cancel":
                this.cancel(e);
                break;

            default:
                if (Boolean.FALSE.equals(PermissionsHelper.isAdmin(p))) {
                    e.setCancelled(true);
                }
        }
    }

    /**
     * Cancel actions that are not done by an admin
     * @param e inventory interaction
     */
    @EventHandler
    public void onInventoryClick(final InventoryDragEvent e) {
        Player p = (Player) e.getWhoClicked();
        if (e.getInventory().equals(inv) && !PermissionsHelper.isAdmin(p)) {
            e.setCancelled(true);
        }
    }

    public String getName() { return this.name; }

    public void save(final InventoryClickEvent e) {

        Player p = (Player) e.getWhoClicked();

        ItemStack[] newPackageItems = e.getInventory().getContents();
        try {
            PackageManager.updatePackageInventory(this.getName(), new ArrayList<>(Arrays.asList(newPackageItems)));
        } catch (PackageNotFoundException error) {
            ChatHandler.sendErrorMessage(p, error.getMessage());
        }

        p.closeInventory();
        ChatHandler.sendMessage(p, "Package " + ChatColor.AQUA + this.getName() + ChatColor.BLUE + " was saved successfully");
    }

    public void cancel(final InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        p.closeInventory();
        ChatHandler.sendMessage(p,"Package edit was canceled");
    }

    /**
     * Go back to the packages inventory (showing all packages)
     * @param e click event
     */
    public void back(final InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        p.closeInventory();
        Airdrop.getPackagesGui().openInventory(p);
    }

    /**
     * Determines if the given ItemStack is a control stack (is not an item in the package)
     * @param itemstack to check
     * @return is the ItemStack used to control the plugin
     */
    public static Boolean isControlItemStack(ItemStack itemstack) {
        String itemName = "";
        try {
            itemName = Objects.requireNonNull(itemstack.getItemMeta()).getDisplayName();
        } catch (NullPointerException err) {
            ChatHandler.logMessage(err.getMessage());
        }
        return Arrays.asList(controlItemNames).contains(itemName);
    }

}
