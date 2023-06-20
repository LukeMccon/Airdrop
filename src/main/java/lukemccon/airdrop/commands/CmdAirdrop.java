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

		// Check if sender is a Player
		if (sender instanceof Player) {

			// Cast sender to Player
			Player player = (Player) sender;

			// If no arguments, return false
			if (args.length == 0) {
				return false;
			}
			
			switch (args[0]) {
				case "package":
					return PackageController.onCommand(player,args);
				case "packages":
					return PackagesController.onCommand(player, args);
				case "version":
					ChatHandler.sendMessage(player, ChatColor.WHITE + "\nVERSION: " + Airdrop.PLUGIN_VERSION + "\nAPI_VERSION: " + Airdrop.PLUGIN_API_VERSION);
					return true;
				default:
					return DropController.onCommand(player, args);
			}
		}

		// If sender isn't player, return false
		return false;
	}

}
