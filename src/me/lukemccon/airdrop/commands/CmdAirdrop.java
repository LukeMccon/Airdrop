package me.lukemccon.airdrop.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.lukemccon.airdrop.controllers.DropController;
import me.lukemccon.airdrop.controllers.PackageController;

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
				case "packages":
					return PackageController.onCommand(player, args);
				default:
					return DropController.onCommand(player, args);
			}
		}

		// If sender isn't player, return false
		return false;
	}

}
