package com.airdropmc.commands;

import com.airdropmc.packages.PackageManager;
import com.airdropmc.helpers.PermissionsHelper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.Objects;

public class PackageTabCompletion implements TabCompleter {

    @Override
    public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command,
            @NotNull String alias, String[] args) {

        // args[0] verified to be "package"

        // Initialize with empty string if args length <= 1
        String commandArg = args.length > 1 ? args[1] : "";
        boolean isAdmin = PermissionsHelper.isAdmin(commandSender);

        String createCommand = "create";
        switch (args.length) {

            case 2:
                List<String> commands = new ArrayList<>();
                if (isAdmin) {
                    commands.add(createCommand);
                    commands.add("delete");
                }
                commands.addAll(PackageManager.getPackages());
                return commands;
            case 3:
                if (Objects.equals(commandArg, createCommand) || Objects.equals(commandArg, "delete")) {
                    if (!isAdmin) {
                        return Collections.emptyList();
                    }
                    return List.of("[packageName]");
                }
                return Collections.emptyList();
            case 4:
                if (Objects.equals(commandArg, createCommand) && isAdmin) {
                    return List.of("[price]");
                }
                return Collections.emptyList();
            default:
                return Collections.emptyList();
        }
    }
}
