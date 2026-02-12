package com.airdropmc.packages;

import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.Airdrop;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        inv.setItem(inventorySize - 2, createGuiItem(Material.GREEN_WOOL, ChatHandler.get(MessageKey.GUI_SAVE), 1));
        inv.setItem(inventorySize - 1, createGuiItem(Material.RED_WOOL, ChatHandler.get(MessageKey.GUI_CANCEL), 1));
    }

    public void openInventory(final HumanEntity ent) {
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

        String saveLabel = ChatHandler.get(MessageKey.GUI_SAVE);
        String cancelLabel = ChatHandler.get(MessageKey.GUI_CANCEL);

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
     * Handle when a player drags an item
     * 
     * @param e drag event
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

        ItemStack[] newPackageItems = e.getInventory().getContents();
        List<ItemStack> packageItems = PackageManager.sanitizePackageItems(new ArrayList<>(Arrays.asList(newPackageItems)));
        if (packageItems.size() > PackageManager.MAX_PACKAGE_ITEM_STACKS) {
            ChatHandler.sendError(p, MessageKey.PACKAGES_ITEM_LIMIT,
                    Map.of("max", String.valueOf(PackageManager.MAX_PACKAGE_ITEM_STACKS)));
            e.setCancelled(true);
            return;
        }

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
        p.closeInventory();
        ChatHandler.send(p, MessageKey.PACKAGES_CREATE_CANCELED);
    }

}
