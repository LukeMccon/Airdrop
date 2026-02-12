package com.airdropmc;

import com.airdropmc.commands.PackageTabCompletion;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.packages.PackageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class AirdropTabCompleter implements TabCompleter {

    private static final List<String> ADMIN_SUB_COMMANDS = List.of("package", "packages", "version", "reload", "debug");
    private static final List<String> NON_ADMIN_SUB_COMMANDS = List.of("package", "packages", "version");
    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String alias, String[] args) {
        // If no arguments, return false
        if (args.length == 1) {
            Set<String> suggestions = new LinkedHashSet<>(PackageManager.getPackages());
            if (PermissionsHelper.isAdmin(commandSender)) {
                suggestions.addAll(ADMIN_SUB_COMMANDS);
                return new ArrayList<>(suggestions);
            }
            suggestions.addAll(NON_ADMIN_SUB_COMMANDS);
            return new ArrayList<>(suggestions);
        }

        if (args.length == 2 && Objects.equals(args[0], "debug")) {
            if (!PermissionsHelper.isAdmin(commandSender)) {
                return List.of();
            }
            return List.of("on", "off", "toggle");
        }

        if (Objects.equals(args[0], "package")) {
            return (new PackageTabCompletion()).onTabComplete(commandSender, command, alias, args);
        }
        return new ArrayList<>();
    }
}
