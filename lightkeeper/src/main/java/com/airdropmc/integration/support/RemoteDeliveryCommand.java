package com.airdropmc.integration.support;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Barrel;
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
			if (args[0].equals("prepare") || args[0].equals("prepare-explosion")) {
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
					if (args[0].equals("prepare-explosion")) {
						for (int dx = -2; dx <= 2; dx++) {
							for (int dz = -2; dz <= 2; dz++) {
								world.getBlockAt(x + dx, 80, z + dz).setType(Material.BEDROCK);
							}
						}
					} else {
						world.getBlockAt(x, 80, z).setType(Material.STONE);
					}
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
						+ " level=" + (loaded ? world.getChunkAt(x >> 4, z >> 4).getLoadLevel() : "UNLOADED")
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
			} else if (args[0].equals("unload")) {
				boolean unloaded = world.unloadChunk(x >> 4, z >> 4);
				sender.sendMessage(marker + " status=OK unloaded=" + unloaded
						+ " loaded=" + world.isChunkLoaded(x >> 4, z >> 4)
						+ " players=" + world.getPlayers().size());
			} else if (args[0].equals("send-free")) {
				// Scenario lane has no permissions provider; scope the explicit console exemption to this send.
				var console = Bukkit.getConsoleSender();
				var exemption = console.addAttachment(airdrop, "airdrop.cost.bypass", true);
				try {
					Objects.requireNonNull(Bukkit.getPluginCommand("airdrop")).execute(console, "airdrop",
							new String[]{"send", "premium", Integer.toString(x), Integer.toString(z), world.getName()});
				} finally {
					console.removeAttachment(exemption);
				}
				sender.sendMessage(marker + " status=OK");
			} else if (args[0].equals("inspect-free")) {
				if (!world.isChunkLoaded(x >> 4, z >> 4)) {
					throw new IllegalStateException("Free crate inspection requires its retained chunk");
				}
				Class<?> manager = airdrop.getClass().getClassLoader().loadClass("com.airdropmc.helpers.CrateManager");
				Object crate = Objects.requireNonNull(manager.getMethod("getCrate", Location.class)
						.invoke(null, new Location(world, x, 81, z)), "free crate must remain tracked");
				Barrel barrel = (Barrel) world.getBlockAt(x, 81, z).getState();
				sender.sendMessage(marker + " status=OK paid=" + crate.getClass().getMethod("isPaid").invoke(crate)
						+ " persistedPaid=" + (crate.getClass().getMethod("readPaidPersistence", Barrel.class).invoke(null, barrel) != null)
						+ " crateId=" + crate.getClass().getMethod("getCrateId").invoke(crate)
						+ " deadline=" + crate.getClass().getMethod("getExpiresAtMillis").invoke(crate));
			} else if (args[0].equals("cleanup")) {
				Class<?> manager = airdrop.getClass().getClassLoader().loadClass("com.airdropmc.helpers.CrateManager");
				manager.getMethod("removeCratesInChunk", org.bukkit.Chunk.class)
						.invoke(null, world.getChunkAt(x >> 4, z >> 4));
				world.save();
				sender.sendMessage(marker + " status=OK");
			} else if (args[0].equals("explode-free")) {
				var rule = org.bukkit.GameRules.BLOCK_EXPLOSION_DROP_DECAY;
				boolean decay = Boolean.TRUE.equals(world.getGameRuleValue(rule));
				var loot = new java.util.TreeMap<String, Integer>();
				try {
					world.setGameRule(rule, false);
					world.createExplosion(x + 0.5, 81.5, z + 0.5, 2.0f, false, true);
					for (var entity : world.getNearbyEntities(new Location(world, x, 81, z), 6, 6, 6)) {
						if (entity instanceof org.bukkit.entity.Item item) {
							var stack = item.getItemStack();
							loot.merge(stack.getType().name(), stack.getAmount(), Integer::sum);
						}
					}
				} finally {
					world.setGameRule(rule, decay);
				}
				sender.sendMessage(marker + " status=OK loot=" + loot);
			} else return false;
		} catch (ReflectiveOperationException | RuntimeException failure) {
			sender.sendMessage(marker + " status=FAILED detail=" + failure);
		}
		return true;
	}
}
