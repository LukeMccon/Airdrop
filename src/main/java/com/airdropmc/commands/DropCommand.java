package com.airdropmc.commands;

import com.airdropmc.exceptions.CannotAffordException;
import com.airdropmc.exceptions.EconomyUnavailableException;
import com.airdropmc.exceptions.DropLimitException;
import com.airdropmc.exceptions.InsufficientPermissionsException;
import com.airdropmc.exceptions.PackageNotFoundException;
import com.airdropmc.exceptions.SkyNotClearException;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.packages.Package;
import com.airdropmc.packages.PackageManager;
import com.airdropmc.packages.PackageNamePolicy;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.airdropmc.controllers.DropController;

import java.util.Map;
public class DropCommand {

    private DropCommand() {

    }

    public static void onCommand(CommandSender sender, String[] args) {

        String packageName = args[0];
        Package pkg;

        if (!(sender instanceof Player player)) {
            ChatHandler.sendError(sender, MessageKey.COMMANDS_PLAYER_ONLY);
            return;
        }

        try {
            pkg = PackageManager.get(packageName);
        } catch (PackageNotFoundException e) {
            ChatHandler.sendError(player, MessageKey.ERROR_PACKAGE_NOT_FOUND,
                    Map.of("name", e.getPackageName()));
            return;
        }

        if (args.length == 1) {

			try {
				DropController.playerInitiatedDropPackage(pkg, player);
			} catch (CannotAffordException e) {
				ChatHandler.sendError(player, MessageKey.ERROR_CANNOT_AFFORD, Map.of(
						"player", e.getPlayerName(),
						"price", String.valueOf(e.getPrice())));
			} catch (EconomyUnavailableException e) {
				ChatHandler.sendError(player, MessageKey.ERROR_ECONOMY_UNAVAILABLE);
			} catch (SkyNotClearException e) {
				ChatHandler.sendError(player, MessageKey.ERROR_SKY_NOT_CLEAR);
			} catch (InsufficientPermissionsException e) {
				String canonicalName = PackageNamePolicy.requireCanonical(e.getPackageName());
				ChatHandler.sendError(player, MessageKey.ERROR_INSUFFICIENT_PERMISSIONS,
						Map.of(
								"permission", PackageNamePolicy.permissionNode(e.getPackageName()),
								"package", canonicalName));
			} catch (DropLimitException e) {
				sendLimitError(player, e);
            }

        }
    }

	private static void sendLimitError(Player player, DropLimitException exception) {
		switch (exception.getReason()) {
			case REQUEST_PENDING -> ChatHandler.sendError(player, MessageKey.ERROR_DROP_REQUEST_PENDING);
			case COOLDOWN -> ChatHandler.sendError(player, MessageKey.ERROR_DROP_COOLDOWN,
					Map.of("seconds", String.valueOf(exception.getRetryAfterSeconds())));
			case FALLING_CAPACITY -> ChatHandler.sendError(player, MessageKey.ERROR_DROP_FALLING_LIMIT);
			case LANDED_CAPACITY -> ChatHandler.sendError(player, MessageKey.ERROR_DROP_LANDED_LIMIT);
			case LOCATION_RESERVED -> ChatHandler.sendError(player, MessageKey.ERROR_DROP_LOCATION_RESERVED);
			case SHUTTING_DOWN -> ChatHandler.sendError(player, MessageKey.ERROR_DROP_SHUTTING_DOWN);
		}
	}

}
