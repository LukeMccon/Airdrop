package lukemccon.airdrop.commands;

import lukemccon.airdrop.Airdrop;
import lukemccon.airdrop.controllers.PackageController;
import lukemccon.airdrop.helpers.ChatHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import lukemccon.airdrop.controllers.DropController;
import lukemccon.airdrop.controllers.PackagesController;

public class CmdAirdrop implements CommandExecutor {

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

			// If no arguments exit
			if (args.length == 0) {
				return false;
			}
			
			switch (args[0]) {
				case "package":
					return PackageController.onCommand(sender,args);
				case "packages":
					return PackagesController.onCommand(sender, args);
				case "version":
					ChatHandler.sendMessage(sender, ChatColor.WHITE + "\nAirdrop Version: " + ChatColor.AQUA + Airdrop.PLUGIN_VERSION + ChatColor.WHITE + "\nSpigot API Version: " + ChatColor.AQUA + Airdrop.PLUGIN_API_VERSION);
					return true;
				default:
					return DropController.onCommand(sender, args);
			}
	}

}
