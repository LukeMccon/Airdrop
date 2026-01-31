package com.airdropmc;

import com.airdropmc.commands.PackageTabCompletion;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class AirdropTabCompleter implements TabCompleter {

    private static final List<String> subCommands = Arrays.asList("[packageName]", "package", "packages", "version", "reload");
    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String alias, String[] args) {
        // If no arguments, return false
        if (args.length == 1) {
            return subCommands;
        }

        if (Objects.equals(args[0], "package")) {
            return (new PackageTabCompletion()).onTabComplete(commandSender, command, alias, args);
        }
        return new ArrayList<>();
    }
}
