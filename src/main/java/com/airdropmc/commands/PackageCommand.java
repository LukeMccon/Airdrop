package com.airdropmc.commands;

import com.airdropmc.controllers.PackageController;
import com.airdropmc.exceptions.PackageNotFoundException;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.packages.Package;
import com.airdropmc.packages.PackageManager;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PackageCommand {

    private PackageCommand() {

    }

    public static void onCommand(CommandSender sender, String[] args) {

        if (args.length < 2) {
            ChatHandler.sendError(sender, MessageKey.COMMANDS_PACKAGE_SPECIFY);
            ChatHandler.sendError(sender, MessageKey.COMMANDS_PACKAGE_EXAMPLE);
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
            Package pkg = PackageManager.get(packageName);
			Map<String, String> placeholders = new HashMap<>();
			placeholders.put("name", packageName);
			placeholders.put("price", String.valueOf(pkg.getPrice()));
			placeholders.put("info", pkg.toString());
            ChatHandler.sendWithoutPrefix(sender, MessageKey.PACKAGES_INFO, placeholders);
        } catch (PackageNotFoundException e) {
            ChatHandler.sendError(sender, MessageKey.ERROR_PACKAGE_NOT_FOUND,
                    Map.of("name", e.getPackageName()));
        }
    }

}
