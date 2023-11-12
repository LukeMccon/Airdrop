package lukemccon.airdrop.packages;

import lukemccon.airdrop.Airdrop;

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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * GUI that shows available packages within airdrop
 */
public class PackagesGui implements Listener {
    private final Inventory inv;
    private final int ROW_SIZE = 9;
    private final int SAVE_CANCEL_PADDING = 3;

    public int inventorySize;

    public PackagesGui() {

        int inventorySize;
        int packageCount = PackageManager.getNumberofPackages();

        inventorySize = (int) (ROW_SIZE * Math.ceil(((packageCount + SAVE_CANCEL_PADDING)/ROW_SIZE) + 1 ));

        if (inventorySize < 9) {
            inventorySize = 9;
        }

        // Create a new inventory, with no owner (as this isn't a real inventory), a size of nine, called example
        inv = Bukkit.createInventory(null, inventorySize, "Packages");

        // Put the items into the inventory
        initializeItems();
    }

    // You can call this whenever you want to put the items in
    public void initializeItems() {
        Set<String> packages = PackageManager.getPackages();

        List<ItemStack> pkglist = packages.stream().map(this::packageGuiItem).toList();
        pkglist.forEach(inv::addItem);
    }

    private ItemStack packageGuiItem(String packageName) {
        return createGuiItem(Material.CHEST, packageName, "");
    }

    // Creates a gui item with a custom name, and description
    protected ItemStack createGuiItem(final Material material, final String name, final String... lore) {
        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = item.getItemMeta();

        // Set the name of the item
        meta.setDisplayName(name);

        // Set the lore of the item
        meta.setLore(Arrays.asList(lore));

        item.setItemMeta(meta);

        return item;
    }

    public void openInventory(final HumanEntity ent) {
        ent.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent e) {

        if (!e.getInventory().equals(inv)) return;

        e.setCancelled(true);

        final ItemStack clickedItem = e.getCurrentItem();

        // verify current item is not null
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        final Player p = (Player) e.getWhoClicked();


        String packageName =  e.getCurrentItem().getItemMeta().getDisplayName().toLowerCase();
        Airdrop.PACKAGE_GUIS.get(packageName).openInventory(p);
    }

    @EventHandler
    public void onInventoryClick(final InventoryDragEvent e) {
        if (e.getInventory().equals(inv)) {
            e.setCancelled(true);
        }
    }
}
