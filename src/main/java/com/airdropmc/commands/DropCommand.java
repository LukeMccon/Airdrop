package com.airdropmc.commands;

import com.airdropmc.exceptions.CannotAffordException;
import com.airdropmc.exceptions.EconomyUnavailableException;
import com.airdropmc.exceptions.InsufficientPermissionsException;
import com.airdropmc.exceptions.PackageNotFoundException;
import com.airdropmc.exceptions.SkyNotClearException;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.packages.Package;
import com.airdropmc.packages.PackageManager;
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
                ChatHandler.sendErrorMessage(player, "Economy provider is unavailable. Contact a server administrator.");
            } catch (SkyNotClearException e) {
                ChatHandler.sendError(player, MessageKey.ERROR_SKY_NOT_CLEAR);
            } catch (InsufficientPermissionsException e) {
                ChatHandler.sendError(player, MessageKey.ERROR_INSUFFICIENT_PERMISSIONS,
                        Map.of("package", e.getPackageName()));
            }

        }
    }

}
