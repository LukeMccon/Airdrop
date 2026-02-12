package com.airdropmc;

import com.airdropmc.commands.PackageTabCompletion;
import com.airdropmc.helpers.PermissionsHelper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class AirdropTabCompleter implements TabCompleter {

    private static final List<String> subCommands = Arrays.asList("[packageName]", "package", "packages", "version", "reload", "debug");
    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String alias, String[] args) {
        // If no arguments, return false
        if (args.length == 1) {
            if (PermissionsHelper.isAdmin(commandSender)) {
                return subCommands;
            }
            return Arrays.asList("[packageName]", "package", "packages", "version");
        }

        if (args.length == 2 && Objects.equals(args[0], "debug")) {
            if (!PermissionsHelper.isAdmin(commandSender)) {
                return List.of();
            }
            return Arrays.asList("on", "off", "toggle");
        }

        if (Objects.equals(args[0], "package")) {
            return (new PackageTabCompletion()).onTabComplete(commandSender, command, alias, args);
        }
        return new ArrayList<>();
    }
}
