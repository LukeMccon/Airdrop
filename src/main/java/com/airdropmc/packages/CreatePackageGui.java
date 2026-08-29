package com.airdropmc.packages;

import com.airdropmc.Airdrop;
import com.airdropmc.exceptions.DuplicatePackageException;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.MessageKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public class CreatePackageGui extends PackageEditorGui {
	private final double price;

	public CreatePackageGui(String name, double price) {
		super(name, false);
		this.price = price;
		initializeItems();
	}

	public void initializeItems() {
		initializeEditorItems(List.of());
	}

	@Override
	protected boolean validateSave(Player player) {
		PackageNamePolicy.Result nameValidation = PackageNamePolicy.validate(getName());
		if (nameValidation.accepted()) {
			return true;
		}

		MessageKey message = switch (nameValidation.rejection()) {
			case MISSING -> MessageKey.PACKAGES_NAME_REQUIRED;
			case INVALID_CHARACTERS -> MessageKey.PACKAGES_NAME_INVALID;
			case RESERVED -> MessageKey.PACKAGES_NAME_RESERVED;
		};
		ChatHandler.sendError(player, message);
		return false;
	}

	@Override
	protected CompletionStage<Boolean> persist(Airdrop plugin, List<ItemStack> items) {
		return plugin.createPackageAsync(new Package(getName(), price, items));
	}

	@Override
	protected MessageKey saveSuccessMessage() {
		return MessageKey.PACKAGES_CREATED;
	}

	@Override
	protected MessageKey cancelMessage() {
		return MessageKey.PACKAGES_CREATE_CANCELED;
	}

	@Override
	protected boolean handleSpecificSaveFailure(Player player, Throwable failure) {
		if (!(failure instanceof DuplicatePackageException duplicate)) {
			return false;
		}

		ChatHandler.sendError(player, MessageKey.ERROR_PACKAGE_EXISTS,
				Map.of("name", duplicate.getPackageName()));
		return true;
	}
}
