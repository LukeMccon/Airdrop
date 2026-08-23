package com.airdropmc.packages;

import com.airdropmc.Airdrop;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.exceptions.DuplicatePackageException;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CreatePackageGui extends Gui implements Listener {
    private final Inventory inv;
    private final String name;
    private final double price;
    private final PackageEditorSession session;

	public CreatePackageGui(String name, double price) {

		this.name = name;
		this.price = price;

        int inventorySize = 36;

        inv = Bukkit.createInventory(null, inventorySize, name);
        session = new PackageEditorSession(inv);

        initializeItems();
    }

    /**
     * Setup control item blocks
     */
    public void initializeItems() {
        int inventorySize = inv.getSize();

        // Add a save an cancel ItemStack to the package
        inv.setItem(inventorySize - 3, createGuiItem(
                Material.BOOK,
                ChatHandler.get(MessageKey.GUI_HELP),
                1,
                ChatHandler.get(MessageKey.GUI_EDITOR_HELP_ADD_STACK),
                ChatHandler.get(MessageKey.GUI_EDITOR_HELP_ADD_ONE),
                ChatHandler.get(MessageKey.GUI_EDITOR_HELP_REMOVE_STACK),
                ChatHandler.get(MessageKey.GUI_EDITOR_HELP_REMOVE_ONE)));
        inv.setItem(inventorySize - 2, createGuiItem(Material.GREEN_WOOL, ChatHandler.get(MessageKey.GUI_SAVE), 1));
        inv.setItem(inventorySize - 1, createGuiItem(Material.RED_WOOL, ChatHandler.get(MessageKey.GUI_CANCEL), 1));
    }

    public boolean openInventory(final Player player) {
        if (!session.bind(player)) {
            return false;
        }

        Airdrop plugin = Airdrop.getPluginInstance();
        if (plugin == null || !plugin.isEnabled()) {
            return retire();
        }

        try {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            InventoryView view = player.openInventory(inv);
            if (view == null || view.getTopInventory() != inv || !session.activate(view.getTopInventory())) {
                return retire();
            }
            return true;
        } catch (RuntimeException error) {
            retire();
            throw error;
        }
    }

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryClick(final InventoryClickEvent e) {
		Inventory top = e.getView().getTopInventory();
		if (!(e.getWhoClicked() instanceof Player player) || !session.protects(player, top)) {
			return;
		}

		e.setCancelled(true);
		if (!session.canProcess(player, top)) {
			return;
		}

		Inventory clickedInventory = e.getClickedInventory();
		if (clickedInventory == null) {
			return;
		}

		boolean controlSlot = clickedInventory == inv && isControlSlot(e.getSlot());
		PackageEditorInteraction.VirtualAction action = PackageEditorInteraction.classify(
				e.getClick(), e.getAction(), e.getCursor(), controlSlot);
		if (action == PackageEditorInteraction.VirtualAction.DENY) {
			return;
		}

		ItemStack clickedItem = e.getCurrentItem();
		if (clickedItem == null || clickedItem.getType().isAir()) {
			return;
		}

		if (action == PackageEditorInteraction.VirtualAction.CONTROL) {
			handleControl(e, player);
			return;
		}

		if (!PermissionsHelper.isAdmin(player)) {
			return;
		}

		if (clickedInventory == inv) {
			if (isEditablePackageSlot(e.getSlot())) {
				removeFromPackage(e.getSlot(), clickedItem, action);
			}
			return;
		}

		if (clickedInventory == player.getInventory()) {
			addItemToPackage(
					player,
					clickedItem,
					action == PackageEditorInteraction.VirtualAction.SINGLE_ITEM);
		}
	}

	private void handleControl(InventoryClickEvent event, Player player) {
		int slot = event.getSlot();
		if (slot == inv.getSize() - 2) {
			if (PermissionsHelper.isAdmin(player)) {
				save(event);
			} else {
				ChatHandler.sendError(player, MessageKey.ADMIN_PACKAGE_SAVE_REQUIRED);
			}
		} else if (slot == inv.getSize() - 1) {
			cancel(event);
		}
	}

	private void removeFromPackage(
			int slot,
			ItemStack clickedItem,
			PackageEditorInteraction.VirtualAction action) {
		if (action == PackageEditorInteraction.VirtualAction.SINGLE_ITEM && clickedItem.getAmount() > 1) {
			ItemStack updated = clickedItem.clone();
			updated.setAmount(clickedItem.getAmount() - 1);
			inv.setItem(slot, updated);
			return;
		}

		inv.setItem(slot, null);
	}

    /**
     * Handle when a player drags an item
     * 
     * @param e drag event
     */
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryClick(final InventoryDragEvent e) {
		if (session.protects(e.getWhoClicked(), e.getView().getTopInventory())) {
			e.setCancelled(true);
		}
	}

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent e) {
        if (session.protects(e.getPlayer(), e.getInventory())) {
            retire();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        if (session.viewerId() != null && session.viewerId().equals(e.getPlayer().getUniqueId())) {
            retire();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent e) {
		if (session.viewerId() == null || !session.viewerId().equals(e.getPlayer().getUniqueId())) {
			return;
		}
		scheduleKickObservation(e.getPlayer());
    }

	private boolean retire() {
		if (!session.retire()) {
			return false;
		}
		HandlerList.unregisterAll(this);
		return false;
	}

	private void scheduleTransition(Player player, Runnable viewChange) {
		if (!session.beginTransition()) {
			return;
		}

		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin == null || !plugin.isEnabled()) {
			retire();
			return;
		}

		Bukkit.getScheduler().runTask(plugin, () -> {
			if (session.state() != PackageEditorSession.State.TRANSITIONING) {
				return;
			}
			if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inv) {
				retire();
				return;
			}

			viewChange.run();
			if (session.state() == PackageEditorSession.State.TRANSITIONING
					&& (!player.isOnline() || player.getOpenInventory().getTopInventory() != inv)) {
				retire();
			}
		});
	}

	private void scheduleKickObservation(Player player) {
		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin == null || !plugin.isEnabled()) {
			retire();
			return;
		}

		Bukkit.getScheduler().runTask(plugin, () -> {
			if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inv) {
				retire();
			}
		});
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

		PackageNamePolicy.Result nameValidation = PackageNamePolicy.validate(this.name);
		if (!nameValidation.accepted()) {
			MessageKey message = switch (nameValidation.rejection()) {
				case MISSING -> MessageKey.PACKAGES_NAME_REQUIRED;
				case INVALID_CHARACTERS -> MessageKey.PACKAGES_NAME_INVALID;
				case RESERVED -> MessageKey.PACKAGES_NAME_RESERVED;
			};
			ChatHandler.sendError(p, message);
			return;
		}

		List<ItemStack> packageItems = PackageManager.sanitizePackageItems(editableItems());
        if (packageItems.size() > PackageManager.MAX_PACKAGE_ITEM_STACKS) {
            ChatHandler.sendError(p, MessageKey.PACKAGES_ITEM_LIMIT,
                    Map.of("max", String.valueOf(PackageManager.MAX_PACKAGE_ITEM_STACKS)));
            e.setCancelled(true);
            return;
        }

        Package pkg = new Package(this.name, this.price, packageItems);
        try {
            if (!PackageManager.createPackage(pkg)) {
                ChatHandler.sendError(p, MessageKey.ERROR_PACKAGE_SAVE_FAILED);
                return;
            }
        } catch (DuplicatePackageException error) {
            ChatHandler.sendError(p, MessageKey.ERROR_PACKAGE_EXISTS,
                    Map.of("name", error.getPackageName()));
            return;
        }

		scheduleTransition(p, p::closeInventory);
        ChatHandler.send(p, MessageKey.PACKAGES_CREATED, Map.of("name", this.getName()));
    }

    /**
     * When the cancel control ItemStack is clicked, create the package
     * 
     * @param e event from clicking cancel
     */
    public void cancel(final InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
		scheduleTransition(p, p::closeInventory);
        ChatHandler.send(p, MessageKey.PACKAGES_CREATE_CANCELED);
    }

	private int getFirstControlSlot() {
		return PackageManager.MAX_PACKAGE_ITEM_STACKS;
	}

	private List<ItemStack> editableItems() {
		List<ItemStack> items = new ArrayList<>(PackageManager.MAX_PACKAGE_ITEM_STACKS);
		for (int slot = 0; slot < PackageManager.MAX_PACKAGE_ITEM_STACKS; slot++) {
			ItemStack item = inv.getItem(slot);
			if (item != null && !item.getType().isAir()) {
				items.add(item.clone());
			}
		}
		return items;
	}

	private boolean isEditablePackageSlot(int slot) {
        return slot >= 0 && slot < getFirstControlSlot();
    }

	private boolean isControlSlot(int slot) {
		return slot >= inv.getSize() - 3 && slot < inv.getSize();
	}

    private void addItemToPackage(Player player, ItemStack itemToCopy, boolean singleItem) {
        int requestedAmount = singleItem ? 1 : itemToCopy.getAmount();
        int remaining = requestedAmount;

        for (int slot = 0; slot < getFirstControlSlot() && remaining > 0; slot++) {
            ItemStack existing = inv.getItem(slot);
            if (existing == null || existing.getType().isAir() || !existing.isSimilar(itemToCopy)) {
                continue;
            }

            int space = existing.getMaxStackSize() - existing.getAmount();
            if (space <= 0) {
                continue;
            }

            int toAdd = Math.min(space, remaining);
            existing.setAmount(existing.getAmount() + toAdd);
            inv.setItem(slot, existing);
            remaining -= toAdd;
        }

        while (remaining > 0) {
			int targetSlot = findFirstEmptyEditableSlot();
			if (targetSlot == -1) {
				ChatHandler.sendError(player, MessageKey.PACKAGES_ITEM_LIMIT,
						Map.of("max", String.valueOf(getFirstControlSlot())));
				return;
			}

            int stackAmount = Math.min(itemToCopy.getMaxStackSize(), remaining);
            ItemStack toInsert = itemToCopy.clone();
            toInsert.setAmount(stackAmount);
            inv.setItem(targetSlot, toInsert);
            remaining -= stackAmount;
        }
    }

    private int findFirstEmptyEditableSlot() {
        for (int slot = 0; slot < getFirstControlSlot(); slot++) {
            ItemStack existing = inv.getItem(slot);
            if (existing == null || existing.getType().isAir()) {
                return slot;
            }
        }
        return -1;
    }

}
