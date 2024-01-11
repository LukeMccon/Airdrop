package com.airdropmc.commands;

import com.airdropmc.packages.PackageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PackageTabCompletion implements TabCompleter {

    @Override
    public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String alias, String[] args) {

        // args[0] verified to be "package"

        String createCommand = "create";
        switch (args.length) {

            case 2:
                List<String> commands = new ArrayList<>(PackageManager.getPackages().stream().toList());
                commands.add(createCommand);
                commands.add("delete");
                return commands;
            case 3:
                if (args[1].equals(createCommand) || args[1].equals("delete")) {
                    return List.of("[packageName]");
                }
                return Collections.emptyList();
            case 4:
                if (args[1].equals(createCommand)) {
                    return List.of("[price]");
                }
                return Collections.emptyList();
            default:
                return Collections.emptyList();
        }
    }
}
