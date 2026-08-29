package com.airdropmc;

import com.airdropmc.commands.PackageTabCompletion;
import com.airdropmc.commands.TabCompletionFilter;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.packages.PackageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class AirdropTabCompleter implements TabCompleter {

	@Override
	public List<String> onTabComplete(CommandSender commandSender, Command command, String alias, String[] args) {
		if (!Airdrop.isReady()) {
			return args.length == 1
					? TabCompletionFilter.filter(List.of(AirdropCommandNames.VERSION), args[0])
					: List.of();
		}

		if (args.length == 1) {
			boolean admin = PermissionsHelper.isAdmin(commandSender);
			List<String> suggestions = new ArrayList<>(
					AirdropCommandNames.visibleTo(admin, commandSender instanceof Player));
			if (commandSender instanceof Player player) {
				PackageManager.getPackages().stream()
						.filter(packageName -> PermissionsHelper.hasPermission(player, packageName))
						.forEach(suggestions::add);
			}
			return TabCompletionFilter.filter(suggestions, args[0]);
		}

		if (AirdropCommandNames.PACKAGE.equals(args[0])) {
			return new PackageTabCompletion().onTabComplete(commandSender, command, alias, args);
		}
		return List.of();
	}
}
