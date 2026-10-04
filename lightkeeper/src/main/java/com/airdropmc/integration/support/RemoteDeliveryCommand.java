package com.airdropmc.integration.support;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** AIRDR-73: generate test terrain separately from the command being verified. */
public final class RemoteDeliveryCommand implements CommandExecutor {
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof ConsoleCommandSender) || args.length != 5) return false;
		String marker = "AIRDR_73_REMOTE token=" + args[1] + " action=" + args[0];
		try {
			World world = Objects.requireNonNull(Bukkit.getWorld(args[2]));
			int x = Integer.parseInt(args[3]);
			int z = Integer.parseInt(args[4]);
			Plugin airdrop = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("Airdrop"));
			if (args[0].equals("prepare")) {
				var loads = new ArrayList<CompletableFuture<?>>();
				for (int cx = (x >> 4) - 2; cx <= (x >> 4) + 2; cx++) {
					for (int cz = (z >> 4) - 2; cz <= (z >> 4) + 2; cz++) {
						loads.add(world.getChunkAtAsync(cx, cz, true, false));
					}
				}
				CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> {
					if (failure != null) {
						sender.sendMessage(marker + " status=FAILED detail=" + failure);
						return;
					}
					world.getBlockAt(x, 80, z).setType(Material.STONE);
					world.save();
					sender.sendMessage(marker + " status=OK");
				});
			} else if (args[0].equals("observe")) {
				boolean loaded = world.isChunkLoaded(x >> 4, z >> 4);
				long auxiliaries = world.getEntities().stream()
						.filter(entity -> entity.getLocation().getBlockX() >> 4 == x >> 4)
						.filter(entity -> entity.getLocation().getBlockZ() >> 4 == z >> 4)
						.filter(entity -> entity instanceof org.bukkit.entity.Chicken || entity instanceof org.bukkit.entity.Slime)
						.count();
				sender.sendMessage(marker + " status=OK loaded=" + loaded
						+ " generated=" + world.isChunkGenerated(x >> 4, z >> 4)
						+ " tickets=" + world.getPluginChunkTickets(x >> 4, z >> 4).stream().filter(airdrop::equals).count()
						+ " auxiliaries=" + auxiliaries);
			} else if (args[0].equals("border")) {
				world.getWorldBorder().setSize(x);
				sender.sendMessage(marker + " status=OK");
			} else if (args[0].equals("generation")) {
				Object wrapper = airdrop.getClass().getMethod("getConfiguration").invoke(null);
				FileConfiguration config = (FileConfiguration) wrapper.getClass().getMethod("getConfig").invoke(wrapper);
				config.set("drop.remote-loading.generate-new-chunks", x != 0);
				sender.sendMessage(marker + " status=OK");
			} else return false;
		} catch (ReflectiveOperationException | RuntimeException failure) {
			sender.sendMessage(marker + " status=FAILED detail=" + failure);
		}
		return true;
	}
}
