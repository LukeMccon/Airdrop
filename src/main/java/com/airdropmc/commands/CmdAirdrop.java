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
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

public class CmdAirdrop implements CommandExecutor {
	private static final String CREATE = "create";
	private static final String DELETE = "delete";
	private static final String MODRINTH_URL = "https://modrinth.com/plugin/airdrop";

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
			@NotNull String label, String[] args) {

		if (args.length == 0) {
			sendHelp(sender);
			return true;
		}
		if (requiresPackageArgumentFeedback(args)) {
			PackageCommand.onCommand(sender, args);
			return true;
		}
		if (hasInvalidGenericArgumentCount(args)) {
			sendHelp(sender);
			return true;
		}

		if (AirdropCommandNames.VERSION.equals(args[0])) {
			ChatHandler.sendWithoutPrefix(sender, MessageKey.SYSTEM_VERSION_INFO, Map.of(
					"version", known(Airdrop.getVersion()),
					"api_version", known(Airdrop.getPaperApiVersion()),
					"plugin_version", known(Airdrop.getVersion()),
					"extension_api_version", known(Airdrop.getExtensionApiVersion()),
					"paper_version", known(Airdrop.getPaperApiVersion()),
					"java_version", known(Airdrop.getJavaCompatibilityVersion()),
					"docs_url", MODRINTH_URL));
			return true;
		}
		if (AirdropCommandNames.STATUS.equals(args[0])) {
			StatusCommand.onCommand(sender);
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

	private static void sendHelp(CommandSender sender) {
		ChatHandler.sendWithoutPrefix(sender, MessageKey.COMMANDS_HELP_HEADER, Map.of());
		if (sender instanceof Player) {
			ChatHandler.sendWithoutPrefix(sender, MessageKey.COMMANDS_HELP_DROP, Map.of());
			ChatHandler.sendWithoutPrefix(sender, MessageKey.COMMANDS_HELP_PACKAGES, Map.of());
		}
		ChatHandler.sendWithoutPrefix(sender, MessageKey.COMMANDS_HELP_PACKAGE, Map.of());
		ChatHandler.sendWithoutPrefix(sender, MessageKey.COMMANDS_HELP_VERSION, Map.of());

		if (!PermissionsHelper.isAdmin(sender)) {
			return;
		}
		if (sender instanceof Player) {
			ChatHandler.sendWithoutPrefix(sender, MessageKey.COMMANDS_HELP_ADMIN_CREATE, Map.of());
		}
		ChatHandler.sendWithoutPrefix(sender, MessageKey.COMMANDS_HELP_ADMIN_DELETE, Map.of());
		ChatHandler.sendWithoutPrefix(sender, MessageKey.COMMANDS_HELP_ADMIN_RELOAD, Map.of());
		ChatHandler.sendWithoutPrefix(sender, MessageKey.COMMANDS_HELP_ADMIN_STATUS, Map.of());
	}

	private static boolean hasInvalidGenericArgumentCount(String[] args) {
		if (!AirdropCommandNames.PACKAGE.equals(args[0])) {
			return args.length != 1;
		}
		if (args.length == 1 || CREATE.equals(args[1]) || DELETE.equals(args[1])) {
			return false;
		}
		return args.length != 2;
	}

	private static boolean requiresPackageArgumentFeedback(String[] args) {
		if (!AirdropCommandNames.PACKAGE.equals(args[0])) {
			return false;
		}
		if (args.length == 1) {
			return true;
		}
		return CREATE.equals(args[1]) && args.length != 4
				|| DELETE.equals(args[1]) && args.length != 3;
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

	private static String known(String value) {
		return value == null || value.isBlank() ? "unknown" : value;
	}
}
