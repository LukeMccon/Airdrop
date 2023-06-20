package lukemccon.airdrop.controllers;

import lukemccon.airdrop.packages.PackageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PackageTabCompletion implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String alias, String[] args) {

        // args[0] verified to be "package"

        if (args.length == 2) {
            return PackageManager.getPackages().stream().toList();
        } else {
            return new ArrayList<String>();
        }
    }
}
