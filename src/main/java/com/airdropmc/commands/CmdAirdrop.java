package com.airdropmc.commands;

import com.airdropmc.Airdrop;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.lang.MessageKey;
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
            case "version" -> ChatHandler.send(sender, MessageKey.SYSTEM_VERSION_INFO, java.util.Map.of(
                    "version", Airdrop.getVersion(),
                    "api_version", Airdrop.getPluginApiVersion()));
            case "reload" -> {
                if (!PermissionsHelper.isAdmin(sender)) {
                    ChatHandler.sendError(sender, MessageKey.ADMIN_PERMISSION_REQUIRED);
                    return true;
                }
                Airdrop.getConfiguration().reloadConfig();
                String lang = Airdrop.getConfiguration().getConfig().getString("language", "en");
                Airdrop.getPluginInstance().getLanguageManager().loadLanguage(lang);
                ChatHandler.send(sender, MessageKey.SYSTEM_RELOAD_SUCCESS);
            }
            default -> DropCommand.onCommand(sender, args);
        }
        return true;
	}

}
