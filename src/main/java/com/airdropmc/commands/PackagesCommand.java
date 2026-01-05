package com.airdropmc.commands;

import com.airdropmc.Airdrop;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.MessageKey;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PackagesCommand {

    private PackagesCommand() {

    }

    public static void onCommand(CommandSender sender) {

        // Lists available packages
        // /airdrop packages

        if (!(sender instanceof Player)) {
            ChatHandler.sendError(sender, MessageKey.COMMANDS_PLAYER_ONLY);
            ChatHandler.sendError(sender, MessageKey.COMMANDS_PACKAGES_CONSOLE_ONLY);
            return;
        }

        Airdrop.getPackagesGui().openInventory((Player) sender);
    }
}
