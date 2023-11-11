package lukemccon.airdrop.controllers;

import lukemccon.airdrop.packages.PackageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class PackageTabCompletion implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String alias, String[] args) {

        // args[0] verified to be "package"

        switch (args.length) {

            case 2:
                List<String> commands = new ArrayList<>(PackageManager.getPackages().stream().toList());
                commands.add("create");
                return commands;
            case 3:
                if (args[1].equals("create")) {
                    return new ArrayList<>(List.of("[packageName]"));
                }
            case 4:
                if (args[1].equals("create")) {
                    return new ArrayList<>(List.of("[price]"));
                }
            default:
                return new ArrayList<String>();
        }
    }
}
