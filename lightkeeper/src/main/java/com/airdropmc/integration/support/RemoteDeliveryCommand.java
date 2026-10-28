package com.airdropmc.integration.support;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
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
			} else if (args[0].equals("holder-loss")) {
				holderLoss(sender, marker, world, x, z, airdrop);
			} else if (args[0].equals("observe")) {
				boolean loaded = world.isChunkLoaded(x >> 4, z >> 4);
				long auxiliaries = world.getEntities().stream()
						.filter(entity -> entity.getLocation().getBlockX() >> 4 == x >> 4)
						.filter(entity -> entity.getLocation().getBlockZ() >> 4 == z >> 4)
						.filter(entity -> entity instanceof org.bukkit.entity.Chicken || entity instanceof org.bukkit.entity.Slime)
						.count();
				long leadItems = world.getEntities().stream()
						.filter(entity -> entity instanceof Item item && item.getItemStack().getType() == Material.LEAD)
						.count();
				sender.sendMessage(marker + " status=OK loaded=" + loaded
						+ " level=" + (loaded ? world.getChunkAt(x >> 4, z >> 4).getLoadLevel() : "UNLOADED")
						+ " generated=" + world.isChunkGenerated(x >> 4, z >> 4)
						+ " tickets=" + world.getPluginChunkTickets(x >> 4, z >> 4).stream().filter(airdrop::equals).count()
						+ " auxiliaries=" + auxiliaries + " leadItems=" + leadItems);
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

	/** AIRDR-101: let native entity ticking break real leashes after their holders disappear. */
	private void holderLoss(CommandSender sender, String marker, World world, int x, int z, Plugin plugin)
			throws ReflectiveOperationException {
		ClassLoader loader = plugin.getClass().getClassLoader();
		Class<?> optionsType = loader.loadClass("com.airdropmc.config.DropOptions");
		Object options = optionsType.getMethod("createDefault").invoke(null);
		optionsType.getMethod("withChickenCount", int.class).invoke(options, 1);
		Class<?> systemType = loader.loadClass("com.airdropmc.ParachuteSystem");
		Object system = systemType.getConstructor(World.class, optionsType).newInstance(world, options);
		var entities = new ArrayList<Entity>();
		Listener listener = new Listener() {};
		Runnable cleanup = () -> {
			try {
				systemType.getMethod("cancel").invoke(system);
			} catch (ReflectiveOperationException failure) {
				throw new IllegalStateException(failure);
			} finally {
				HandlerList.unregisterAll(listener);
				entities.forEach(Entity::remove);
			}
		};
		boolean scheduled = false;
		try {
			Location location = new Location(world, x + 0.5, Math.min(200, world.getMaxHeight() - 32), z + 0.5);
			FallingBlock falling = world.spawnFallingBlock(location, Material.BARREL.createBlockData());
			entities.add(falling);
			falling.setDropItem(false);
			systemType.getMethod("initialize", Location.class, FallingBlock.class, plugin.getClass())
					.invoke(system, location, falling, plugin);
			Entity holder = falling.getPassengers().getFirst();
			var chickens = systemType.getDeclaredField("chickenParachutes");
			chickens.setAccessible(true);
			Chicken parachute = (Chicken) ((java.util.List<?>) chickens.get(system)).getFirst();
			if (!parachute.isLeashed()) throw new IllegalStateException("parachute leash was not attached");
			Slime controlHolder = world.spawn(location.clone().add(8, 1, 0), Slime.class);
			entities.add(controlHolder);
			Chicken control = world.spawn(location.clone().add(8, 2, 0), Chicken.class);
			entities.add(control);
			control.setAI(false);
			control.setGravity(false);
			if (!control.setLeashHolder(controlHolder)) throw new IllegalStateException("control leash was not attached");
			var events = new HashMap<java.util.UUID, String>();
			Bukkit.getPluginManager().registerEvent(EntityUnleashEvent.class, listener, EventPriority.MONITOR,
					(ignored, event) -> {
						EntityUnleashEvent unleash = (EntityUnleashEvent) event;
						events.put(unleash.getEntity().getUniqueId(), unleash.getReason() + ":" + unleash.isDropLeash());
					}, plugin);
			holder.remove();
			controlHolder.remove();
			// Drive the real leash tick directly so chunk activation/player tracking
			// cannot defer the holder-loss behavior this fixture is checking.
			var leashTick = Class.forName("net.minecraft.world.entity.Leashable").getMethod("tickLeash",
					Class.forName("net.minecraft.server.level.ServerLevel"), Class.forName("net.minecraft.world.entity.Entity"));
			Object level = world.getClass().getMethod("getHandle").invoke(world);
			for (Chicken chicken : new Chicken[]{parachute, control}) {
				leashTick.invoke(null, level, chicken.getClass().getMethod("getHandle").invoke(chicken));
			}
			Bukkit.getScheduler().runTaskLater(plugin, () -> {
				try {
					String counts = "";
					for (Chicken chicken : new Chicken[]{parachute, control}) {
						var leads = chicken.getNearbyEntities(3, 3, 3).stream()
								.filter(entity -> entity instanceof Item item && item.getItemStack().getType() == Material.LEAD).toList();
						entities.addAll(leads);
						counts += " " + (chicken == parachute ? "parachute" : "control") + "LeadItems="
								+ leads.stream().mapToInt(entity -> ((Item) entity).getItemStack().getAmount()).sum();
					}
					sender.sendMessage(marker + " status=OK parachuteEvent=" + events.get(parachute.getUniqueId())
							+ " controlEvent=" + events.get(control.getUniqueId()) + counts);
				} finally {
					cleanup.run();
				}
			}, 5L);
			scheduled = true;
		} finally {
			if (!scheduled) cleanup.run();
		}
	}
}
