package com.airdropmc.packages;

import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.MessageKey;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public abstract class Gui {

    protected static List<String> getControlItemNames() {
        return Arrays.asList(
                ChatHandler.get(MessageKey.GUI_SAVE),
                ChatHandler.get(MessageKey.GUI_CANCEL),
                ChatHandler.get(MessageKey.GUI_BACK));
    }

    /**
     * Safely extracts the display name from an ItemStack
     * @param item the ItemStack to get the display name from
     * @return the display name, or empty string if not available
     */
    protected static String getDisplayName(ItemStack item) {
        if (item == null) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return "";
    }

    /**
     * Checks if the given ItemStack is a GUI control item (save, cancel, back)
     * @param item the ItemStack to check
     * @return true if the item is a control item
     */
    protected static boolean isControlItem(ItemStack item) {
        String displayName = getDisplayName(item);
        return !displayName.isEmpty() && getControlItemNames().contains(displayName);
    }

    /**
     * Creates a ItemStack that will be placed within the GUI
     * @param material of the ItemStack
     * @param name of the ItemStack
     * @param amount of Items in the ItemStack
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
}
