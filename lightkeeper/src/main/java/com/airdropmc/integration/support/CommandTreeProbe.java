package com.airdropmc.integration.support;

import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pinned Paper 1.21.11 fixture: inspect the actual outgoing tree and exercise its packet encoder. */
final class CommandTreeProbe implements Listener {
	private final Plugin plugin;
	private final Set<UUID> requested = new HashSet<>();

	CommandTreeProbe(Plugin plugin) {
		this.plugin = plugin;
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}

	void inspect(String playerName) {
		var player = java.util.Objects.requireNonNull(Bukkit.getPlayerExact(playerName));
		requested.add(player.getUniqueId());
		player.updateCommands();
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void outgoingTree(AsyncPlayerSendCommandsEvent<CommandSourceStack> event) {
		if (event.isAsynchronous() || !requested.remove(event.getPlayer().getUniqueId())) {
			return;
		}
		try {
			var root = event.getCommandNode();
			Map<CommandNode<CommandSourceStack>, Boolean> providers = new IdentityHashMap<>();
			for (String alias : List.of("airdrop", "ad", "drop", "airdrop:airdrop")) {
				CommandNode<CommandSourceStack> command = child(root, alias);
				if (command.getRedirect() != null) {
					command = command.getRedirect();
				}
				var pkg = child(child(command, "send"), "package");
				var player = child(child(pkg, "to"), "player");
				var x = child(child(pkg, "at"), "x");
				var z = child(x, "z");
				var world = child(z, "world");
				for (var expected : Map.of(x, false, z, false, player, true, world, true).entrySet()) {
					if (!(expected.getKey() instanceof ArgumentCommandNode<?, ?> argument)
							|| (argument.getCustomSuggestions() != null) != expected.getValue()) {
						throw new IllegalStateException("Unexpected suggestion provider for " + alias
								+ " send " + expected.getKey().getName());
					}
					providers.put(expected.getKey(), expected.getValue());
				}
				if (pkg.getChild("player") != null || pkg.getChild("x") != null) {
					throw new IllegalStateException("Bare send destination remains in client tree");
				}
				plugin.getLogger().info("AIRDR_SUGGESTIONS player=" + event.getPlayer().getName()
						+ " alias=" + alias + " x=client z=client player=server world=server");
			}
			int bytes = encodePacket(root, providers);
			plugin.getLogger().info("AIRDR_TREE player=" + event.getPlayer().getName()
					+ " paths=send/package/to/player,send/package/at/x/z/world aliases=airdrop,ad,drop,airdrop:airdrop"
					+ " packetSuggestions=verified bytes=" + bytes);
			// Unlike CommandMap.tabComplete, retain and inspect the actual suggestion tooltips.
			var dispatcher = dispatcher();
			for (String input : List.of("airdrop send premium ", "airdrop send premium to ",
					"airdrop send premium at ", "airdrop send premium at +1 ",
					"airdrop send premium at +1 1e2 ")) {
				var source = source(event.getPlayer());
				logSmartUsage("outgoing", new CommandDispatcher<>(root), source, event.getPlayer().getName(), input);
				logSmartUsage("native", dispatcher, source, event.getPlayer().getName(), input);
				var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(input, source)).join();
				for (var hint : suggestions.getList()) {
					plugin.getLogger().info("AIRDR_HINT player=" + event.getPlayer().getName() + " input=" + input
							+ " text=" + hint.getText() + " tooltip=" + (hint.getTooltip() == null ? "NONE" : hint.getTooltip().getString()));
				}
			}
		} catch (ReflectiveOperationException | RuntimeException failure) {
			throw new IllegalStateException("Native command tree probe failed", failure);
		}
	}

	private void logSmartUsage(String tree, CommandDispatcher<CommandSourceStack> dispatcher,
			CommandSourceStack source, String player, String input) {
		// Match the client's usage lookup at the current suggestion cursor.
		var parsed = dispatcher.parse(input, source);
		var cursor = parsed.getContext().findSuggestionContext(input.length());
		plugin.getLogger().info("AIRDR_USAGE player=" + player + " tree=" + tree + " input=" + input
				+ "node=" + cursor.parent.getName() + " usage="
				+ String.join("|", dispatcher.getSmartUsage(cursor.parent, source).values()));
	}

	private static <S> CommandNode<S> child(CommandNode<S> parent, String name) {
		return java.util.Objects.requireNonNull(parent.getChild(name), "Missing typed node " + name);
	}

	@SuppressWarnings("unchecked")
	static CommandDispatcher<CommandSourceStack> dispatcher() throws ReflectiveOperationException {
		// PaperCommands exposes a registration mirror whose parsing maps are empty.
		// Execute/complete against the live dispatcher used by CraftServer instead.
		Class<?> serverType = Class.forName("net.minecraft.server.MinecraftServer");
		Object server = serverType.getMethod("getServer").invoke(null);
		Object commands = serverType.getMethod("getCommands").invoke(server);
		return (CommandDispatcher<CommandSourceStack>) Class.forName("net.minecraft.commands.Commands")
				.getMethod("getDispatcher").invoke(commands);
	}

	static CommandSourceStack source(CommandSender sender) throws ReflectiveOperationException {
		return (CommandSourceStack) Class.forName("org.bukkit.craftbukkit.command.VanillaCommandWrapper")
				.getMethod("getListener", CommandSender.class).invoke(null, sender);
	}

	private static int encodePacket(RootCommandNode<CommandSourceStack> root,
			Map<CommandNode<CommandSourceStack>, Boolean> providers) throws ReflectiveOperationException {
		Class<?> packetType = Class.forName("net.minecraft.network.protocol.game.ClientboundCommandsPacket");
		Class<?> inspectorType = Class.forName("net.minecraft.network.protocol.game.ClientboundCommandsPacket$NodeInspector");
		var inspector = Class.forName("net.minecraft.commands.Commands").getDeclaredField("COMMAND_NODE_INSPECTOR");
		inspector.setAccessible(true);
		Object packet = packetType.getConstructor(RootCommandNode.class, inspectorType).newInstance(root, inspector.get(null));
		Class<?> bufferType = Class.forName("io.netty.buffer.ByteBuf");
		Object buffer = Class.forName("io.netty.buffer.Unpooled").getMethod("buffer").invoke(null);
		try {
			Object friendly = Class.forName("net.minecraft.network.FriendlyByteBuf").getConstructor(bufferType).newInstance(buffer);
			Object codec = packetType.getField("STREAM_CODEC").get(null);
			Class.forName("net.minecraft.network.codec.StreamEncoder").getMethod("encode", Object.class, Object.class)
					.invoke(codec, friendly, packet);
			int bytes = (int) bufferType.getMethod("readableBytes").invoke(buffer);
			Object decoded = Class.forName("net.minecraft.network.codec.StreamDecoder").getMethod("decode", Object.class)
					.invoke(codec, friendly);
			// The decoded entries retain the encoder's node indices, including alias redirects.
			var enumerate = packetType.getDeclaredMethod("enumerateNodes", RootCommandNode.class);
			enumerate.setAccessible(true);
			var indices = (Map<?, ?>) enumerate.invoke(null, root);
			var entriesField = packetType.getDeclaredField("entries");
			entriesField.setAccessible(true);
			var entries = (List<?>) entriesField.get(decoded);
			var flags = Class.forName("net.minecraft.network.protocol.game.ClientboundCommandsPacket$Entry")
					.getDeclaredField("flags");
			flags.setAccessible(true);
			var customSuggestions = packetType.getDeclaredField("FLAG_CUSTOM_SUGGESTIONS");
			customSuggestions.setAccessible(true);
			for (var expected : providers.entrySet()) {
				int index = (Integer) indices.get(expected.getKey());
				if (((flags.getInt(entries.get(index)) & customSuggestions.getByte(null)) != 0) != expected.getValue()) {
					throw new IllegalStateException("Unexpected packet suggestion flag for " + expected.getKey().getName());
				}
			}
			return bytes;
		} finally {
			bufferType.getMethod("release").invoke(buffer);
		}
	}
}
