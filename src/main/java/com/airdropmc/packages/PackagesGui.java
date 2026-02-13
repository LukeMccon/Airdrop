package com.airdropmc.packages;

import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.AirdropLogger;
import com.airdropmc.Airdrop;
import com.airdropmc.lang.MessageKey;

import com.airdropmc.exceptions.PackageNotFoundException;
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

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * GUI that shows available packages within airdrop
 */
public class PackagesGui extends Gui implements Listener {
    private final Inventory inv;
    private boolean listenerRegistered = false;

    public PackagesGui() {

        int inventorySize = 27;

        // Create a new inventory, with no owner (as this isn't a real inventory), a
        // size of nine, called example
        inv = Bukkit.createInventory(null, inventorySize, ChatHandler.get(MessageKey.GUI_PACKAGES_TITLE));

        // Put the items into the inventory
        initializeItems();
    }

	public void initializeItems() {
		Set<String> packages = PackageManager.getPackages();
		inv.clear();

		List<ItemStack> pkglist = packages.stream()
				.map(this::packageGuiItem)
				.filter(Objects::nonNull)
				.toList();
		pkglist.forEach(inv::addItem);
	}

    /**
     * Creates a ItemStack that represents one of the configured packages
     * 
     * @param packageName name of package the ItemStack references
     * @return created ItemStack
     */
	private ItemStack packageGuiItem(String packageName) {
		try {
			Package pkg = PackageManager.get(packageName);
			double price = pkg.getPrice();
			return createGuiItem(Material.CHEST, packageName, 1,
					ChatHandler.get(MessageKey.GUI_PACKAGE_PRICE, Map.of("price", String.valueOf(price))));
		} catch (PackageNotFoundException e) {
			AirdropLogger.warning("Skipping package entry '" + packageName
					+ "' in packages GUI because it is missing from memory");
			return null;
		}
	}

    public void openInventory(final HumanEntity ent) {
        ensureListenerRegistered();
        ent.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent e) {

        if (!e.getInventory().equals(inv))
            return;

        e.setCancelled(true);

        final ItemStack clickedItem = e.getCurrentItem();

        // verify current item is not null
        if (clickedItem == null || clickedItem.getType().isAir())
            return;

        if (!(e.getWhoClicked() instanceof Player p))
            return;

        String displayName = getDisplayName(clickedItem);
        if (displayName.isEmpty()) {
            return;
        }
        String packageName = displayName;

        Package pkg = null;
        try {
            pkg = PackageManager.get(packageName);
        } catch (PackageNotFoundException error) {
            ChatHandler.sendError(p, MessageKey.ERROR_PACKAGE_NOT_FOUND,
                    Map.of("name", error.getPackageName()));
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

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent e) {
        if (!e.getInventory().equals(inv)) {
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
