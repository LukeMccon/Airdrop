package com.airdropmc.commands;

import com.airdropmc.Airdrop;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.ChatTheme;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;


public class CmdAirdrop implements CommandExecutor {

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {

			// If no arguments exit
			if (args.length == 0) {
				return false;
			}

        switch (args[0]) {
            case "package" -> PackageCommand.onCommand(sender, args);
            case "packages" -> PackagesCommand.onCommand(sender);
            case "version" -> ChatHandler.sendMessage(sender, ChatTheme.text() + "\nAirdrop Version: " + ChatTheme.accent()
                    + Airdrop.getVersion() + ChatTheme.text() + "\nSpigot API Version: " + ChatTheme.accent()
                    + Airdrop.getPluginApiVersion());
            default -> DropCommand.onCommand(sender, args);
        }
        return true;
	}

}
