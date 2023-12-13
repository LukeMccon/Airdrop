package com.airdropmc.commands;

import com.airdropmc.Airdrop;
import com.airdropmc.helpers.ChatHandler;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PackagesCommand {

    private PackagesCommand() {

    }

    public static void onCommand(CommandSender sender) {

        // Lists available packages
        // /airdrop packages

        if (!(sender instanceof Player)) {
            ChatHandler.sendErrorMessage(sender,"Must be a player to use this command");
            return;
        }

        Airdrop.getPackagesGui().openInventory((Player) sender);
    }
}
