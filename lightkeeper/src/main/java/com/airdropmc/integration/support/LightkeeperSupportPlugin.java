package com.airdropmc.integration.support;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class LightkeeperSupportPlugin extends JavaPlugin {
	@Override
	public boolean onCommand(
			CommandSender sender,
			Command command,
			String label,
			String[] args
	) {
		if (args.length != 5) {
			return false;
		}

		World world = Bukkit.getWorld(args[0]);
		if (world == null) {
			return false;
		}

		try {
			double x = Double.parseDouble(args[1]);
			double y = Double.parseDouble(args[2]);
			double z = Double.parseDouble(args[3]);
			float power = Float.parseFloat(args[4]);
			world.createExplosion(x, y, z, power, false, true);
			return true;
		} catch (NumberFormatException exception) {
			return false;
		}
	}
}
