package com.airdropmc.packages;

import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.MessageKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public abstract class Gui {

    static final NamespacedKey CONTROL_MARKER_KEY = Objects.requireNonNull(
            NamespacedKey.fromString("airdrop:gui_control"));
    static final NamespacedKey PACKAGE_ICON_MARKER_KEY = Objects.requireNonNull(
            NamespacedKey.fromString("airdrop:package_icon"));

    protected enum Control {
        HELP("help", Material.BOOK),
        BACK("back", Material.BLUE_WOOL),
        SAVE("save", Material.GREEN_WOOL),
        CANCEL("cancel", Material.RED_WOOL);

        private final String marker;
        private final Material material;

        Control(String marker, Material material) {
            this.marker = marker;
            this.material = material;
        }
    }

    /**
     * Returns localized control labels for display-only compatibility with older GUI subclasses.
     * Control identity must use the PDC marker instead of these visible names.
     *
     * @deprecated Use {@link #isControlItem(ItemStack)} to identify controls.
     */
    @Deprecated(forRemoval = false)
    protected static List<String> getControlItemNames() {
        return Arrays.asList(
                ChatHandler.get(MessageKey.GUI_SAVE),
                ChatHandler.get(MessageKey.GUI_CANCEL),
                ChatHandler.get(MessageKey.GUI_BACK),
                ChatHandler.get(MessageKey.GUI_HELP));
    }

    /**
     * Returns an item's visible display name for compatibility with older GUI subclasses.
     * Visible names must not be used to identify controls or package icons.
     *
     * @deprecated Use {@link #isControlItem(ItemStack)} or {@link #getPackageIconMarker(ItemStack)} for GUI identity.
     */
    @Deprecated(forRemoval = false)
    protected static String getDisplayName(ItemStack item) {
        if (item == null) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
    }

    protected static boolean isControlItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        for (Control control : Control.values()) {
            if (isControlItem(item, control)) {
                return true;
            }
        }
        return false;
    }

    protected static boolean isControlItem(ItemStack item, Control expected) {
        if (item == null || item.getType() != expected.material) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && expected.marker.equals(
                meta.getPersistentDataContainer().get(CONTROL_MARKER_KEY, PersistentDataType.STRING));
    }

    protected static String getPackageIconMarker(ItemStack item) {
        if (item == null || item.getType() != Material.CHEST) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(
                PACKAGE_ICON_MARKER_KEY, PersistentDataType.STRING);
    }

    protected ItemStack createControlItem(
            final Control control,
            final String name,
            final String... lore) {
        return createMarkedGuiItem(
                control.material,
                name,
                CONTROL_MARKER_KEY,
                control.marker,
                lore);
    }

    protected ItemStack createPackageIcon(
            final String packageName,
            final String displayName,
            final String... lore) {
        return createMarkedGuiItem(
                Material.CHEST,
                displayName,
                PACKAGE_ICON_MARKER_KEY,
                packageName,
                lore);
    }

    /**
     * Creates an ItemStack that will be placed within a GUI.
     *
     * @param material material of the ItemStack
     * @param name display name of the ItemStack
     * @param amount number of items in the stack
     * @param lore metadata associated with the item
     * @return new ItemStack with properties
     */
    protected ItemStack createGuiItem(final Material material, final String name, int amount, final String... lore) {
        final ItemStack item = new ItemStack(material, amount);
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createMarkedGuiItem(
            Material material,
            String name,
            NamespacedKey markerKey,
            String markerValue,
            String... lore) {
        ItemStack item = createGuiItem(material, name, 1, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.STRING, markerValue);
        item.setItemMeta(meta);
        return item;
    }
}
