package com.airdropmc.commands;

import com.airdropmc.Airdrop;
import com.airdropmc.Config;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.helpers.AirdropLogger;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.packages.PackageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;


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
            case "version" -> {
                String version = Airdrop.getVersion() != null ? Airdrop.getVersion() : "unknown";
                String apiVersion = Airdrop.getPluginApiVersion() != null ? Airdrop.getPluginApiVersion() : "unknown";
                ChatHandler.sendWithoutPrefix(sender, MessageKey.SYSTEM_VERSION_INFO, Map.of(
                        "version", version,
                        "api_version", apiVersion));
            }
            case "reload" -> {
                if (!PermissionsHelper.isAdmin(sender)) {
                    ChatHandler.sendError(sender, MessageKey.ADMIN_PERMISSION_REQUIRED);
                    return true;
                }
                Config configuration = Airdrop.getConfiguration();
                Airdrop plugin = Airdrop.getPluginInstance();
                if (configuration == null || plugin == null || !plugin.isEnabled()) {
                    ChatHandler.sendErrorMessage(sender, "Reload unavailable while plugin is shutting down");
                    return true;
                }

                configuration.reloadConfig();
                FileConfiguration configValues = configuration.getConfig();
                String lang = configValues != null ? configValues.getString("language", "en") : "en";
                if (plugin.getLanguageManager() != null) {
                    plugin.getLanguageManager().loadLanguage(lang);
                }

                if (!PackageManager.reload()) {
                    ChatHandler.sendErrorMessage(sender, "Reload failed because packages configuration is unavailable");
                    return true;
                }
                ChatHandler.send(sender, MessageKey.SYSTEM_RELOAD_SUCCESS);
            }
            case "debug" -> handleDebugCommand(sender, args);
            default -> DropCommand.onCommand(sender, args);
        }
        return true;
	}

    private static void handleDebugCommand(CommandSender sender, String[] args) {
        if (!PermissionsHelper.isAdmin(sender)) {
            ChatHandler.sendError(sender, MessageKey.ADMIN_PERMISSION_REQUIRED);
            return;
        }

        boolean debugEnabled = ConfigKeys.isDebugLoggingEnabled();
        if (args.length == 1) {
            ChatHandler.send(sender, MessageKey.SYSTEM_DEBUG_STATUS, Map.of(
                    "status", debugEnabled ? "enabled" : "disabled"));
            return;
        }

        Boolean nextValue = parseDebugValue(args[1], debugEnabled);
        if (nextValue == null) {
            ChatHandler.sendError(sender, MessageKey.SYSTEM_DEBUG_USAGE);
            return;
        }

        Airdrop.getConfiguration().getConfig().set(ConfigKeys.LOGGING_DEBUG, nextValue);
        Airdrop.getConfiguration().saveConfig();

        ChatHandler.send(sender, MessageKey.SYSTEM_DEBUG_UPDATED, Map.of(
                "status", nextValue ? "enabled" : "disabled"));
        AirdropLogger.info("Debug logging " + (nextValue ? "enabled" : "disabled") + " by " + sender.getName());
    }

    private static Boolean parseDebugValue(String value, boolean currentValue) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "on", "enable", "enabled", "true" -> true;
            case "off", "disable", "disabled", "false" -> false;
            case "toggle" -> !currentValue;
            default -> null;
        };
    }

}
