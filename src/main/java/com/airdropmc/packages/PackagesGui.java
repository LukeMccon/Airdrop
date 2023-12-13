package com.airdropmc.packages;

import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.Airdrop;

import com.airdropmc.exceptions.PackageNotFoundException;
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

import java.util.List;
import java.util.Set;

/**
 * GUI that shows available packages within airdrop
 */
public class PackagesGui extends Gui implements Listener {
    private final Inventory inv;

    public PackagesGui() {

        int inventorySize = 27;

        // Create a new inventory, with no owner (as this isn't a real inventory), a size of nine, called example
        inv = Bukkit.createInventory(null, inventorySize, "Packages");

        // Put the items into the inventory
        initializeItems();
    }

    public void initializeItems() {
        Set<String> packages = PackageManager.getPackages();

        List<ItemStack> pkglist = packages.stream().map(this::packageGuiItem).toList();
        pkglist.forEach(inv::addItem);
    }

    /**
     * Creates a ItemStack that represents one of the configured packages
     * @param packageName name of package the ItemStack references
     * @return created ItemStack
     */
    private ItemStack packageGuiItem(String packageName) {
        Package pkg;
        double price;

        try {
           pkg = PackageManager.get(packageName);
           price = pkg.getPrice();
        } catch (PackageNotFoundException e) {
           price = 0.0;
        }
        return createGuiItem(Material.CHEST, packageName,1 , "$" + price);
    }

    public void openInventory(final HumanEntity ent) {
        ent.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent e) throws PackageNotFoundException {

        if (!e.getInventory().equals(inv)) return;

        e.setCancelled(true);

        final ItemStack clickedItem = e.getCurrentItem();

        // verify current item is not null
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        final Player p = (Player) e.getWhoClicked();


        String packageName = null;

        try {
            packageName = e.getCurrentItem().getItemMeta().getDisplayName().toLowerCase();
        } catch (NullPointerException err) {
            ChatHandler.logMessage(err.getMessage());
        }


        Package pkg = null;
        try {
            pkg = PackageManager.get(packageName);
        } catch (PackageNotFoundException error) {
            ChatHandler.sendErrorMessage(p, error.getMessage());
            return;
        }
        PackageGui packageGui = new PackageGui(pkg);
        Bukkit.getPluginManager().registerEvents(packageGui, Airdrop.getPluginInstance());
        packageGui.openInventory(p);
    }

    @EventHandler
    public void onInventoryClick(final InventoryDragEvent e) {
        if (e.getInventory().equals(inv)) {
            e.setCancelled(true);
        }
    }
}
