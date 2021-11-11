package me.lukemccon.airdrop.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;


import me.lukemccon.airdrop.controllers.DropController;
import me.lukemccon.airdrop.controllers.PackageController;
import me.lukemccon.airdrop.helpers.ChatHandler;

public class CmdAirdrop implements CommandExecutor {

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		
		Player player = null;

		// Check if sender is a Player
		if (sender instanceof Player) {

			// Cast sender to Player
			player = (Player) sender;

			// If no arguments, return false
			if (args.length == 0) {
				return false;
			}
			
			switch(args.length){
				case 0:
					return false;
				default:
					switch (args[0]){
						case "packages":
							return PackageController.onCommand(player, args);
						case "gift":
							return true;
						case "drop":
							return DropController.onCommand(player, args);
						default:
							ChatHandler.sendErrorMessage(player, "Invalid Command: " + args[0]);
							return false;
					}
			}
		}

		// If sender isn't player, return false
		return false;
	}

}
