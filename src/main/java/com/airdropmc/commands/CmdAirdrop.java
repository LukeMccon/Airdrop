package com.airdropmc.commands;

import com.airdropmc.Airdrop;
import com.airdropmc.AirdropCommandNames;
import com.airdropmc.economy.EconomyProviderRefreshResult;
import com.airdropmc.helpers.AirdropLogger;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.lang.MessageKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

public class CmdAirdrop implements CommandExecutor {

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
			@NotNull String label, String[] args) {

		if (args.length == 0) {
			return false;
		}

		if (AirdropCommandNames.VERSION.equals(args[0])) {
			String version = Airdrop.getVersion() != null ? Airdrop.getVersion() : "unknown";
			String apiVersion = Airdrop.getPluginApiVersion() != null
					? Airdrop.getPluginApiVersion()
					: "unknown";
			ChatHandler.sendWithoutPrefix(sender, MessageKey.SYSTEM_VERSION_INFO, Map.of(
					"version", version,
					"api_version", apiVersion));
			return true;
		}

		if (!Airdrop.isReady()) {
			ChatHandler.sendError(sender, MessageKey.ERROR_PLUGIN_NOT_READY);
			return true;
		}

		switch (args[0]) {
			case AirdropCommandNames.PACKAGE -> PackageCommand.onCommand(sender, args);
			case AirdropCommandNames.PACKAGES -> PackagesCommand.onCommand(sender);
			case AirdropCommandNames.RELOAD -> reload(sender);
			default -> DropCommand.onCommand(sender, args);
		}
		return true;
	}

	private static void reload(CommandSender sender) {
		if (!PermissionsHelper.isAdmin(sender)) {
			ChatHandler.sendError(sender, MessageKey.ADMIN_PERMISSION_REQUIRED);
			return;
		}

		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin == null || !plugin.isEnabled() || Airdrop.isShuttingDown()) {
			ChatHandler.sendError(sender, MessageKey.ERROR_RELOAD_UNAVAILABLE);
			return;
		}

		ChatHandler.send(sender, MessageKey.SYSTEM_RELOAD_STARTED);
		plugin.reloadConfiguration().whenComplete((result, failure) -> {
			if (Airdrop.isShuttingDown() || Airdrop.getPluginInstance() != plugin) {
				return;
			}
			if (failure != null || result == null) {
				Throwable cause = failure == null
						? new IllegalStateException("Reload completed without a result")
						: unwrap(failure);
				AirdropLogger.log(Level.WARNING, "Configuration reload failed; retaining live state", cause);
				ChatHandler.sendError(sender, MessageKey.ERROR_RELOAD_FAILED_RETAINED);
				return;
			}
			sendReloadResult(sender, result);
		});
	}

	private static void sendReloadResult(CommandSender sender, EconomyProviderRefreshResult result) {
		switch (result.outcome()) {
			case ACTIVE -> ChatHandler.send(sender, MessageKey.SYSTEM_RELOAD_ECONOMY_ACTIVE,
					Map.of("provider", result.providerName()));
			case DISABLED -> ChatHandler.send(sender, MessageKey.SYSTEM_RELOAD_ECONOMY_DISABLED);
			case UNAVAILABLE -> ChatHandler.sendError(sender, MessageKey.SYSTEM_RELOAD_ECONOMY_UNAVAILABLE);
		}
	}

	private static Throwable unwrap(Throwable failure) {
		Throwable current = failure;
		while ((current instanceof CompletionException || current instanceof ExecutionException)
				&& current.getCause() != null) {
			current = current.getCause();
		}
		return current;
	}
}
