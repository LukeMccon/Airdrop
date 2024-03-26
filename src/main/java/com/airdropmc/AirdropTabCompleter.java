package com.airdropmc;

import com.airdropmc.commands.PackageTabCompletion;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AirdropTabCompleter implements TabCompleter {

    private static final List<String> subCommands = Arrays.asList("[packageName]", "package", "packages", "version");
    @Override
    public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String alias, String[] args) {
        // If no arguments, return false
        if (args.length == 1) {
            return subCommands;
        }

        if (args[0].equals("package")) {
            return (new PackageTabCompletion()).onTabComplete(commandSender, command, alias, args);
        }
        return new ArrayList<>();
    }
}
