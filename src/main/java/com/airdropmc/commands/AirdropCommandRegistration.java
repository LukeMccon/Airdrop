package com.airdropmc.commands;

import com.airdropmc.AirdropTabCompleter;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.MessageKey;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Native syntax hints only; CmdAirdrop remains the execution and validation authority. */
public final class AirdropCommandRegistration {
	private static final CmdAirdrop EXECUTOR = new CmdAirdrop();
	private static final AirdropTabCompleter COMPLETER = new AirdropTabCompleter();

	private AirdropCommandRegistration() {
	}

	public static void register(Commands commands) {
		commands.register(tree(), "Call in an airdrop!", List.of("drop", "ad"));
	}

	static LiteralCommandNode<CommandSourceStack> tree() {
		var root = Commands.literal("airdrop").executes(AirdropCommandRegistration::execute);
		// No permission/readiness predicates: they would prune syntax during startup and
		// bypass our localized errors. In particular, /airdrop send can order package 'send'.
		var send = new SendLiteral(Commands.literal("send").executes(AirdropCommandRegistration::execute));
		// Both branches accept raw tokens. A single numeric token is still a player
		// name; only a second destination token selects coordinate delivery in CmdAirdrop.
		send.addChild(token("package")
				.then(token("x").then(token("z").then(token("world", StringArgumentType.greedyString()).then(remainder()))))
				// x supplies both player/X choices. Even an empty custom provider becomes
				// ask_server on the wire, issuing a second request that cancels the first.
				.then(Commands.argument("player", new RawToken(StringArgumentType.word())).executes(AirdropCommandRegistration::execute))
				.build());
		root.then(send);
		// Keep unrelated commands and malformed/extra arguments on the existing router.
		root.then(token("package_or_command").then(remainder()));
		return root.build();
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> token(String name) {
		return token(name, StringArgumentType.word());
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> token(String name, ArgumentType<String> nativeType) {
		return Commands.argument(name, new RawToken(nativeType))
				.suggests((context, builder) -> suggest(context, builder, false))
				.executes(AirdropCommandRegistration::execute);
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> remainder() {
		return Commands.argument("arguments", StringArgumentType.greedyString())
				.suggests((context, builder) -> suggest(context, builder, false))
				.executes(AirdropCommandRegistration::execute);
	}

	private static int execute(CommandContext<CommandSourceStack> context) {
		String input = argumentInput(context, context.getInput());
		// Match BukkitCommandNode's StringUtils.split(input, ' ') for native function
		// calls too: discard empty space-separated tokens, not raw coordinate text.
		String[] args = java.util.Arrays.stream(input.split(" ")).filter(token -> !token.isEmpty()).toArray(String[]::new);
		EXECUTOR.execute(context.getSource().getSender(), args);
		return 1;
	}

	private static String argumentInput(CommandContext<CommandSourceStack> context, String input) {
		// Suggestions receive the outer context, execution the redirected child. Follow
		// only redirects before the cursor, then skip this command's parsed root token.
		while (context.getChild() != null && context.getChild().getRange().getStart() <= input.length()) {
			context = context.getChild();
		}
		int start = context.getRootNode() instanceof RootCommandNode<?>
				? context.getNodes().getFirst().getRange().getEnd() + 1
				: context.getRange().getStart(); // An alias redirect already consumed the root.
		return input.substring(Math.min(start, input.length()));
	}

	private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context,
			SuggestionsBuilder builder, boolean sendLiteral) {
		String input = builder.getInput();
		String[] args = argumentInput(context, input).split(" ", -1);
		// A greedy fallback starts before the current word; replace only the final token.
		SuggestionsBuilder current = builder.createOffset(input.lastIndexOf(' ') + 1);
		var sender = context.getSource().getSender();
		for (String suggestion : COMPLETER.complete(sender, args)) {
			if (args.length == 1 && suggestion.equals("send") != sendLiteral) {
				continue;
			}
			// The client's local send literal has no tooltip; match it so the merge deduplicates.
			MessageKey tooltip = (args.length > 1 && args.length <= 3 && args[0].equals("send"))
					? MessageKey.COMMANDS_SEND_USAGE
					: args.length >= 3 && args[0].equals("send")
							? sender instanceof Player ? MessageKey.COMMANDS_HELP_SEND_COORDINATES
									: MessageKey.COMMANDS_HELP_SEND_CONSOLE
							: null;
			if (tooltip == null) {
				current.suggest(suggestion);
			} else {
				current.suggest(suggestion, MessageComponentSerializer.message().serialize(
						LegacyComponentSerializer.legacySection().deserialize(ChatHandler.get(tooltip))));
			}
		}
		return current.buildFuture();
	}

	/** Paper rebuilds literals on registration; keep server-side filtering through that copy. */
	private static final class SendLiteral extends LiteralCommandNode<CommandSourceStack> {
		private SendLiteral(LiteralArgumentBuilder<CommandSourceStack> builder) {
			super(builder.getLiteral(), builder.getCommand(), builder.getRequirement(), builder.getRedirect(),
					builder.getRedirectModifier(), builder.isFork());
			builder.getArguments().forEach(this::addChild);
		}

		@Override
		public LiteralArgumentBuilder<CommandSourceStack> createBuilder() {
			return new LiteralArgumentBuilder<CommandSourceStack>(getLiteral()) {
				@Override
				public LiteralCommandNode<CommandSourceStack> build() {
					return new SendLiteral(this);
				}
			}.executes(getCommand()).requires(getRequirement()).forward(getRedirect(), getRedirectModifier(), isFork());
		}

		@Override
		public CompletableFuture<Suggestions> listSuggestions(CommandContext<CommandSourceStack> context,
				SuggestionsBuilder builder) {
			return suggest(context, builder, true);
		}
	}

	/** Native words preserve branch hints; the terminal world needs greedy wire parsing for Unicode/punctuation. */
	private static final class RawToken implements CustomArgumentType<String, String> {
		private final ArgumentType<String> nativeType;

		private RawToken(ArgumentType<String> nativeType) {
			this.nativeType = nativeType;
		}

		@Override
		public String parse(StringReader reader) {
			// Brigadier consumes only one separator; native function calls may have more.
			while (reader.canRead() && reader.peek() == ' ') {
				reader.skip();
			}
			int start = reader.getCursor();
			while (reader.canRead() && reader.peek() != ' ') {
				reader.skip();
			}
			return reader.getString().substring(start, reader.getCursor());
		}

		@Override
		public ArgumentType<String> getNativeType() {
			return nativeType;
		}
	}
}
