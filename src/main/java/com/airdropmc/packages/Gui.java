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

        // Set  the name of the item
        assert meta != null;
        meta.setDisplayName(name);

        // Set the lore of the item
        meta.setLore(Arrays.asList(lore));

        item.setItemMeta(meta);

        return item;
    }
}
