package com.airdropmc.packages;

import com.airdropmc.Airdrop;
import com.airdropmc.exceptions.PackageNotFoundException;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.MessageKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class PackageGui extends PackageEditorGui {
	private final Package pkg;

	public PackageGui(Package pkg) {
		super(pkg.getName(), true);
		this.pkg = pkg;
		initializeItems();
	}

	public static void closeOpenEditors() {
		PackageEditorSession.closeOpenEditors();
	}

	public void initializeItems() {
		initializeEditorItems(pkg.getItems());
	}

	@Override
	protected CompletionStage<Boolean> persist(Airdrop plugin, List<ItemStack> items) {
		return plugin.updatePackageInventoryAsync(getName(), items);
	}

	@Override
	protected MessageKey saveSuccessMessage() {
		return MessageKey.PACKAGES_SAVED;
	}

	@Override
	protected MessageKey cancelMessage() {
		return MessageKey.PACKAGES_EDIT_CANCELED;
	}

	@Override
	protected boolean handleSpecificSaveFailure(Player player, Throwable failure) {
		if (!(failure instanceof PackageNotFoundException notFound)) {
			return false;
		}

		ChatHandler.sendError(player, MessageKey.ERROR_PACKAGE_NOT_FOUND,
				Map.of("name", notFound.getPackageName()));
		return true;
	}

	@Override
	protected void navigateBack(InventoryClickEvent event) {
		back(event);
	}

	public void back(final InventoryClickEvent event) {
		Player player = (Player) event.getWhoClicked();
		scheduleTransition(player, () -> {
			PackagesGui packagesGui = Airdrop.getPackagesGui();
			if (packagesGui == null) {
				player.closeInventory();
				return;
			}
			packagesGui.openInventory(player);
		});
	}

	public static boolean isControlItemStack(ItemStack itemstack) {
		return isControlItem(itemstack);
	}
}
