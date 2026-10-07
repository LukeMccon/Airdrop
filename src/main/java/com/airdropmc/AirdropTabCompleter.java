package com.airdropmc;

import com.airdropmc.commands.PackageTabCompletion;
import com.airdropmc.commands.TabCompletionFilter;
import com.airdropmc.commands.TargetedDropCommand;
import com.airdropmc.config.ConfigKeys;
import org.bukkit.Bukkit;
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
			boolean admin = PermissionsHelper.isAdmin(commandSender);
			return args.length == 1
					? TabCompletionFilter.filter(
							admin
									? List.of(AirdropCommandNames.STATUS, AirdropCommandNames.VERSION)
									: List.of(AirdropCommandNames.VERSION),
							args[0])
					: List.of();
		}

		if (args.length == 1) {
			boolean admin = PermissionsHelper.isAdmin(commandSender);
			List<String> suggestions = new ArrayList<>(
					AirdropCommandNames.visibleTo(admin, commandSender instanceof Player));
			if (TargetedDropCommand.canSend(commandSender)) {
				suggestions.add(AirdropCommandNames.SEND);
			}
			if (commandSender instanceof Player player) {
				PackageManager.getPackages().stream()
						.filter(packageName -> PermissionsHelper.hasPermission(player, packageName))
						.forEach(suggestions::add);
			}
			return TabCompletionFilter.filter(suggestions.stream().distinct().toList(), args[0]);
		}

		if (TargetedDropCommand.isTargeted(args[0])) {
			if (!TargetedDropCommand.canSend(commandSender)) {
				return List.of();
			}
			if (args.length == 2) {
				return TabCompletionFilter.filter(PackageManager.getPackages().stream()
						.filter(name -> !(commandSender instanceof Player player) || PermissionsHelper.hasPermission(player, name))
						.toList(), args[1]);
			}
			if (!PackageManager.has(args[1]) || commandSender instanceof Player player
					&& !PermissionsHelper.hasPermission(player, args[1])) {
				return List.of();
			}
			if (args.length == 3) {
				return TabCompletionFilter.filter(Bukkit.getOnlinePlayers().stream()
						.filter(player -> !(commandSender instanceof Player viewer) || viewer.canSee(player))
						.filter(player -> !ConfigKeys.requiresGiftRecipientPermission() || PermissionsHelper.hasPermission(player, args[1]))
						.map(Player::getName).toList(), args[2]);
			}
			if (args.length == 5 && TargetedDropCommand.isCoordinate(args[2]) && TargetedDropCommand.isCoordinate(args[3])) {
				return TabCompletionFilter.filter(Bukkit.getWorlds().stream().map(org.bukkit.World::getName).toList(), args[4]);
			}
			return List.of();
		}
		if (AirdropCommandNames.PACKAGE.equals(args[0])) {
			return new PackageTabCompletion().onTabComplete(commandSender, command, alias, args);
		}
		return List.of();
	}
}
