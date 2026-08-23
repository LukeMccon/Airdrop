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

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String alias, String[] args) {
        // If no arguments, return false
        if (args.length == 1) {
            Set<String> suggestions = new LinkedHashSet<>(PackageManager.getPackages());
            suggestions.addAll(AirdropCommandNames.visibleTo(PermissionsHelper.isAdmin(commandSender)));
            return new ArrayList<>(suggestions);
        }

        if (Objects.equals(args[0], AirdropCommandNames.PACKAGE)) {
            return (new PackageTabCompletion()).onTabComplete(commandSender, command, alias, args);
        }
        return new ArrayList<>();
    }
}
