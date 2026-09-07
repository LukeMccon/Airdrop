package com.airdropmc.packages;

import com.airdropmc.Airdrop;
import com.airdropmc.exceptions.PackageNotFoundException;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.lang.MessageKey;
import org.bukkit.Material;
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
		this.pkg = new Package(pkg.getName(), pkg.getPrice(), pkg.getItems());
		initializeItems();
	}

	public static void closeOpenEditors() {
		PackageEditorGui.closeTrackedEditors();
	}

	public void initializeItems() {
		initializeEditorItems(pkg.getItems());
		String price = pkg.getPrice() == 0
				? ChatHandler.get(MessageKey.GUI_CATALOG_FREE)
				: ChatHandler.get(MessageKey.GUI_PACKAGE_PRICE, Map.of("price", String.valueOf(pkg.getPrice())));
		initializeInformationItem(createGuiItem(Material.PAPER, pkg.getName(), 1,
				price,
				ChatHandler.get(MessageKey.GUI_PACKAGE_REQUEST, Map.of("name", pkg.getName())),
				ChatHandler.get(MessageKey.GUI_PACKAGE_AVAILABILITY)));
	}

	@Override
	protected boolean canOpen(Player player) {
		return PermissionsHelper.hasPermission(player, getName());
	}

	@Override
	protected boolean isDefinitionCurrent() {
		try {
			// Unrelated writes rematerialize every package, so compare the displayed values.
			Package current = PackageManager.get(getName());
			return current.getName().equals(pkg.getName())
					&& Double.compare(current.getPrice(), pkg.getPrice()) == 0
					&& current.getItems().equals(pkg.getItems());
		} catch (PackageNotFoundException ignored) {
			return false;
		}
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
