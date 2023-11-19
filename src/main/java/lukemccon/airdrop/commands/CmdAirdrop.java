package lukemccon.airdrop.commands;

import lukemccon.airdrop.Airdrop;
import lukemccon.airdrop.controllers.PackageController;
import lukemccon.airdrop.helpers.ChatHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import lukemccon.airdrop.controllers.DropController;
import lukemccon.airdrop.controllers.PackagesController;

public class CmdAirdrop implements CommandExecutor {

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

			// If no arguments exit
			if (args.length == 0) {
				return false;
			}

        return switch (args[0]) {
            case "package" -> PackageController.onCommand(sender, args);
            case "packages" -> PackagesController.onCommand(sender);
            case "version" -> {
                ChatHandler.sendMessage(sender, ChatColor.WHITE + "\nAirdrop Version: " + ChatColor.AQUA + Airdrop.getVersion() + ChatColor.WHITE + "\nSpigot API Version: " + ChatColor.AQUA + Airdrop.getPluginApiVersion());
                yield true;
            }
            default -> DropController.onCommand(sender, args);
        };
	}

}
