package com.airdropmc.packages;

import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.Airdrop;
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

import java.util.ArrayList;
import java.util.Arrays;

public class CreatePackageGui extends Gui implements Listener {
    private final Inventory inv;
    private final String name;
    private final double price;
    public CreatePackageGui(String name, double price) {

        this.name = name.toLowerCase();
        this.price = price;

        int inventorySize = 36;

        inv = Bukkit.createInventory(null, inventorySize, name);

        initializeItems();

        Bukkit.getPluginManager().registerEvents(this, Airdrop.getPluginInstance());
    }

    /**
     * Setup control item blocks
     */
    public void initializeItems() {
        int inventorySize = inv.getSize();

        // Add a save an cancel ItemStack to the package
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
             itemStackName = clickedItem.getItemMeta().getDisplayName();
        } catch (NullPointerException err) {
            ChatHandler.logMessage(err.getMessage());
        }

        switch(itemStackName){
            case "Save":
                if (PermissionsHelper.isAdmin(p)) {
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
                if (!PermissionsHelper.isAdmin(p)) {
                    e.setCancelled(true);
                }
        }
    }

    /**
     * Handle when a player drags an item
     * @param e drag event
     */
    @EventHandler
    public void onInventoryClick(final InventoryDragEvent e) {
        Player p = (Player) e.getWhoClicked();
        if (e.getInventory().equals(inv) && !PermissionsHelper.isAdmin(p)) {
            e.setCancelled(true);
        }
    }

    public String getName() { return this.name; }

    /**
     * When the save control ItemStack is clicked, create the package
     * @param e event from clicking save
     */
    public void save(final InventoryClickEvent e) {

        Player p = (Player) e.getWhoClicked();

        ItemStack[] newPackageItems = e.getInventory().getContents();
        p.closeInventory();

        Package pkg = new Package(this.name, this.price, new ArrayList<>(Arrays.asList(newPackageItems)));
        PackageManager.createPackage(pkg);

        ChatHandler.sendMessage(p, "Package " + ChatColor.AQUA + this.getName() + ChatColor.BLUE + " was created successfully");
    }

    /**
     * When the cancel control ItemStack is clicked, create the package
     * @param e event from clicking cancel
     */
    public void cancel(final InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        p.closeInventory();
        ChatHandler.sendMessage(p,"Package creation was canceled");
    }

}
