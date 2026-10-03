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
		server.getPluginManager().addPermission(new Permission("airdrop.gift", PermissionDefault.TRUE));
		server.getPluginManager().addPermission(new Permission("airdrop.grant", PermissionDefault.OP));
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
	void grantCanBeDelegatedWithoutAdminOrPackageAccess() {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.grant", true);
		configuration.set(ConfigKeys.GIFT_REQUIRE_RECIPIENT_PERMISSION, true);
		DropHandle handle = pendingHandle();
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			drops.when(() -> DropController.requestSystemDrop(
					recipient.getLocation(), "starter", DropRequestOptions.defaults())).thenReturn(handle);
			command(sender, "grant", "Recipient", "starter");
			drops.verify(() -> DropController.requestSystemDrop(
					recipient.getLocation(), "starter", DropRequestOptions.defaults()));
		}
		assertTrue(messages(sender).contains(handle.requestId().toString()));
	}

	@Test
	void explicitGrantDenialBlocksEvenAnOperator() {
		sender.setOp(true);
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.grant", false);
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(sender, "grant", "Recipient", "starter");
			drops.verifyNoInteractions();
		}
		assertTrue(messages(sender).contains("airdrop.grant"));
	}

	@Test
	void partialOnlineNamesAreRejectedBeforeRequestingADrop() {
		sender.setOp(true);
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(sender, "grant", "Recip", "starter");
			drops.verifyNoInteractions();
		}
		assertTrue(messages(sender).contains("online player"));
	}

	@Test
	void wrongArgumentCountShowsSpecificGiftUsage() {
		command(sender, "gift", "Recipient");
		assertTrue(messages(sender).contains("Usage: /airdrop gift <player> <package>"));
	}

	@Test
	void giftIsDiscoverableButGrantIsHiddenFromOrdinaryPlayers() {
		List<String> suggestions = new AirdropTabCompleter().onTabComplete(
				sender, mock(Command.class), "ad", new String[]{""});
		assertTrue(suggestions.contains("gift"));
		assertFalse(suggestions.contains("grant"));
		command(sender);
		String help = messages(sender);
		assertTrue(help.contains("/airdrop gift <player> <package>"));
		assertFalse(help.contains("/airdrop grant <player> <package>"));
	}

	@Test
	void giftCompletionUsesSenderPackageAccessAndExactRecipientNames() {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		AirdropTabCompleter completer = new AirdropTabCompleter();
		assertEquals(List.of("Recipient"), completer.onTabComplete(
				sender, mock(Command.class), "ad", new String[]{"gift", "Re"}));
		assertEquals(List.of("starter"), completer.onTabComplete(
				sender, mock(Command.class), "ad", new String[]{"gift", "Recipient", "st"}));
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void giftRoutesThePayerRecipientAndConfiguredPermissionPolicy(boolean requireRecipientPermission) {
		configuration.set(ConfigKeys.GIFT_REQUIRE_RECIPIENT_PERMISSION, requireRecipientPermission);
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		if (requireRecipientPermission) {
			recipient.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		}
		DefaultDropHandle handle = pendingGiftHandle();
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			drops.when(() -> DropController.requestGiftDrop(sender, recipient, "starter",
					DropRequestOptions.defaults(), requireRecipientPermission)).thenReturn(handle);
			command(sender, "gift", "Recipient", "starter");
			drops.verify(() -> DropController.requestGiftDrop(sender, recipient, "starter",
					DropRequestOptions.defaults(), requireRecipientPermission));
			drops.verifyNoMoreInteractions();
		}
		String feedback = messages(sender);
		assertTrue(feedback.contains("Gift request " + handle.requestId()), feedback);
		assertTrue(feedback.contains("starter at Recipient's location"), feedback);
	}

	@Test
	void strictGiftRejectsAnIneligibleRecipientBeforeRequestingEvenForAnOperator() {
		configuration.set(ConfigKeys.GIFT_REQUIRE_RECIPIENT_PERMISSION, true);
		sender.setOp(true);
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(sender, "gift", "Recipient", "starter");
			drops.verifyNoInteractions();
		}
		String feedback = messages(sender);
		assertTrue(feedback.contains("Recipient cannot receive package starter"), feedback);
	}

	@Test
	void giftPermissionRevokedDuringRequestReportsTheGiftNodeInsteadOfPackageAccess() {
		configuration.set(ConfigKeys.GIFT_REQUIRE_RECIPIENT_PERMISSION, true);
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		recipient.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		DefaultDropHandle handle = pendingGiftHandle();
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			drops.when(() -> DropController.requestGiftDrop(sender, recipient, "starter",
					DropRequestOptions.defaults(), true)).thenAnswer(ignored -> {
				sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.gift", false);
				assertTrue(handle.completeNotSpawned(new DropOutcome.Rejected(handle.descriptor(), Optional.of(context(handle)),
						DropRejection.of(DropRejectionReason.INSUFFICIENT_PERMISSION, "gift permission revoked"),
						PaymentStatus.NOT_APPLICABLE)));
				return handle;
			});
			command(sender, "gift", "Recipient", "starter");
			drops.verify(() -> DropController.requestGiftDrop(sender, recipient, "starter",
					DropRequestOptions.defaults(), true));
		}
		String feedback = messages(sender);
		assertTrue(feedback.contains("You need airdrop.gift permission"), feedback);
		assertFalse(feedback.contains("airdrop.package.starter"), feedback);
		assertTrue(feedback.contains(handle.requestId().toString()), feedback);
	}

	@Test
	void recipientAccessRevokedDuringStrictGiftReportsRecipientEligibility() {
		configuration.set(ConfigKeys.GIFT_REQUIRE_RECIPIENT_PERMISSION, true);
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		recipient.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		DefaultDropHandle handle = pendingGiftHandle();
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			drops.when(() -> DropController.requestGiftDrop(sender, recipient, "starter",
					DropRequestOptions.defaults(), true)).thenAnswer(ignored -> {
				recipient.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", false);
				assertTrue(handle.completeNotSpawned(new DropOutcome.Rejected(handle.descriptor(), Optional.of(context(handle)),
						DropRejection.of(DropRejectionReason.INSUFFICIENT_PERMISSION, "recipient access revoked"),
						PaymentStatus.NOT_APPLICABLE)));
				return handle;
			});
			command(sender, "gift", "Recipient", "starter");
			drops.verify(() -> DropController.requestGiftDrop(sender, recipient, "starter",
					DropRequestOptions.defaults(), true));
		}
		String feedback = messages(sender);
		assertTrue(feedback.contains("Recipient cannot receive package starter"), feedback);
		assertFalse(feedback.contains("airdrop.package.starter"), feedback);
		assertTrue(feedback.contains(handle.requestId().toString()), feedback);
	}

	@Test
	void giftRejectsASenderWithoutPackageAccessBeforeRequesting() {
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(sender, "gift", "Recipient", "starter");
			drops.verifyNoInteractions();
		}
		assertTrue(messages(sender).contains("airdrop.package.starter"));
	}

	@Test
	void consoleCanGrantAnOnlinePlayerAPricedPackage() {
		var console = server.getConsoleSender();
		DefaultDropHandle handle = pendingHandle();
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			drops.when(() -> DropController.requestSystemDrop(
					recipient.getLocation(), "starter", DropRequestOptions.defaults())).thenReturn(handle);
			command(console, "grant", "Recipient", "starter");
			drops.verify(() -> DropController.requestSystemDrop(
					recipient.getLocation(), "starter", DropRequestOptions.defaults()));
			drops.verifyNoMoreInteractions();
		}
		assertTrue(console.nextMessage().contains("Grant request " + handle.requestId()));
	}

	@Test
	void consoleCannotBuyAGift() {
		var console = server.getConsoleSender();
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(console, "gift", "Recipient", "starter");
			drops.verifyNoInteractions();
		}
		assertTrue(console.nextMessage().contains("Must be a player"));
		assertFalse(new AirdropTabCompleter().onTabComplete(console, mock(Command.class), "drop",
				new String[]{""}).contains("gift"));
	}

	@Test
	void explicitGiftDenialBlocksAnOperatorAndHidesGiftHelpAndCompletion() {
		sender.setOp(true);
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.gift", false);
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			command(sender, "gift", "Recipient", "starter");
			drops.verifyNoInteractions();
		}
		assertTrue(messages(sender).contains("airdrop.gift"));
		assertFalse(new AirdropTabCompleter().onTabComplete(sender, mock(Command.class), "ad",
				new String[]{""}).contains("gift"));
		command(sender);
		assertFalse(messages(sender).contains("/airdrop gift <player> <package>"));
	}

	@Test
	void delegatedGrantHelpAndCompletionIncludeAllPackagesWithoutAdministrativeCommands() {
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.grant", true);
		configuration.set(ConfigKeys.GIFT_REQUIRE_RECIPIENT_PERMISSION, true);
		PackageManager.publishPackages(Map.of(
				"starter", new Package("starter", 10, List.of(new ItemStack(Material.BREAD))),
				"premium", new Package("premium", 25, List.of(new ItemStack(Material.DIAMOND)))));
		AirdropTabCompleter completer = new AirdropTabCompleter();
		assertTrue(completer.onTabComplete(sender, mock(Command.class), "drop", new String[]{""})
				.contains("grant"));
		assertEquals(List.of("Recipient"), completer.onTabComplete(sender, mock(Command.class), "ad",
				new String[]{"grant", "Re"}));
		assertEquals(List.of("premium", "starter"), completer.onTabComplete(
				sender, mock(Command.class), "ad", new String[]{"grant", "Recipient", ""}));
		command(sender);
		String help = messages(sender);
		assertTrue(help.contains("/airdrop grant <player> <package>"), help);
		assertFalse(help.contains("/airdrop reload") || help.contains("/airdrop package create"), help);
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void giftCompletionObeysTheConfiguredRecipientPolicy(boolean requireRecipientPermission) {
		configuration.set(ConfigKeys.GIFT_REQUIRE_RECIPIENT_PERMISSION, requireRecipientPermission);
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.package.starter", true);
		assertEquals(requireRecipientPermission ? List.of() : List.of("starter"),
				new AirdropTabCompleter().onTabComplete(sender, mock(Command.class), "ad",
						new String[]{"gift", "Recipient", ""}));
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
		sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.grant", true);
		DefaultDropHandle handle = pendingHandle();
		requestGrant(handle);
		messages(sender);
		spawn(handle, PaymentStatus.NOT_APPLICABLE);
		String feedback = messages(sender);
		assertTrue(feedback.contains("Grant request " + handle.requestId()), feedback);
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
			sender.addAttachment(MockBukkit.createMockPlugin(), "airdrop.grant", true);
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

	private void requestGift(DefaultDropHandle handle) {
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			drops.when(() -> DropController.requestGiftDrop(sender, recipient, "starter",
					DropRequestOptions.defaults(), false)).thenReturn(handle);
			command(sender, "gift", "Recipient", "starter");
		}
	}

	private void requestGrant(DefaultDropHandle handle) {
		try (MockedStatic<DropController> drops = mockStatic(DropController.class)) {
			drops.when(() -> DropController.requestSystemDrop(recipient.getLocation(), "starter",
					DropRequestOptions.defaults())).thenReturn(handle);
			command(sender, "grant", "Recipient", "starter");
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
