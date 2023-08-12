package lukemccon.airdrop.packages;

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
import java.util.stream.Collectors;

public class PackageGui implements Listener {
    private final Inventory inv;
    private final int ROW_SIZE = 9;
    private final int SAVE_CANCEL_PADDING = 3;
    private final Package pkg;

    public PackageGui(Package pkg) {

        this.pkg = pkg;

        int inventorySize;
        int packageCount = PackageManager.getNumberofPackages();

        System.out.println(packageCount);

        inventorySize = (int) (ROW_SIZE * Math.ceil(((packageCount + SAVE_CANCEL_PADDING)/ROW_SIZE) + 1 ));

        // Create a new inventory, with no owner (as this isn't a real inventory), a size of nine, called example
        inv = Bukkit.createInventory(null, inventorySize, "Packages");

        // Put the items into the inventory
        initializeItems();
    }

    // You can call this whenever you want to put the items in
    public void initializeItems() {

        List<ItemStack> itemList = this.getItems();
        itemList.forEach(item -> inv.addItem(item));

        int inventorySize = inv.getSize();

        inv.setItem(inventorySize - 2, createGuiItem(Material.GREEN_WOOL, "Save"));
        inv.setItem(inventorySize - 1 ,createGuiItem(Material.RED_WOOL, "Cancel"));

    }

    private List<ItemStack> getItems() {
        return pkg.getItems().stream().map(item -> createGuiItem(item.getType(), "")).collect(Collectors.toList());
    }

    // Nice little method to create a gui item with a custom name, and description
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

    // You can open the inventory with this
    public void openInventory(final HumanEntity ent) {
        ent.openInventory(inv);
    }

    // Check for clicks on items
    @EventHandler
    public void onInventoryClick(final InventoryClickEvent e) {
        if (!e.getInventory().equals(inv)) return;

        e.setCancelled(true);

        final ItemStack clickedItem = e.getCurrentItem();

        // verify current item is not null
        if (clickedItem == null || clickedItem.getType().isAir()) return;

        final Player p = (Player) e.getWhoClicked();

        // Using slots click is a best option for your inventory click's
        p.sendMessage("You clicked at slot " + e.getRawSlot());
    }

    // Cancel dragging in our inventory
    @EventHandler
    public void onInventoryClick(final InventoryDragEvent e) {
        if (e.getInventory().equals(inv)) {
            e.setCancelled(true);
        }
    }
}
