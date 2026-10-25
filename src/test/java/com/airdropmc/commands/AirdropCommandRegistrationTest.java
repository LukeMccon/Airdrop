package com.airdropmc.commands;

import com.airdropmc.Airdrop;
import com.airdropmc.Config;
import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropRequestDescriptor;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.api.DropSource;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.controllers.DropController;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.internal.drop.DefaultDropHandle;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.packages.Package;
import com.airdropmc.packages.PackageManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.brigadier.PaperCommandsMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.lifecycle.event.LifecycleEventRunnerMock;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AirdropCommandRegistrationTest {
	private ServerMock server;
	private Plugin permissions;
	private PlayerMock sender;
	private YamlConfiguration config;
	private CommandDispatcher<CommandSourceStack> dispatcher;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		permissions = MockBukkit.createMockPlugin();
		server.getPluginManager().addPermission(new Permission("airdrop.send", PermissionDefault.TRUE));
		server.getPluginManager().addPermission(new Permission("airdrop.cost.bypass", PermissionDefault.FALSE));
		// Exercise the actual onLoad lifecycle hook, not a reconstructed test tree.
		var plugin = server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		assertFalse(plugin.getDescription().getCommands().containsKey("airdrop"));
		LifecycleEventRunnerMock.INSTANCE.callReloadableRegistrarEvent(LifecycleEvents.COMMANDS,
				PaperCommandsMock.INSTANCE, Plugin.class, ReloadableRegistrarEvent.Cause.INITIAL);
		dispatcher = PaperCommandsMock.INSTANCE.getDispatcherInternal();
		sender = server.addPlayer("Sender");
		sender.teleport(new Location(server.addSimpleWorld("hints_world"), 12.9, 64, -3.2));
		sender.addAttachment(permissions, "airdrop.package.starter", true);
		config = new YamlConfiguration();
		setStatic("configuration", new Config(config));
		setStatic("ready", true);
		ChatHandler.init(null);
		PackageManager.publishPackages(Map.of("starter", new Package("Starter", 0, List.of())));
	}

	@AfterEach
	void tearDown() throws Exception {
		setStatic("ready", false);
		setStatic("configuration", null);
		PackageManager.clear();
		ChatHandler.init(null);
		MockBukkit.unmock();
	}

	@Test
	void coordinateUsageDoesNotWaitForAnEmptyServerReply() {
		var connection = new ClientSuggestions();
		var nativeRoot = new RootCommandNode<CommandSourceStack>();
		nativeRoot.addChild(nativeCopy(dispatcher.getRoot().getChild("airdrop"), connection::request));
		nativeRoot.addChild(Commands.literal("ad").redirect(nativeRoot.getChild("airdrop")).build());
		var client = new CommandDispatcher<>(nativeRoot);
		for (String input : List.of("airdrop send Starter at ", "airdrop send Starter at 12 ",
				"ad send Starter at ", "ad send Starter at 12 ")) {
			var suggestions = client.getCompletionSuggestions(client.parse(input, source(sender)));
			assertTrue(suggestions.isDone(), "Coordinate usage must resolve locally: Paper suppresses empty replies");
			assertTrue(suggestions.join().isEmpty());
		}
		assertEquals(0, connection.requests, "Coordinate axes must not use minecraft:ask_server");
	}

	@Test
	void coordinateEntryExposesRequiredUsageInsteadOfNumericCompletions() {
		var nativeRoot = new RootCommandNode<CommandSourceStack>();
		nativeRoot.addChild(nativeCopy(dispatcher.getRoot().getChild("airdrop"),
				(context, builder) -> Suggestions.empty()));
		var client = new CommandDispatcher<>(nativeRoot);
		for (var entry : Map.of("airdrop send Starter at ", "<x> <z> [<world>]",
				"airdrop send Starter at 12 ", "<z> [<world>]").entrySet()) {
			String input = entry.getKey();
			assertEquals(List.of(), texts(sender, input), "Value suggestions would hide native usage");
			var context = client.parse(input, source(sender)).getContext().findSuggestionContext(input.length());
			assertEquals(List.of(entry.getValue()), List.copyOf(client.getSmartUsage(context.parent, source(sender)).values()));
		}
	}

	@Test
	void destinationModesAreTheOnlyChoicesAfterPackage() {
		server.addPlayer("Recipient");
		assertEquals(List.of("at", "to"), texts(sender, "airdrop send Starter "));
		assertEquals(List.of("Recipient"), texts(sender, "airdrop send Starter to Re"));
		assertEquals(List.of(), texts(sender, "airdrop send Starter at "));
	}

	@Test
	void lifecycleRegistersBothTypedBranchesAndAliasesWithoutLegacyRoot() {
		for (String root : List.of("airdrop", "ad", "drop", "airdrop:airdrop", "airdrop:ad", "airdrop:drop")) {
			assertNotNull(dispatcher.getRoot().getChild(root), root);
		}
		var pkg = dispatcher.getRoot().getChild("airdrop").getChild("send").getChild("package");
		assertNotNull(pkg.getChild("to").getChild("player"));
		var world = pkg.getChild("at").getChild("x").getChild("z").getChild("world");
		assertNotNull(world);
		var argument = (ArgumentCommandNode<?, ?>) pkg.getChild("at").getChild("x");
		var nativeType = ((CustomArgumentType<?, ?>) argument.getType()).getNativeType();
		assertEquals(StringArgumentType.StringType.SINGLE_WORD, ((StringArgumentType) nativeType).getType());
	}

	@ParameterizedTest
	@ValueSource(strings = {"airdrop", "ad", "drop", "airdrop:airdrop", "airdrop:ad", "airdrop:drop"})
	void aliasesDispatchRawTokensUsingSenderNotExecutor(String root) throws Exception {
		try (var target = mockStatic(TargetedDropCommand.class)) {
			target.when(() -> TargetedDropCommand.isTargeted("send")).thenReturn(true);
			execute(sender, root + " send Starter at +1 1e2 hints_world");
			target.verify(() -> TargetedDropCommand.onCommand(sender,
					new String[]{"send", "Starter", "at", "+1", "1e2", "hints_world"}));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"airdrop", "ad", "drop", "airdrop:airdrop", "airdrop:ad", "airdrop:drop", "linked"})
	void redirectsPreserveRawArgumentsAndAbsoluteCompletionRanges(String root) throws Exception {
		registerExecuteRedirects();
		var airdrop = dispatcher.getRoot().getChild("airdrop");
		dispatcher.register(Commands.literal("linked").executes(airdrop.getCommand()).redirect(airdrop));
		sender.setOp(true);
		for (String prefix : List.of("", "execute run ", "execute as Sender run ",
				"execute run execute as Sender run ")) {
			String command = prefix + root;
			try (var target = mockStatic(TargetedDropCommand.class)) {
				target.when(() -> TargetedDropCommand.isTargeted("send")).thenReturn(true);
				execute(sender, command + " send Starter to Alex");
				target.verify(() -> TargetedDropCommand.onCommand(sender,
						new String[]{"send", "Starter", "to", "Alex"}));
				execute(sender, command + " send Starter at +1 1e2 hints_world");
				target.verify(() -> TargetedDropCommand.onCommand(sender,
						new String[]{"send", "Starter", "at", "+1", "1e2", "hints_world"}));
			}
			try (var chat = mockStatic(ChatHandler.class)) {
				execute(sender, command);
				chat.verify(() -> ChatHandler.sendWithoutPrefix(sender, MessageKey.COMMANDS_HELP_HEADER, Map.of()));
			}
			for (var completion : Map.of(" se", "send", " send St", "Starter",
					" send Starter at 1 2 hints", "hints_world",
					" package price St", "Starter").entrySet()) {
				String input = command + completion.getKey();
				var results = suggestions(sender, input);
				assertEquals(List.of(completion.getValue()), results.stream().map(Suggestion::getText).toList(), input);
				assertEquals(input.lastIndexOf(' ') + 1, results.getFirst().getRange().getStart(), input);
				assertEquals(input.substring(0, input.lastIndexOf(' ') + 1) + completion.getValue(),
						results.getFirst().apply(input), input);
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"airdrop", "ad", "airdrop:airdrop", "execute run airdrop", "execute run ad"})
	void nativeExecutionIgnoresEmptySpaceTokensWithoutChangingRawArguments(String root) throws Exception {
		registerExecuteRedirects();
		try (var target = mockStatic(TargetedDropCommand.class); var pkg = mockStatic(PackageCommand.class)) {
			target.when(() -> TargetedDropCommand.isTargeted("send")).thenReturn(true);
			for (String arguments : List.of("send Starter at  +1 1e2 hints_world",
					"send  Starter at +1  1e2 hints_world", " send Starter at +1 1e2  hints_world ")) {
				execute(sender, root + " " + arguments);
			}
			target.verify(() -> TargetedDropCommand.onCommand(sender,
					new String[]{"send", "Starter", "at", "+1", "1e2", "hints_world"}), times(3));
			execute(sender, root + " send Starter to  123");
			target.verify(() -> TargetedDropCommand.onCommand(sender, new String[]{"send", "Starter", "to", "123"}));
			execute(sender, root + " send Starter at -1.25  .5 hints_world");
			target.verify(() -> TargetedDropCommand.onCommand(sender,
					new String[]{"send", "Starter", "at", "-1.25", ".5", "hints_world"}));
			execute(sender, root + " package  price Starter  1.25 ");
			pkg.verify(() -> PackageCommand.onCommand(sender, new String[]{"package", "price", "Starter", "1.25"}));
		}
		try (var chat = mockStatic(ChatHandler.class)) {
			execute(sender, root + "  ");
			chat.verify(() -> ChatHandler.sendWithoutPrefix(sender, MessageKey.COMMANDS_HELP_HEADER, Map.of()));
		}
		// Completion still needs the trailing empty slot to select the Z argument.
		assertEquals(List.of(), texts(sender, root + " send Starter at +1 "));
	}

	@Test
	void redirectedCompletionUsesCursorRatherThanTrailingInput() {
		registerExecuteRedirects();
		String beforeCursor = "execute run execute as Sender run airdrop:ad send St";
		String input = beforeCursor + " Recipient";
		var results = dispatcher.getCompletionSuggestions(dispatcher.parse(input, source(sender)),
				beforeCursor.length()).join().getList();
		assertEquals(List.of("Starter"), results.stream().map(Suggestion::getText).toList());
		assertEquals(input.replace("send St ", "send Starter "), results.getFirst().apply(input));
		assertEquals(beforeCursor.lastIndexOf(' ') + 1, results.getFirst().getRange().getStart());
		assertEquals(beforeCursor.length(), results.getFirst().getRange().getEnd());
	}

	private void registerExecuteRedirects() {
		var execute = Commands.literal("execute").build();
		execute.addChild(Commands.literal("run").redirect(dispatcher.getRoot()).build());
		execute.addChild(Commands.literal("as")
				.then(Commands.argument("executor", StringArgumentType.word()).redirect(execute)).build());
		dispatcher.getRoot().addChild(execute);
	}

	@ParameterizedTest
	@ValueSource(strings = {"123", "to", "at"})
	void explicitModesDistinguishPlayerNamesFromCoordinates(String targetName) throws Exception {
		var numeric = server.addPlayer(targetName);
		try (var drops = mockStatic(DropController.class)) {
			drops.when(() -> DropController.requestSendDrop(sender, numeric, "Starter",
					DropRequestOptions.defaults(), false)).thenReturn(pending());
			drops.when(() -> DropController.requestSendDrop(eq(sender), any(Location.class), eq("Starter"),
					eq(DropRequestOptions.defaults()))).thenReturn(pending());
			execute(sender, "airdrop send Starter to " + targetName);
			drops.verify(() -> DropController.requestSendDrop(sender, numeric, "Starter",
					DropRequestOptions.defaults(), false));
			execute(sender, "airdrop send Starter at 123 -4");
			drops.verify(() -> DropController.requestSendDrop(sender,
					new Location(sender.getWorld(), 123, sender.getWorld().getMaxHeight() - 1, -4),
					"Starter", DropRequestOptions.defaults()));
		}
	}

	@ParameterizedTest
	@CsvSource({"+1,1e2", "-12.5,.25", "29999999.9,-29999999.9", "-0,+0"})
	void acceptedCoordinatesKeepTheirMeaningAndExplicitWorld(String x, String z) throws Exception {
		var other = server.addSimpleWorld("other_world");
		try (var drops = mockStatic(DropController.class)) {
			drops.when(() -> DropController.requestSendDrop(eq(sender), any(Location.class), eq("Starter"),
					eq(DropRequestOptions.defaults()))).thenReturn(pending());
			execute(sender, "ad send Starter at " + x + " " + z + " other_world");
			drops.verify(() -> DropController.requestSendDrop(sender, new Location(other,
					Double.parseDouble(x), other.getMaxHeight() - 1, Double.parseDouble(z)),
					"Starter", DropRequestOptions.defaults()));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"NaN", "Infinity", "-Infinity", "30000000", "-30000000", "~1", "^2", "@a", "\"1\"", "bad"})
	void malformedCoordinatesReachLocalizedValidation(String invalid) throws Exception {
		try (var chat = mockStatic(ChatHandler.class); var drops = mockStatic(DropController.class)) {
			execute(sender, "airdrop send Starter at " + invalid + " 2");
			chat.verify(() -> ChatHandler.sendError(sender, MessageKey.ERROR_SEND_COORDINATES));
			drops.verifyNoInteractions();
		}
	}

	@Test
	void incompleteAndExtraArgumentsNeverRequestADrop() throws Exception {
		try (var chat = mockStatic(ChatHandler.class); var drops = mockStatic(DropController.class)) {
			for (String command : List.of("airdrop send", "airdrop send Starter")) {
				execute(sender, command);
			}
			chat.verify(() -> ChatHandler.sendError(sender, MessageKey.COMMANDS_SEND_USAGE), times(2));
			for (String input : List.of("airdrop send Starter at", "airdrop send Starter at 1",
					"airdrop send Starter at 1 2 hints_world extra", "airdrop send Starter to Sender extra")) {
				assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class, () -> execute(sender, input));
			}
			execute(sender, "airdrop send Starter at 1 2 absent");
			chat.verify(() -> ChatHandler.sendError(sender, MessageKey.ERROR_SEND_WORLD, Map.of("world", "absent")));
			execute(sender, "airdrop send Starter to missing");
			chat.verify(() -> ChatHandler.sendError(sender, MessageKey.ERROR_TARGETED_PLAYER, Map.of("player", "missing")));
			drops.verifyNoInteractions();
		}
	}

	@Test
	void sendGiftAndGrantPackagesAndDeniedSendSelfOrderStillWork() throws Exception {
		PackageManager.publishPackages(Map.of("send", new Package("send", 0, List.of()),
				"gift", new Package("gift", 0, List.of()), "grant", new Package("grant", 0, List.of())));
		sender.addAttachment(permissions, "airdrop.send", false);
		try (var drop = mockStatic(DropCommand.class); var chat = mockStatic(ChatHandler.class)) {
			for (String name : List.of("send", "gift", "grant")) {
				execute(sender, "ad " + name);
				drop.verify(() -> DropCommand.onCommand(sender, new String[]{name}));
			}
			execute(sender, "ad send send to Sender");
			chat.verify(() -> ChatHandler.sendError(sender, MessageKey.ERROR_TARGETED_PERMISSION,
					Map.of("permission", "airdrop.send")));
		}
	}

	@Test
	void rootWithoutArgumentsStillShowsHelp() throws Exception {
		try (var chat = mockStatic(ChatHandler.class)) {
			execute(sender, "airdrop");
			chat.verify(() -> ChatHandler.sendWithoutPrefix(sender, MessageKey.COMMANDS_HELP_HEADER, Map.of()));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "to Re"})
	void wireDestinationSuggestionsUseOneServerRequestAndComplete(String partial) throws Exception {
		server.addPlayer("Recipient");
		server.addPlayer("12");
		var connection = new ClientSuggestions();
		var root = new RootCommandNode<CommandSourceStack>();
		root.addChild(nativeCopy(dispatcher.getRoot().getChild("airdrop"), connection::request));
		var client = new CommandDispatcher<>(root);
		String input = "airdrop send Starter " + partial;
		var merged = client.getCompletionSuggestions(client.parse(input, source(sender)));
		connection.reply();
		assertTrue(connection.pending.isDone(), "The final server response arrived");
		assertTrue(merged.isDone(), "Client merge must finish after the response; requests=" + connection.requests);
		assertEquals(1, connection.requests, "Sibling arguments must not cancel the client's pending request");
		var choices = merged.get(1, TimeUnit.SECONDS).getList();
		assertEquals(texts(sender, input), choices.stream().map(Suggestion::getText).toList());
		assertEquals(0,
				choices.stream().filter(s -> s.getText().equals("12")).count());
		assertTrue(choices.stream().allMatch(s -> partial.isEmpty() ? s.getTooltip() == null : s.getTooltip() != null));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "s", "se"})
	void wireRootSuggestionsDeduplicateLocalSendLiteral(String partial) throws Exception {
		var connection = new ClientSuggestions();
		var root = new RootCommandNode<CommandSourceStack>();
		root.addChild(nativeCopy(dispatcher.getRoot().getChild("airdrop"), connection::request));
		var client = new CommandDispatcher<>(root);
		String input = "airdrop " + partial;
		var merged = client.getCompletionSuggestions(client.parse(input, source(sender)));
		connection.reply();
		var choices = merged.get(1, TimeUnit.SECONDS).getList();
		assertEquals(1, connection.requests);
		assertEquals(1, choices.stream().filter(s -> s.getText().equals("send")).count(), choices.toString());
		assertNull(choices.stream().filter(s -> s.getText().equals("send")).findFirst().orElseThrow().getTooltip());
	}

	@Test
	void nativeWordTreeKeepsExplicitModesAndNextCoordinateDiscoverable() {
		var nativeRoot = new com.mojang.brigadier.tree.RootCommandNode<CommandSourceStack>();
		nativeRoot.addChild(nativeCopy(dispatcher.getRoot().getChild("airdrop"),
				(context, builder) -> Suggestions.empty()));
		var client = new CommandDispatcher<>(nativeRoot);
		var pkg = nativeRoot.getChild("airdrop").getChild("send").getChild("package");
		String usage = String.join(", ", client.getAllUsage(pkg, source(sender), false));
		assertTrue(usage.contains("to") && usage.contains("<player>"), usage);
		assertTrue(usage.contains("at") && usage.contains("<x>") && usage.contains("<z>"), usage);
		for (String x : List.of("+1", "1e2", "123", "-1.25")) {
			String input = "airdrop send Starter at " + x + " ";
			var parse = client.parse(input, source(sender));
			assertTrue(parse.getExceptions().isEmpty(), x);
			assertNotNull(parse.getContext().findSuggestionContext(input.length()).parent.getChild("z"), x);
		}
	}

	@Test
	void separateModesKeepNumericPlayerAndCoordinateSuggestionsValid() {
		server.addPlayer("12");
		assertEquals(1, texts(sender, "airdrop send Starter to ").stream().filter("12"::equals).count());
		sender.teleport(new Location(sender.getWorld(), 30_000_000, 64, -30_000_000));
		assertFalse(texts(sender, "airdrop send Starter at ").contains("30000000"));
		assertEquals(List.of(), texts(sender, "airdrop send Starter at 1 "));
	}

	@Test
	void unrelatedCommandsAndGreedyCompletionKeepExistingRouter() throws Exception {
		try (var pkg = mockStatic(PackageCommand.class)) {
			execute(sender, "ad package price Starter 1.25");
			pkg.verify(() -> PackageCommand.onCommand(sender, new String[]{"package", "price", "Starter", "1.25"}));
		}
		sender.setOp(true);
		assertEquals(List.of("Starter"), texts(sender, "ad package price St"));
		var suggestion = suggestions(sender, "ad package price St").getFirst();
		assertEquals("ad package price Starter", suggestion.apply("ad package price St"));
	}

	@Test
	void hintsAreLiveAndFilteredWithUsefulTooltips() throws Exception {
		server.addPlayer("Recipient");
		assertEquals(List.of("Starter"), texts(sender, "ad send st"));
		assertEquals(List.of("at", "to"), texts(sender, "ad send Starter "));
		assertEquals(List.of("Recipient"), texts(sender, "ad send Starter to Re"));
		assertEquals(List.of(), texts(sender, "ad send Starter at "));
		assertEquals(List.of(), texts(sender, "ad send Starter at +1 "));
		assertTrue(texts(sender, "ad send Starter at +1 1e2 ").contains("hints_world"));
		assertEquals(List.of(), texts(sender, "ad send Starter at ~1 "));
		assertEquals(List.of(), texts(sender, "ad send Starter at 1 NaN "));
		var hints = suggestions(sender, "ad send Starter at 1 2 ");
		assertTrue(hints.stream().allMatch(hint -> hint.getTooltip() != null));
		assertTrue(hints.getFirst().getTooltip().getString().contains("<x> <z> [world]"));
		assertTrue(hints.stream().noneMatch(hint -> hint.getText().contains("<") || hint.getText().contains("~")));
		server.addSimpleWorld("new_world");
		assertEquals(List.of("new_world"), texts(sender, "ad send Starter at 1 2 new"));
		PackageManager.clear();
		assertEquals(List.of(), texts(sender, "ad send st"));
		assertEquals(List.of(), texts(sender, "ad send Starter "));
	}

	@Test
	void readinessNeverPrunesTreeAndPermissionChangesAreLive() throws Exception {
		setStatic("ready", false);
		assertEquals(List.of(), texts(sender, "airdrop send "));
		assertTrue(dispatcher.getRoot().getChild("airdrop").getChild("send").canUse(source(sender)));
		try (var chat = mockStatic(ChatHandler.class)) {
			execute(sender, "airdrop send Starter at 1 2");
			chat.verify(() -> ChatHandler.sendError(sender, MessageKey.ERROR_PLUGIN_NOT_READY));
			execute(sender, "airdrop version");
			chat.verify(() -> ChatHandler.sendWithoutPrefix(eq(sender), eq(MessageKey.SYSTEM_VERSION_INFO), anyMap()));
		}
		setStatic("ready", true);
		assertEquals(List.of("Starter"), texts(sender, "airdrop send "));
		var denial = sender.addAttachment(permissions, "airdrop.send", false);
		assertEquals(List.of(), texts(sender, "airdrop se"));
		assertEquals(List.of(), texts(sender, "airdrop send "));
		sender.removeAttachment(denial);
		assertEquals(List.of("send"), texts(sender, "airdrop se"));
	}

	@Test
	void paperLiteralRebuildPreservesServerSidePermissionAndReadinessFiltering() throws Exception {
		var original = dispatcher.getRoot().getChild("airdrop");
		var rebuiltRoot = original.createBuilder().build();
		for (var child : original.getChildren()) {
			// Paper's ApiMirrorRootNode reconstructs literal nodes through createBuilder().
			var rebuilt = child.createBuilder().build();
			child.getChildren().forEach(rebuilt::addChild);
			rebuiltRoot.addChild(rebuilt);
		}
		dispatcher = new CommandDispatcher<>();
		dispatcher.getRoot().addChild(rebuiltRoot);
		assertEquals(List.of("send"), texts(sender, "airdrop se"));
		var denial = sender.addAttachment(permissions, "airdrop.send", false);
		assertEquals(List.of(), texts(sender, "airdrop se"));
		sender.removeAttachment(denial);
		setStatic("ready", false);
		assertEquals(List.of(), texts(sender, "airdrop se"));
	}

	@Test
	void invisibleOrStrictlyIneligiblePlayersAreNeverSuggested() {
		var recipient = server.addPlayer("Recipient");
		config.set(ConfigKeys.GIFT_REQUIRE_RECIPIENT_PERMISSION, true);
		assertEquals(List.of(), texts(sender, "airdrop send Starter to Re"));
		recipient.addAttachment(permissions, "airdrop.package.starter", true);
		assertEquals(List.of("Recipient"), texts(sender, "airdrop send Starter to Re"));
		sender.hidePlayer(permissions, recipient);
		assertEquals(List.of(), texts(sender, "airdrop send Starter to Re"));
	}

	@Test
	void consoleUsesZeroExamplesRequiresWorldAndHonorsEffectiveDenials() throws Exception {
		var console = mock(ConsoleCommandSender.class);
		when(console.hasPermission("airdrop.send")).thenReturn(true);
		assertEquals(List.of(), texts(console, "airdrop send Starter at "));
		assertEquals(List.of(), texts(console, "airdrop send Starter at 1 "));
		try (var chat = mockStatic(ChatHandler.class)) {
			execute(console, "airdrop send Starter at 1 2");
			chat.verify(() -> ChatHandler.sendError(console, MessageKey.ERROR_SEND_CONSOLE_WORLD));
			when(console.getEffectivePermissions()).thenReturn(java.util.Set.of(
					new org.bukkit.permissions.PermissionAttachmentInfo(console, "airdrop.cost.bypass",
							new org.bukkit.permissions.PermissionAttachment(permissions, console), false)));
			assertEquals(List.of(), texts(console, "airdrop se"));
			execute(console, "airdrop send Starter at 1 2 hints_world");
			chat.verify(() -> ChatHandler.sendError(console, MessageKey.ERROR_SEND_CONSOLE_COST));
		}
		var custom = mock(CommandSender.class);
		when(custom.hasPermission("airdrop.send")).thenReturn(true);
		assertEquals(List.of(), texts(custom, "airdrop se"));
		when(custom.hasPermission("airdrop.cost.bypass")).thenReturn(true);
		assertEquals(List.of("send"), texts(custom, "airdrop se"));
	}

	/** Model Paper's wire tree: native types, plain literals, and custom providers as minecraft:ask_server. */
	private com.mojang.brigadier.tree.CommandNode<CommandSourceStack> nativeCopy(
			com.mojang.brigadier.tree.CommandNode<CommandSourceStack> node,
			SuggestionProvider<CommandSourceStack> askServer) {
		com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> builder;
		if (node instanceof ArgumentCommandNode<CommandSourceStack, ?> argument) {
			var type = argument.getType() instanceof CustomArgumentType<?, ?> custom
					? custom.getNativeType() : argument.getType();
			var nativeArgument = Commands.argument(node.getName(), type);
			if (argument.getCustomSuggestions() != null) {
				nativeArgument.suggests(askServer);
			}
			builder = nativeArgument;
		} else {
			builder = io.papermc.paper.command.brigadier.Commands.literal(node.getName());
		}
		if (node.getCommand() != null) {
			builder.executes(context -> 1);
		}
		for (var child : node.getChildren()) {
			builder.then(nativeCopy(child, askServer));
		}
		return builder.build();
	}

	/** Vanilla keeps one outstanding request and cancels it when another ask_server provider runs. */
	private final class ClientSuggestions {
		private CompletableFuture<Suggestions> pending;
		private String input;
		private int requests;

		private CompletableFuture<Suggestions> request(
				com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
				com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
			if (pending != null) {
				pending.cancel(false);
			}
			requests++;
			input = builder.getInput();
			pending = new CompletableFuture<>();
			return pending;
		}

		private void reply() {
			assertNotNull(pending, "A wire ask_server provider must have issued a request");
			pending.complete(dispatcher.getCompletionSuggestions(dispatcher.parse(input, source(sender))).join());
		}
	}

	private void execute(CommandSender sender, String command) throws Exception {
		assertEquals(1, dispatcher.execute(command, source(sender)));
	}

	private CommandSourceStack source(CommandSender sender) {
		var source = mock(CommandSourceStack.class);
		when(source.getSender()).thenReturn(sender);
		return source;
	}

	private List<Suggestion> suggestions(CommandSender sender, String input) {
		return dispatcher.getCompletionSuggestions(dispatcher.parse(input, source(sender))).join().getList();
	}

	private List<String> texts(CommandSender sender, String input) {
		return suggestions(sender, input).stream().map(Suggestion::getText).toList();
	}

	private DropHandle pending() {
		return new DefaultDropHandle(new DropRequestDescriptor(UUID.randomUUID(), DropSource.PLAYER,
				sender.getUniqueId(), "Starter", sender.getLocation()));
	}

	private static void setStatic(String name, Object value) throws Exception {
		var field = Airdrop.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(null, value);
	}
}
