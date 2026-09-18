package com.airdropmc.commands;

import com.airdropmc.packages.PackageManager;
import com.airdropmc.helpers.PermissionsHelper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PackageTabCompletion implements TabCompleter {
	private static final String CREATE = "create";
	private static final String DELETE = "delete";

	@Override
	public List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command,
			@NotNull String alias, String[] args) {

		String commandArg = args.length > 1 ? args[1] : "";
		boolean isAdmin = PermissionsHelper.isAdmin(commandSender);
		boolean canCreate = commandSender instanceof Player && isAdmin;

		switch (args.length) {
			case 2:
				List<String> commands = new ArrayList<>();
				if (isAdmin) {
					if (canCreate) {
						commands.add(CREATE);
					}
					commands.add(DELETE);
				}
				commands.addAll(PackageManager.getPackages());
				return TabCompletionFilter.filter(commands, args[1]);
			case 3:
				if (DELETE.equals(commandArg) && isAdmin) {
					return TabCompletionFilter.filter(PackageManager.getPackages(), args[2]);
				}
				return List.of();
			default:
				return List.of();
		}
	}
}
