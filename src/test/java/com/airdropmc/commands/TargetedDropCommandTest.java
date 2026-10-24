package com.airdropmc.commands;

import com.airdropmc.Airdrop;
import com.airdropmc.AirdropTabCompleter;
import com.airdropmc.Config;
import com.airdropmc.api.AirdropPackage;
import com.airdropmc.api.DeliveryStatus;
import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.DropRejection;
import com.airdropmc.api.DropRejectionReason;
import com.airdropmc.api.DropRequestDescriptor;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.api.DropSource;
import com.airdropmc.api.DropSpawnResult;
import com.airdropmc.api.FallingAirdropView;
import com.airdropmc.api.LandedAirdropView;
import com.airdropmc.api.PaymentStatus;
import com.airdropmc.api.ResolvedDropContext;
import com.airdropmc.api.ResolvedDropSettings;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.controllers.DropController;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.internal.drop.DefaultDropHandle;
import com.airdropmc.packages.Package;
import com.airdropmc.packages.PackageManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TargetedDropCommandTest {

	@org.junit.jupiter.api.io.TempDir
	java.nio.file.Path tempDir;
	private ServerMock server;
	private PlayerMock sender;
	private PlayerMock recipient;
	private YamlConfiguration configuration;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		server.getPluginManager().addPermission(new Permission("airdrop.send", PermissionDefault.TRUE));
		server.getPluginManager().addPermission(new Permission("airdrop.cost.bypass", PermissionDefault.FALSE));
		sender = server.addPlayer("Sender");
		recipient = server.addPlayer("Recipient");
		var world = server.addSimpleWorld("targeted_world");
		sender.teleport(new Location(world, 0, 64, 0));
		recipient.teleport(new Location(world, 8.9, 65, -3.2));
		ChatHandler.init(null);
		setStatic("ready", true);
		configuration = new YamlConfiguration();
		setStatic("configuration", new Config(configuration));
		PackageManager.clear();
		PackageManager.publishPackages(java.util.Map.of(
				"starter", new Package("starter", 10, List.of(new ItemStack(Material.BREAD)))));
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
	void explicitPlayerModeRequestsTheNamedRecipient() {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			drops.when(() -> DropController.requestSendDrop(sender, recipient, "starter",
					DropRequestOptions.defaults(), false)).thenReturn(pendingGiftHandle());
			command(sender, "send", "starter", "to", "Recipient");
			drops.verify(() -> DropController.requestSendDrop(sender, recipient, "starter",
					DropRequestOptions.defaults(), false));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"Recipient", "1 2", "1 2 targeted_world", "to", "at", "at 1", "to Recipient extra"})
	void bareAndIncompleteDestinationsNeverRequestADrop(String destination) {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		server.addPlayer("to");
		server.addPlayer("at");
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			drops.when(() -> DropController.requestSendDrop(eq(sender), any(org.bukkit.entity.Player.class),
					eq("starter"), eq(DropRequestOptions.defaults()), eq(false))).thenReturn(pendingGiftHandle());
			drops.when(() -> DropController.requestSendDrop(eq(sender), any(Location.class),
					eq("starter"), eq(DropRequestOptions.defaults()))).thenReturn(pendingHandle());
			command(sender, ("send starter " + destination).split(" "));
			drops.verifyNoInteractions();
		}
		assertTrue(messages(sender).contains("Usage: /airdrop send"));
	}

	@Test
	void incompleteSendShowsPositionalUsage() {
		command(sender, "send", "starter");
		assertTrue(messages(sender).contains("/airdrop send <package> to <player>"));
	}

	@Test
	void sendWithoutDestinationNeverRequestsSelf() {
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(sender, "send", "starter");
			drops.verifyNoInteractions();
		}
	}

	@Test
	void helpAndCompletionExposeExplicitDestinationModes() {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		var tabs = new AirdropTabCompleter();
		assertTrue(tabs.onTabComplete(sender, mock(Command.class), "ad", new String[]{""}).contains("send"));
		assertEquals(List.of("starter"), tabs.onTabComplete(sender, mock(Command.class), "ad", new String[]{"send", "st"}));
		assertEquals(List.of("Recipient"), tabs.onTabComplete(sender, mock(Command.class), "ad", new String[]{"send", "starter", "to", "Re"}));
		assertEquals(List.of("targeted_world"), tabs.onTabComplete(sender, mock(Command.class), "ad", new String[]{"send", "starter", "at", "1", "2", "tar"}));
		command(sender);
		String help = messages(sender);
		assertTrue(help.contains("/airdrop send <package> to <player>"), help);
		assertTrue(help.contains("<x> <z> [world]"), help);
		assertFalse(help.contains("/airdrop gift") || help.contains("/airdrop grant"), help);
	}

	@Test
	void paidGiftFeedbackCorrelatesSpawnAndLandingAndNeverChargesTheRecipient() {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		DefaultDropHandle handle = pendingGiftHandle();
		requestGift(handle);
		String requested = messages(sender);
		assertTrue(requested.contains("Preparing the destination"), requested);
		assertFalse(requested.contains("was taken from your account"), requested);
		ResolvedDropContext context = spawn(handle, PaymentStatus.CHARGED);
		String spawned = messages(sender);
		assertFalse(spawned.contains(handle.requestId().toString()), spawned);
		assertTrue(spawned.contains("$10 was taken from your account"), spawned);
		String incoming = messages(recipient);
		assertTrue(incoming.contains("Sender is sending you package starter"), incoming);
		assertFalse(incoming.contains(handle.requestId().toString()), incoming);
		assertTrue(incoming.contains("You will not be charged"), incoming);
		assertFalse(incoming.contains("was taken from your account"), incoming);
		recipient.teleport(recipient.getLocation().add(100, 0, 100));
		LandedAirdropView landed = new LandedAirdropView(UUID.randomUUID(), context.landingPosition(),
				context, System.currentTimeMillis() + 60_000, false);
		assertTrue(handle.completeOutcome(new DropOutcome.Landed(context, landed, PaymentStatus.CHARGED)));
		String senderLanded = messages(sender);
		String recipientLanded = messages(recipient);
		assertFalse(senderLanded.contains(handle.requestId().toString()), senderLanded);
		assertFalse(recipientLanded.contains(handle.requestId().toString()), recipientLanded);
		for (String feedback : List.of(senderLanded, recipientLanded)) {
			assertTrue(feedback.contains("X: 8, Y: 65, Z: -4 in targeted_world"), feedback);
			assertFalse(feedback.contains("was taken from your account"), feedback);
		}
	}

	@Test
	void grantingAPricedPackageReportsNoPaymentAtSpawn() {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.cost.bypass", true);
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		DefaultDropHandle handle = pendingHandle();
		requestGrant(handle);
		messages(sender);
		spawn(handle, PaymentStatus.NOT_APPLICABLE);
		String feedback = messages(sender);
		assertFalse(feedback.contains(handle.requestId().toString()), feedback);
		assertTrue(feedback.contains("no payment was taken"), feedback);
		assertFalse(feedback.contains("was taken from your account"), feedback);
		assertTrue(messages(recipient).contains("You will not be charged"));
	}

	@ParameterizedTest
	@CsvSource({
			"REFUNDED,FAILED,your payment was refunded",
			"REFUND_FAILED,FAILED,your payment could not be refunded",
			"UNKNOWN,FAILED,your payment outcome is uncertain",
			"CHARGED,SHUTDOWN,your payment remains charged",
			"NOT_APPLICABLE,FAILED,no payment was taken"
	})
	void finalFailureFeedbackReportsTheActualPaymentResult(
			PaymentStatus payment, DeliveryStatus delivery, String expected) {
		boolean gift = payment != PaymentStatus.NOT_APPLICABLE;
		DefaultDropHandle handle = gift ? pendingGiftHandle() : pendingHandle();
		if (gift) {
			sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
			requestGift(handle);
		} else {
			sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.cost.bypass", true);
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
			requestGrant(handle);
		}
		ResolvedDropContext context = spawn(handle, gift ? PaymentStatus.CHARGED : PaymentStatus.NOT_APPLICABLE);
		messages(sender);
		messages(recipient);
		assertTrue(handle.completeOutcome(new DropOutcome.Failed(context, delivery, payment)));
		String feedback = messages(sender);
		assertEquals(payment == PaymentStatus.REFUND_FAILED || payment == PaymentStatus.CHARGED
				|| payment == PaymentStatus.UNKNOWN, feedback.contains(handle.requestId().toString()), feedback);
		assertTrue(feedback.contains("starter to Recipient failed"), feedback);
		assertTrue(feedback.contains(expected), feedback);
		assertEquals(payment == PaymentStatus.REFUNDED, feedback.contains("your payment was refunded"), feedback);
		assertEquals(payment == PaymentStatus.REFUND_FAILED,
				feedback.contains("your payment could not be refunded"), feedback);
		assertEquals(payment == PaymentStatus.CHARGED, feedback.contains("your payment remains charged"), feedback);
		assertEquals(payment == PaymentStatus.NOT_APPLICABLE, feedback.contains("no payment was taken"), feedback);
		assertFalse(feedback.contains("landed"), feedback);
	}

	@Test
	void immediateGiftRejectionOmitsTheRequestIdAndDoesNotReportASpawnOrCharge() {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		DefaultDropHandle handle = pendingGiftHandle();
		assertTrue(handle.completeNotSpawned(new DropOutcome.Rejected(handle.descriptor(), Optional.of(context(handle)),
				DropRejection.of(DropRejectionReason.INSUFFICIENT_FUNDS, "insufficient funds"),
				PaymentStatus.REJECTED)));
		requestGift(handle);
		String feedback = messages(sender);
		assertFalse(feedback.contains(handle.requestId().toString()), feedback);
		assertTrue(feedback.contains("was rejected; no payment was taken"), feedback);
		assertTrue(feedback.contains("cannot afford package price"), feedback);
		assertFalse(feedback.contains("accepted for processing") || feedback.contains("on its way"), feedback);
		assertEquals("", messages(recipient));
	}

	@ParameterizedTest
	@CsvSource({"1,2", "-12.5,0.25"})
	void coordinateSendUsesCallerWorldAndAllowsExplicitOverride(String x, String z) {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		configuration.set(ConfigKeys.GIFT_REQUIRE_RECIPIENT_PERMISSION, true);
		var other = server.addSimpleWorld("override_world");
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			drops.when(() -> DropController.requestSendDrop(eq(sender), any(Location.class), eq("starter"),
					eq(DropRequestOptions.defaults()))).thenReturn(pendingGiftHandle());
			command(sender, "send", "starter", "at", x, z);
			drops.verify(() -> DropController.requestSendDrop(sender, new Location(sender.getWorld(),
					Double.parseDouble(x), sender.getWorld().getMaxHeight() - 1, Double.parseDouble(z)),
					"starter", DropRequestOptions.defaults()));
			command(sender, "send", "starter", "at", x, z, "override_world");
			drops.verify(() -> DropController.requestSendDrop(sender, new Location(other,
					Double.parseDouble(x), other.getMaxHeight() - 1, Double.parseDouble(z)),
					"starter", DropRequestOptions.defaults()));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"NaN", "Infinity", "-Infinity", "30000000", "-30000000", "~1", "^2", "foo"})
	void invalidCoordinatesRejectBeforeRequest(String invalid) {
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(sender, "send", "starter", "at", invalid, "2");
			drops.verifyNoInteractions();
		}
		assertTrue(messages(sender).contains("finite absolute X/Z"));
	}

	@Test
	void unknownWorldInvalidShapeAndPartialPlayerNeverSubstituteAnotherTarget() {
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(sender, "send", "starter", "at", "1", "2", "missing_world");
			assertTrue(messages(sender).contains("Unknown world missing_world"));
			command(sender, "send", "starter", "to", "Recipient", "unexpected");
			command(sender, "send", "starter", "at", "1", "2", "targeted_world", "extra");
			command(sender, "send", "starter", "to", "Recip");
			assertTrue(messages(sender).contains("Could not find online player Recip"));
			drops.verifyNoInteractions();
		}
	}

	@Test
	void otherNonPlayerSenderRequiresCostExemptionAndExplicitWorld() {
		org.bukkit.command.CommandSender console = mock(org.bukkit.command.CommandSender.class);
		when(console.hasPermission("airdrop.send")).thenReturn(true);
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(console, "send", "starter", "at", "1", "2", "targeted_world");
			drops.verifyNoInteractions();
			verify(console).sendMessage(contains("airdrop.cost.bypass"));
			when(console.hasPermission("airdrop.cost.bypass")).thenReturn(true);
			command(console, "send", "starter", "at", "1", "2");
			drops.verifyNoInteractions();
			verify(console).sendMessage(contains("trailing world"));
			when(console.getName()).thenReturn("Console");
			drops.when(() -> DropController.requestSendDrop(eq(console), any(Location.class), eq("starter"),
					eq(DropRequestOptions.defaults()))).thenReturn(pendingHandle());
			command(console, "send", "starter", "at", "1", "2", "targeted_world");
			drops.verify(() -> DropController.requestSendDrop(console, new Location(sender.getWorld(), 1,
					sender.getWorld().getMaxHeight() - 1, 2), "starter", DropRequestOptions.defaults()));
		}
	}

	@Test
	void nativeConsoleWithoutCostGrantCanSendAndRequiresExplicitWorld() {
		var console = mock(org.bukkit.command.ConsoleCommandSender.class);
		when(console.hasPermission("airdrop.send")).thenReturn(true);
		when(console.getName()).thenReturn("Console");
		assertTrue(TargetedDropCommand.canSend(console));
		assertTrue(new AirdropTabCompleter().onTabComplete(console, mock(Command.class), "ad",
				new String[]{""}).contains("send"));
		command(console);
		verify(console).sendMessage(contains("/airdrop send <package> to <player>"));
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(console, "send", "starter", "at", "1", "2");
			drops.verifyNoInteractions();
			verify(console).sendMessage(contains("trailing world"));
			drops.when(() -> DropController.requestSendDrop(eq(console), any(Location.class), eq("starter"),
					eq(DropRequestOptions.defaults()))).thenReturn(pendingHandle());
			command(console, "send", "starter", "at", "1", "2", "targeted_world");
			drops.verify(() -> DropController.requestSendDrop(console, new Location(sender.getWorld(), 1,
					sender.getWorld().getMaxHeight() - 1, 2), "starter", DropRequestOptions.defaults()));
		}
	}

	@Test
	void freeSendStillRequiresSenderAndNamedRecipientPackageAccess() {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.cost.bypass", true);
		configuration.set(ConfigKeys.GIFT_REQUIRE_RECIPIENT_PERMISSION, true);
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(sender, "send", "starter", "to", "Recipient");
			assertTrue(messages(sender).contains("airdrop.package.starter"));
			sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
			command(sender, "send", "starter", "to", "Recipient");
			assertTrue(messages(sender).contains("Recipient cannot receive"));
			drops.verifyNoInteractions();
		}
	}

	@Test
	void explicitSendDenialHidesHelpAndCompletionEvenForAdmin() {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.admin", true);
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.send", false);
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(sender, "send", "starter", "to", "Recipient");
			drops.verifyNoInteractions();
		}
		assertTrue(messages(sender).contains("airdrop.send permission"));
		command(sender);
		assertFalse(messages(sender).contains("/airdrop send"));
		assertEquals(List.of(), new AirdropTabCompleter().onTabComplete(sender, mock(Command.class), "ad",
				new String[]{"send", ""}));
	}

	@Test
	void sendPackageSelfShorthandCoexistsWithTargetedSend() {
		PackageManager.publishPackages(Map.of("send", new Package("send", 0, List.of())));
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.send", true);
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			drops.when(() -> DropController.requestPlayerDrop(sender, "send", DropRequestOptions.defaults()))
					.thenReturn(pendingGiftHandle());
			drops.when(() -> DropController.requestSendDrop(sender, recipient, "send", DropRequestOptions.defaults(), false))
					.thenReturn(pendingGiftHandle());
			command(sender, "send");
			drops.verify(() -> DropController.requestPlayerDrop(sender, "send", DropRequestOptions.defaults()));
			command(sender, "send", "send", "to", "Recipient");
			drops.verify(() -> DropController.requestSendDrop(sender, recipient, "send", DropRequestOptions.defaults(), false));
		}
		var tabs = new AirdropTabCompleter();
		assertEquals(1, tabs.onTabComplete(sender, mock(Command.class), "ad", new String[]{"se"}).stream().filter("send"::equals).count());
		assertEquals(List.of("send"), tabs.onTabComplete(sender, mock(Command.class), "ad", new String[]{"send", ""}));
	}

	@Test
	void completionHidesInvisibleAndIneligibleRecipients() {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		configuration.set(ConfigKeys.GIFT_REQUIRE_RECIPIENT_PERMISSION, true);
		var tabs = new AirdropTabCompleter();
		assertEquals(List.of(), tabs.onTabComplete(sender, mock(Command.class), "ad", new String[]{"send", "starter", "to", "Re"}));
		recipient.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		assertEquals(List.of("Recipient"), tabs.onTabComplete(sender, mock(Command.class), "ad", new String[]{"send", "starter", "to", "Re"}));
		sender.hidePlayer(MockBukkit.createMockPlugin(), recipient);
		assertEquals(List.of(), tabs.onTabComplete(sender, mock(Command.class), "ad", new String[]{"send", "starter", "to", "Re"}));
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void resolvedSendHasNoAcceptanceOrPreparationMessage(boolean alreadySpawned) {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		DefaultDropHandle handle = pendingGiftHandle();
		handle.publishContext(context(handle));
		if (alreadySpawned) {
			spawn(handle, PaymentStatus.CHARGED);
		}
		requestGift(handle);
		if (!alreadySpawned) {
			assertEquals("", messages(sender));
			assertEquals("", messages(recipient));
			spawn(handle, PaymentStatus.CHARGED);
		}
		String feedback = messages(sender);
		assertEquals(1, feedback.lines().count());
		assertTrue(feedback.contains("starter is on its way to Recipient. $10 was taken"), feedback);
		assertFalse(feedback.contains(handle.requestId().toString()), feedback);
		assertFalse(handle.outcome().toCompletableFuture().isDone());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void customLocaleKeepsOptionalReferencesAndActualSpawnAmount(boolean charged) throws Exception {
		useLocale("""
				targeted:
				  spawned: 'Anflug {name}; {amount}; {cost}; {request_id}'
				  spawned-charged: 'Bezahlt {name}; {amount}; {cost}; {request_id}'
				  landed: 'Gelandet {name}; {amount}; {cost}'
				  cost-paid: 'Kosten {amount}'
				  cost-exempt: 'Gratis'
				  recipient-incoming: 'Geschenk {name} von {sender}'
				""");
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		DefaultDropHandle handle = pendingGiftHandle();
		requestGift(handle);
		messages(sender);
		// Change catalog and permissions after request; feedback follows the captured result.
		PackageManager.publishPackages(Map.of("starter", new Package("starter", 99, List.of())));
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.cost.bypass", charged);
		ResolvedDropContext context = spawn(handle, charged ? PaymentStatus.CHARGED : PaymentStatus.NOT_APPLICABLE);
		assertEquals("[Airdrop] " + (charged ? "Bezahlt starter; 10; Kosten 10; " : "Anflug starter; 0; Gratis; ")
				+ handle.requestId() + "\n", messages(sender));
		var recipientMessage = recipient.nextComponentMessage();
		assertNotNull(recipientMessage);
		String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
				.serialize(recipientMessage);
		assertTrue(plain.contains("Geschenk starter von "), plain);
		assertTrue(plain.endsWith("Sender"), plain);
		var head = net.kyori.adventure.text.Component.object(
				net.kyori.adventure.text.object.ObjectContents.playerHead(sender.getUniqueId()))
				.append(net.kyori.adventure.text.Component.space())
				.append(net.kyori.adventure.text.Component.text(sender.getName()));
		assertTrue(recipientMessage.contains(head, (actual, expected) ->
				actual instanceof net.kyori.adventure.text.ObjectComponent object
						&& object.contents().equals(head.contents())));
		assertEquals("", messages(recipient));
		assertTrue(handle.completeOutcome(new DropOutcome.Landed(context,
				new LandedAirdropView(UUID.randomUUID(), context.landingPosition(), context, 1L, false),
				charged ? PaymentStatus.CHARGED : PaymentStatus.NOT_APPLICABLE)));
		assertEquals("[Airdrop] Gelandet starter; " + (charged ? "10; Kosten 10" : "0; Gratis") + "\n",
				messages(sender));
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.MethodSource("preSpawnFailures")
	void customPreSpawnFailureUsesTerminalPaymentWithoutAcceptanceSpam(
			String timing, PaymentStatus payment, DeliveryStatus delivery, String amount, String cost) throws Exception {
		useLocale("""
				targeted:
				  refunded: 'Refunded {name}; {amount}; {cost}'
				  refund-failed: 'Refund failed {name}; {amount}; {cost}'
				  charged-failed: 'Still charged {name}; {amount}; {cost}'
				  payment-unknown: 'Uncertain {name}; {amount}; {cost}'
				  failed: 'No charge {name}; {amount}; {cost}'
				  cost-paid: 'Captured cost {amount}'
				  cost-exempt: 'Exempt'
				  cost-unknown: 'Uncertain payment'
				  amount-unknown: 'Unknown amount'
				""");
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		DefaultDropHandle handle = pendingGiftHandle();
		ResolvedDropContext context = context(handle);
		DropOutcome.Failed failure = new DropOutcome.Failed(context, delivery, payment);
		if (timing.equals("resolved")) {
			handle.publishContext(context);
		} else if (timing.equals("completed")) {
			assertTrue(handle.completeNotSpawned(failure));
		}
		requestGift(handle);
		if (!timing.equals("completed")) {
			String initial = messages(sender);
			assertEquals(timing.equals("pending") ? "[Airdrop] Preparing the destination for your package…\n" : "", initial);
			// Neither a later catalog price nor a changed exemption may determine payment feedback.
			PackageManager.publishPackages(Map.of("starter", new Package("starter", 99, List.of())));
			sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.cost.bypass",
					payment != PaymentStatus.NOT_APPLICABLE);
			assertTrue(handle.completeNotSpawned(failure));
		}
		assertInstanceOf(DropSpawnResult.NotSpawned.class, handle.spawn().toCompletableFuture().join());
		String feedback = messages(sender);
		assertEquals(1, feedback.lines().count(), feedback);
		assertTrue(feedback.endsWith("starter; " + amount + "; " + cost + "\n"), feedback);
		assertFalse(feedback.contains("{") || feedback.contains("99"), feedback);
		assertEquals("", messages(recipient));
	}

	private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> preSpawnFailures() {
		return java.util.stream.Stream.of("pending", "resolved", "completed").flatMap(timing ->
				java.util.stream.Stream.of(
						new Object[]{PaymentStatus.REFUNDED, DeliveryStatus.FAILED, "10", "Captured cost 10"},
						new Object[]{PaymentStatus.REFUND_FAILED, DeliveryStatus.FAILED, "10", "Captured cost 10"},
						new Object[]{PaymentStatus.CHARGED, DeliveryStatus.SHUTDOWN, "10", "Captured cost 10"},
						new Object[]{PaymentStatus.UNKNOWN, DeliveryStatus.FAILED, "Unknown amount", "Uncertain payment"},
						new Object[]{PaymentStatus.NOT_APPLICABLE, DeliveryStatus.FAILED, "0", "Exempt"},
						new Object[]{PaymentStatus.REFUNDED, DeliveryStatus.CANCELLED, "10", "Captured cost 10"}
				).map(values -> org.junit.jupiter.params.provider.Arguments.of(
						timing, values[0], values[1], values[2], values[3])));
	}

	@ParameterizedTest
	@CsvSource({"false,REJECTED", "true,REJECTED", "false,NOT_APPLICABLE", "true,NOT_APPLICABLE"})
	void customRejectionHasZeroAmountEvenWithoutResolvedContext(boolean resolved, PaymentStatus payment) throws Exception {
		useLocale("""
				targeted:
				  rejected: 'Rejected {name}; {amount}; {cost}'
				  cost-uncharged: 'Uncharged'
				  cost-exempt: 'Exempt'
				""");
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		DefaultDropHandle handle = pendingGiftHandle();
		assertTrue(handle.completeNotSpawned(new DropOutcome.Rejected(handle.descriptor(),
				resolved ? Optional.of(context(handle)) : Optional.empty(),
				DropRejection.of(DropRejectionReason.SKY_NOT_CLEAR, "blocked"), payment)));
		requestGift(handle);
		String feedback = messages(sender);
		assertTrue(feedback.startsWith("[Airdrop] Rejected starter; 0; "
				+ (resolved && payment == PaymentStatus.NOT_APPLICABLE ? "Exempt" : "Uncharged") + "\n"), feedback);
		assertFalse(feedback.contains("{") || feedback.contains("accepted"), feedback);
		assertEquals("", messages(recipient));
	}

	@Test
	void customFreePackageFailureUsesZeroAmountAndFreeCost() throws Exception {
		useLocale("""
				targeted:
				  failed: 'Failed {amount}; {cost}'
				  cost-zero: 'Free {amount}'
				""");
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		DefaultDropHandle handle = pendingGiftHandle();
		ResolvedDropContext free = context(handle, BigDecimal.ZERO);
		handle.publishContext(free);
		requestGift(handle);
		assertEquals("", messages(sender));
		PackageManager.publishPackages(Map.of("starter", new Package("starter", 99, List.of())));
		assertTrue(handle.completeNotSpawned(new DropOutcome.Failed(free, DeliveryStatus.FAILED, PaymentStatus.NOT_APPLICABLE)));
		assertEquals("[Airdrop] Failed 0; Free 0\n", messages(sender));
	}

	@Test
	void unknownTerminalPaymentReplacesEarlierChargedPlaceholders() throws Exception {
		useLocale("""
				targeted:
				  payment-unknown: 'Uncertain {amount}; {cost}; {request_id}'
				""");
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		DefaultDropHandle handle = pendingGiftHandle();
		requestGift(handle);
		ResolvedDropContext context = spawn(handle, PaymentStatus.CHARGED);
		messages(sender);
		assertTrue(handle.completeOutcome(new DropOutcome.Failed(context, DeliveryStatus.FAILED, PaymentStatus.UNKNOWN)));
		assertEquals("[Airdrop] Uncertain unknown; payment outcome is uncertain; " + handle.requestId() + "\n",
				messages(sender));
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void offlineSenderAndRecipientDoNotReceiveLaterMilestones(boolean disconnectSender) {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		DefaultDropHandle handle = pendingGiftHandle();
		requestGift(handle);
		messages(sender);
		PlayerMock offline = disconnectSender ? sender : recipient;
		offline.disconnect();
		ResolvedDropContext context = spawn(handle, PaymentStatus.CHARGED);
		assertEquals("", messages(offline));
		handle.completeOutcome(new DropOutcome.Landed(context,
				new LandedAirdropView(UUID.randomUUID(), context.landingPosition(), context, 1L, false),
				PaymentStatus.CHARGED));
		assertEquals("", messages(offline));
	}

	@Test
	void selfSendDoesNotDuplicateRecipientNotifications() {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		DefaultDropHandle handle = pendingGiftHandle();
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			drops.when(() -> DropController.requestSendDrop(sender, sender, "starter",
					DropRequestOptions.defaults(), false)).thenReturn(handle);
			command(sender, "send", "starter", "to", "Sender");
		}
		messages(sender);
		ResolvedDropContext context = spawn(handle, PaymentStatus.CHARGED);
		assertEquals(1, messages(sender).lines().count());
		handle.completeOutcome(new DropOutcome.Landed(context,
				new LandedAirdropView(UUID.randomUUID(), context.landingPosition(), context, 1L, false),
				PaymentStatus.CHARGED));
		assertEquals(1, messages(sender).lines().count());
		assertEquals("", messages(recipient));
	}

	private void useLocale(String yaml) throws Exception {
		java.nio.file.Files.createDirectories(tempDir.resolve("lang"));
		java.nio.file.Files.writeString(tempDir.resolve("lang/de.yml"), yaml);
		Airdrop plugin = mock(Airdrop.class);
		when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
		var language = new com.airdropmc.lang.LanguageManager(plugin);
		language.loadLanguage("de");
		ChatHandler.init(language);
	}

	private void requestGift(DefaultDropHandle handle) {
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			drops.when(() -> DropController.requestSendDrop(sender, recipient, "starter",
					DropRequestOptions.defaults(), false)).thenReturn(handle);
			command(sender, "send", "starter", "to", "Recipient");
		}
	}

	private void requestGrant(DefaultDropHandle handle) {
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			drops.when(() -> DropController.requestSendDrop(sender, recipient, "starter",
					DropRequestOptions.defaults(), false)).thenReturn(handle);
			command(sender, "send", "starter", "to", "Recipient");
		}
	}

	private ResolvedDropContext spawn(DefaultDropHandle handle, PaymentStatus payment) {
		ResolvedDropContext context = handle.context().orElseGet(() -> context(handle));
		FallingAirdropView falling = new FallingAirdropView(UUID.randomUUID(), UUID.randomUUID(),
				context.spawnPosition(), context);
		assertTrue(handle.completeSpawned(new DropSpawnResult.Spawned(context, falling, payment)));
		return context;
	}

	private ResolvedDropContext context(DefaultDropHandle handle) {
		return context(handle, BigDecimal.TEN);
	}

	private ResolvedDropContext context(DefaultDropHandle handle, BigDecimal price) {
		Location landing = handle.descriptor().requestedLocation();
		return new ResolvedDropContext(handle.descriptor(),
				new AirdropPackage("starter", price, List.of(new ItemStack(Material.BREAD))),
				landing.clone().add(0, 100, 0), landing,
				new ResolvedDropSettings(5, 0.3, 100, true, true, true, false, 20,
						Duration.ofSeconds(30), 3, 10, Duration.ofMinutes(10)));
	}

	private DefaultDropHandle pendingHandle() {
		return new DefaultDropHandle(new DropRequestDescriptor(UUID.randomUUID(), DropSource.SYSTEM,
				null, "starter", recipient.getLocation()));
	}

	private DefaultDropHandle pendingGiftHandle() {
		return new DefaultDropHandle(new DropRequestDescriptor(UUID.randomUUID(), DropSource.PLAYER,
				sender.getUniqueId(), "starter", recipient.getLocation()));
	}

	private void command(org.bukkit.command.CommandSender caller, String... args) {
		assertTrue(new CmdAirdrop().onCommand(caller, mock(Command.class), "ad", args));
	}

	private String messages(PlayerMock player) {
		StringBuilder result = new StringBuilder();
		String message;
		while ((message = player.nextMessage()) != null) {
			result.append(org.bukkit.ChatColor.stripColor(message)).append('\n');
		}
		return result.toString();
	}

	private static void setStatic(String name, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(null, value);
	}
}
