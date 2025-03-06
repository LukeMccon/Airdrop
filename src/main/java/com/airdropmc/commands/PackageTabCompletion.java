package com.airdropmc.commands;

import com.airdropmc.packages.PackageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PackageTabCompletion implements TabCompleter {

    @Override
    public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command,
            @NotNull String alias, String[] args) {

        // args[0] verified to be "package"

        // Initialize with empty string if args length <= 1
        String commandArg = args.length > 1 ? args[1] : "";

        String createCommand = "create";
        switch (args.length) {

            case 2:
                List<String> commands = new ArrayList<>(PackageManager.getPackages().stream().toList());
                commands.add(createCommand);
                commands.add("delete");
                return commands;
            case 3:
                if (commandArg.equals(createCommand) || commandArg.equals("delete")) {
                    return List.of("[packageName]");
                }
                return Collections.emptyList();
            case 4:
                if (commandArg.equals(createCommand)) {
                    return List.of("[price]");
                }
                return Collections.emptyList();
            default:
                return Collections.emptyList();
        }
    }
}
