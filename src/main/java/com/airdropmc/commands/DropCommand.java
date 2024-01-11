package com.airdropmc.commands;

import com.airdropmc.exceptions.CannotAffordException;
import com.airdropmc.exceptions.InsufficientPermissionsException;
import com.airdropmc.exceptions.PackageNotFoundException;
import com.airdropmc.exceptions.SkyNotClearException;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.packages.Package;
import com.airdropmc.packages.PackageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.airdropmc.controllers.DropController;

public class DropCommand {

    private DropCommand(){

    }

    public static void onCommand(CommandSender sender, String[] args) {

        String packageName = args[0];
        Package pkg;

        if (!(sender instanceof Player player)) {
            ChatHandler.sendErrorMessage(sender,"Must be a player to use this command");
            return;
        }

        try {
            pkg = PackageManager.get(packageName);
        } catch (PackageNotFoundException e) {
            ChatHandler.sendErrorMessage(player, e.getMessage());
            return;
        }

        if(args.length == 1) {

            try {
                DropController.playerInitiatedDropPackage(pkg, player);
            } catch (CannotAffordException | SkyNotClearException | InsufficientPermissionsException e) {
                ChatHandler.sendErrorMessage(player, e.getMessage());
            }

        }
    }

}
