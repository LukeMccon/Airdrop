package lukemccon.airdrop.packages;

import lukemccon.airdrop.Airdrop;
import lukemccon.airdrop.helpers.ChatHandler;
import lukemccon.airdrop.helpers.PermissionsHelper;
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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PackageGui implements Listener {
    private final Inventory inv;
    private final int ROW_SIZE = 9;
    private final int SAVE_CANCEL_BACK_PADDING = 4;
    private final Package pkg;
    private final String name;

    private static final String[] controlItemNames = { "Save", "Cancel", "Back" };

    public PackageGui(Package pkg) {

        this.pkg = pkg;
        this.name = pkg.getName();

        int inventorySize;
        int packageCount = PackageManager.getNumberofPackages();

        // Logic to determine how large to make the inventory
        inventorySize = (int) (ROW_SIZE * (Math.ceil(((packageCount + SAVE_CANCEL_BACK_PADDING)/ROW_SIZE) + 1 ) + 1));

        inv = Bukkit.createInventory(null, inventorySize, pkg.getName());

        initializeItems();
    }

    public void initializeItems() {

        List<ItemStack> itemList = this.getItems();
        itemList.forEach(item -> inv.addItem(item));

        int inventorySize = inv.getSize();

        inv.setItem(inventorySize - 3, createGuiItem(Material.BLUE_WOOL, "Back"));
        inv.setItem(inventorySize - 2, createGuiItem(Material.GREEN_WOOL, "Save"));
        inv.setItem(inventorySize - 1 ,createGuiItem(Material.RED_WOOL, "Cancel"));

    }

    private List<ItemStack> getItems() {
        return pkg.getItems().stream().map(item -> createGuiItem(item.getType(), "")).collect(Collectors.toList());
    }

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
        final Player p = (Player) e.getWhoClicked();

        if (!e.getInventory().equals(inv)) return;

        final ItemStack clickedItem = e.getCurrentItem();

        if (clickedItem == null || clickedItem.getType().isAir()) return;

        String itemStackName = clickedItem.getItemMeta().getDisplayName();

        switch(itemStackName){

            case "Back":
                this.back(e);
                break;

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
        PackageManager.updatePackageInventory(this.getName(), new ArrayList<>(Arrays.asList(newPackageItems)));

        p.closeInventory();
        ChatHandler.sendMessage(p, "Package " + ChatColor.AQUA + this.getName() + ChatColor.BLUE + " was saved successfully");
    }

    public void cancel(final InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        p.closeInventory();
        ChatHandler.sendMessage(p,"Package edit was canceled");
    }

    public void back(final InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        p.closeInventory();
        Airdrop.PACKAGES_GUI.openInventory(p);
    }

    public static Boolean isControlItemStack(ItemStack itemstack) {
        String itemName = itemstack.getItemMeta().getDisplayName();
        return Arrays.stream(PackageGui.controlItemNames).anyMatch(itemName::equals);
    }



}
