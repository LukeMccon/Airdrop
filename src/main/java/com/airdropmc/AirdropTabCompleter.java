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
			if (commandSender.hasPermission("airdrop.grant")) {
				suggestions.add(AirdropCommandNames.GRANT);
			}
			if (commandSender instanceof Player player) {
				if (player.hasPermission("airdrop.gift")) {
					suggestions.add(AirdropCommandNames.GIFT);
				}
				PackageManager.getPackages().stream()
						.filter(packageName -> PermissionsHelper.hasPermission(player, packageName))
						.forEach(suggestions::add);
			}
			return TabCompletionFilter.filter(suggestions, args[0]);
		}

		if (TargetedDropCommand.isTargeted(args[0])) {
			boolean gift = AirdropCommandNames.GIFT.equals(args[0]);
			if (!commandSender.hasPermission("airdrop." + args[0])
					|| gift && !(commandSender instanceof Player)) {
				return List.of();
			}
			if (args.length == 2) {
				return TabCompletionFilter.filter(Bukkit.getOnlinePlayers().stream()
						.filter(player -> !(commandSender instanceof Player viewer) || viewer.canSee(player))
						.map(Player::getName).toList(), args[1]);
			}
			Player recipient = Bukkit.getPlayerExact(args[1]);
			if (args.length != 3 || recipient == null) {
				return List.of();
			}
			return TabCompletionFilter.filter(PackageManager.getPackages().stream()
					.filter(name -> !gift || PermissionsHelper.hasPermission((Player) commandSender, name))
					.filter(name -> !gift || !ConfigKeys.requiresGiftRecipientPermission()
							|| PermissionsHelper.hasPermission(recipient, name))
					.toList(), args[2]);
		}
		if (AirdropCommandNames.PACKAGE.equals(args[0])) {
			return new PackageTabCompletion().onTabComplete(commandSender, command, alias, args);
		}
		return List.of();
	}
}
