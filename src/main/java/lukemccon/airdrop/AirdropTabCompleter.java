package lukemccon.airdrop;

import lukemccon.airdrop.controllers.DropController;
import lukemccon.airdrop.controllers.PackageController;
import lukemccon.airdrop.controllers.PackageTabCompletion;
import lukemccon.airdrop.controllers.PackagesController;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AirdropTabCompleter implements TabCompleter {

    public static List<String> subCommands = Arrays.asList("package", "packages");
    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String alias, String[] args) {
        // If no arguments, return false
        if (args.length == 1) {
            return subCommands;
        }

        switch (args[0]) {
            case "package":
                return (new PackageTabCompletion()).onTabComplete(commandSender, command, alias, args);
//            case "packages":
//                return PackagesController.onCommand(player, args);
        }
        return new ArrayList<String>();
    }
}
