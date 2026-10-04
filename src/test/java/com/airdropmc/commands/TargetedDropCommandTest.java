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
	void incompleteSendShowsPositionalUsage() {
		command(sender, "send", "starter");
		assertTrue(messages(sender).contains("/airdrop send <package> <player>"));
	}

	@Test
	void sendWithoutDestinationNeverRequestsSelf() {
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(sender, "send", "starter");
			drops.verifyNoInteractions();
		}
	}

	@Test
	void helpAndCompletionExposeSendWithoutDestinationKeywords() {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		var tabs = new AirdropTabCompleter();
		assertTrue(tabs.onTabComplete(sender, mock(Command.class), "ad", new String[]{""}).contains("send"));
		assertEquals(List.of("starter"), tabs.onTabComplete(sender, mock(Command.class), "ad", new String[]{"send", "st"}));
		assertEquals(List.of("Recipient"), tabs.onTabComplete(sender, mock(Command.class), "ad", new String[]{"send", "starter", "Re"}));
		assertEquals(List.of("targeted_world"), tabs.onTabComplete(sender, mock(Command.class), "ad", new String[]{"send", "starter", "1", "2", "tar"}));
		command(sender);
		String help = messages(sender);
		assertTrue(help.contains("/airdrop send <package> <player>"), help);
		assertTrue(help.contains("<x> <z> [world]"), help);
		assertFalse(help.contains("/airdrop gift") || help.contains("/airdrop grant"), help);
	}

	@Test
	void paidGiftFeedbackCorrelatesSpawnAndLandingAndNeverChargesTheRecipient() {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		DefaultDropHandle handle = pendingGiftHandle();
		requestGift(handle);
		String requested = messages(sender);
		assertTrue(requested.contains(handle.requestId().toString()), requested);
		assertFalse(requested.contains("was taken from your account"), requested);
		ResolvedDropContext context = spawn(handle, PaymentStatus.CHARGED);
		String spawned = messages(sender);
		assertTrue(spawned.contains(handle.requestId().toString()), spawned);
		assertTrue(spawned.contains("$10 was taken from your account"), spawned);
		String incoming = messages(recipient);
		assertTrue(incoming.contains("Sender is sending you package starter"), incoming);
		assertTrue(incoming.contains("at your location when requested"), incoming);
		assertTrue(incoming.contains(handle.requestId().toString()), incoming);
		assertTrue(incoming.contains("You will not be charged"), incoming);
		assertFalse(incoming.contains("was taken from your account"), incoming);
		recipient.teleport(recipient.getLocation().add(100, 0, 100));
		LandedAirdropView landed = new LandedAirdropView(UUID.randomUUID(), context.landingPosition(),
				context, System.currentTimeMillis() + 60_000, false);
		assertTrue(handle.completeOutcome(new DropOutcome.Landed(context, landed, PaymentStatus.CHARGED)));
		String senderLanded = messages(sender);
		String recipientLanded = messages(recipient);
		for (String feedback : List.of(senderLanded, recipientLanded)) {
			assertTrue(feedback.contains(handle.requestId().toString()), feedback);
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
		assertTrue(feedback.contains("Send request " + handle.requestId()), feedback);
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
		assertTrue(feedback.contains(handle.requestId().toString()), feedback);
		assertTrue(feedback.contains("starter at Recipient failed"), feedback);
		assertTrue(feedback.contains(expected), feedback);
		assertEquals(payment == PaymentStatus.REFUNDED, feedback.contains("your payment was refunded"), feedback);
		assertEquals(payment == PaymentStatus.REFUND_FAILED,
				feedback.contains("your payment could not be refunded"), feedback);
		assertEquals(payment == PaymentStatus.CHARGED, feedback.contains("your payment remains charged"), feedback);
		assertEquals(payment == PaymentStatus.NOT_APPLICABLE, feedback.contains("no payment was taken"), feedback);
		assertFalse(feedback.contains("landed"), feedback);
	}

	@Test
	void immediateGiftRejectionIncludesTheRequestIdAndDoesNotReportASpawnOrCharge() {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		DefaultDropHandle handle = pendingGiftHandle();
		assertTrue(handle.completeNotSpawned(new DropOutcome.Rejected(handle.descriptor(), Optional.of(context(handle)),
				DropRejection.of(DropRejectionReason.INSUFFICIENT_FUNDS, "insufficient funds"),
				PaymentStatus.REJECTED)));
		requestGift(handle);
		String feedback = messages(sender);
		assertTrue(feedback.contains(handle.requestId().toString()), feedback);
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
			command(sender, "send", "starter", x, z);
			drops.verify(() -> DropController.requestSendDrop(sender, new Location(sender.getWorld(),
					Double.parseDouble(x), sender.getWorld().getMaxHeight() - 1, Double.parseDouble(z)),
					"starter", DropRequestOptions.defaults()));
			command(sender, "send", "starter", x, z, "override_world");
			drops.verify(() -> DropController.requestSendDrop(sender, new Location(other,
					Double.parseDouble(x), other.getMaxHeight() - 1, Double.parseDouble(z)),
					"starter", DropRequestOptions.defaults()));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"NaN", "Infinity", "-Infinity", "30000000", "-30000000", "~1", "^2", "foo"})
	void invalidCoordinatesRejectBeforeRequest(String invalid) {
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(sender, "send", "starter", invalid, "2");
			drops.verifyNoInteractions();
		}
		assertTrue(messages(sender).contains("finite absolute X/Z"));
	}

	@Test
	void unknownWorldInvalidShapeAndPartialPlayerNeverSubstituteAnotherTarget() {
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(sender, "send", "starter", "1", "2", "missing_world");
			assertTrue(messages(sender).contains("Unknown world missing_world"));
			command(sender, "send", "starter", "Recipient", "unexpected");
			command(sender, "send", "starter", "1", "2", "targeted_world", "extra");
			command(sender, "send", "starter", "Recip");
			assertTrue(messages(sender).contains("Could not find online player Recip"));
			drops.verifyNoInteractions();
		}
	}

	@Test
	void consoleRequiresCostExemptionAndExplicitWorld() {
		org.bukkit.command.CommandSender console = mock(org.bukkit.command.CommandSender.class);
		when(console.hasPermission("airdrop.send")).thenReturn(true);
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(console, "send", "starter", "1", "2", "targeted_world");
			drops.verifyNoInteractions();
			verify(console).sendMessage(contains("airdrop.cost.bypass"));
			when(console.hasPermission("airdrop.cost.bypass")).thenReturn(true);
			command(console, "send", "starter", "1", "2");
			drops.verifyNoInteractions();
			verify(console).sendMessage(contains("trailing world"));
			when(console.getName()).thenReturn("Console");
			drops.when(() -> DropController.requestSendDrop(eq(console), any(Location.class), eq("starter"),
					eq(DropRequestOptions.defaults()))).thenReturn(pendingHandle());
			command(console, "send", "starter", "1", "2", "targeted_world");
			drops.verify(() -> DropController.requestSendDrop(console, new Location(sender.getWorld(), 1,
					sender.getWorld().getMaxHeight() - 1, 2), "starter", DropRequestOptions.defaults()));
		}
	}

	@Test
	void freeSendStillRequiresSenderAndNamedRecipientPackageAccess() {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.cost.bypass", true);
		configuration.set(ConfigKeys.GIFT_REQUIRE_RECIPIENT_PERMISSION, true);
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(sender, "send", "starter", "Recipient");
			assertTrue(messages(sender).contains("airdrop.package.starter"));
			sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
			command(sender, "send", "starter", "Recipient");
			assertTrue(messages(sender).contains("Recipient cannot receive"));
			drops.verifyNoInteractions();
		}
	}

	@Test
	void explicitSendDenialHidesHelpAndCompletionEvenForAdmin() {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.admin", true);
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.send", false);
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(sender, "send", "starter", "Recipient");
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
			command(sender, "send", "send", "Recipient");
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
		assertEquals(List.of(), tabs.onTabComplete(sender, mock(Command.class), "ad", new String[]{"send", "starter", "Re"}));
		recipient.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		assertEquals(List.of("Recipient"), tabs.onTabComplete(sender, mock(Command.class), "ad", new String[]{"send", "starter", "Re"}));
		sender.hidePlayer(MockBukkit.createMockPlugin(), recipient);
		assertEquals(List.of(), tabs.onTabComplete(sender, mock(Command.class), "ad", new String[]{"send", "starter", "Re"}));
	}

	private void requestGift(DefaultDropHandle handle) {
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			drops.when(() -> DropController.requestSendDrop(sender, recipient, "starter",
					DropRequestOptions.defaults(), false)).thenReturn(handle);
			command(sender, "send", "starter", "Recipient");
		}
	}

	private void requestGrant(DefaultDropHandle handle) {
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			drops.when(() -> DropController.requestSendDrop(sender, recipient, "starter",
					DropRequestOptions.defaults(), false)).thenReturn(handle);
			command(sender, "send", "starter", "Recipient");
		}
	}

	private ResolvedDropContext spawn(DefaultDropHandle handle, PaymentStatus payment) {
		ResolvedDropContext context = context(handle);
		FallingAirdropView falling = new FallingAirdropView(UUID.randomUUID(), UUID.randomUUID(),
				context.spawnPosition(), context);
		assertTrue(handle.completeSpawned(new DropSpawnResult.Spawned(context, falling, payment)));
		return context;
	}

	private ResolvedDropContext context(DefaultDropHandle handle) {
		Location landing = handle.descriptor().requestedLocation();
		return new ResolvedDropContext(handle.descriptor(),
				new AirdropPackage("starter", BigDecimal.TEN, List.of(new ItemStack(Material.BREAD))),
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
			result.append(message).append('\n');
		}
		return result.toString();
	}

	private static void setStatic(String name, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(null, value);
	}
}
