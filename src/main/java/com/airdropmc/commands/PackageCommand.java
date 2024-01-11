package com.airdropmc.commands;

import com.airdropmc.controllers.PackageController;
import com.airdropmc.exceptions.PackageNotFoundException;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.packages.PackageManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Objects;

public class PackageCommand {

    private PackageCommand() {

    }

    public static void onCommand(CommandSender sender, String[] args) {

        if (args.length < 2) {
            ChatHandler.sendErrorMessage(sender, "Must specify a package name");
            ChatHandler.sendErrorMessage(sender, "Example: /airdrop starter");
            return;
        }

        if (Objects.equals(args[1], "create")) {
            PackageController.createPackageCommand(sender, args);
            return;
        }

        if (Objects.equals(args[1], "delete")) {
            PackageController.deletePackageCommand(sender, args);
            return;
        }

        String packageName = args[1];

        try {
            ChatHandler.sendMessage(sender, ChatColor.WHITE + "\nPackage info for: " + ChatColor.AQUA + packageName + "\n================\n" +  ChatColor.WHITE + PackageManager.getInfo(packageName));
        } catch (PackageNotFoundException e) {
            ChatHandler.sendErrorMessage(sender, e.getMessage());
        }
    }


}
