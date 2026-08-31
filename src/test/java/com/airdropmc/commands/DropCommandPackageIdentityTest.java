package com.airdropmc.commands;

import com.airdropmc.Airdrop;
import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.DropRejection;
import com.airdropmc.api.DropRejectionReason;
import com.airdropmc.api.DropRequestDescriptor;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.api.DropSource;
import com.airdropmc.api.DropSpawnResult;
import com.airdropmc.api.PaymentStatus;
import com.airdropmc.controllers.DropController;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.LanguageManager;
import com.airdropmc.lang.MessageKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DropCommandPackageIdentityTest {

	private ServerMock server;
	private WorldMock world;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("command_world");
		ChatHandler.init(new LanguageManager(mock(Airdrop.class)));
	}

	@AfterEach
	void tearDown() {
		ChatHandler.init(null);
		MockBukkit.unmock();
	}

	@Test
	void dropCommandPassesCallerSpellingToTypedRequest() {
		PlayerMock player = server.addPlayer();
		DropHandle handle = rejected(
				player, "STARTER", DropRejectionReason.UNKNOWN_PACKAGE,
				PaymentStatus.REJECTED);

		try (MockedStatic<DropController> controller = mockStatic(DropController.class)) {
			controller.when(() -> DropController.requestPlayerDrop(
					player, "STARTER", DropRequestOptions.defaults())).thenReturn(handle);

			DropCommand.onCommand(player, new String[]{"STARTER"});

			controller.verify(() -> DropController.requestPlayerDrop(
					player, "STARTER", DropRequestOptions.defaults()));
		}
	}

	@Test
	void permissionDenialDisplaysCanonicalNode() {
		PlayerMock player = server.addPlayer();
		DropHandle handle = rejected(
				player, "Starter", DropRejectionReason.INSUFFICIENT_PERMISSION,
				PaymentStatus.REJECTED);

		try (MockedStatic<DropController> controller = mockStatic(DropController.class)) {
			controller.when(() -> DropController.requestPlayerDrop(
					player, "Starter", DropRequestOptions.defaults())).thenReturn(handle);
			DropCommand.onCommand(player, new String[]{"Starter"});
		}

		String text = nextMessage(player);
		assertTrue(text.contains("airdrop.package.starter"), text);
		assertFalse(text.contains("airdrop.package.Starter"));
	}

	@Test
	void permissionDenialSupportsLegacyPackagePlaceholder() {
		LanguageManager language = mock(LanguageManager.class);
		when(language.get(MessageKey.PREFIX)).thenReturn("[Airdrop]");
		when(language.get(eq(MessageKey.ERROR_INSUFFICIENT_PERMISSIONS), anyMap()))
				.thenAnswer(invocation -> {
					Map<String, String> placeholders = invocation.getArgument(1);
					return "requires airdrop.package." + placeholders.get("package");
				});
		ChatHandler.init(language);
		PlayerMock player = server.addPlayer();
		DropHandle handle = rejected(
				player, "Starter", DropRejectionReason.INSUFFICIENT_PERMISSION,
				PaymentStatus.REJECTED);

		try (MockedStatic<DropController> controller = mockStatic(DropController.class)) {
			controller.when(() -> DropController.requestPlayerDrop(
					player, "Starter", DropRequestOptions.defaults())).thenReturn(handle);
			DropCommand.onCommand(player, new String[]{"Starter"});
		}

		String text = nextMessage(player);
		assertTrue(text.contains("airdrop.package.starter"), text);
		assertFalse(text.contains("airdrop.package.null"), text);
	}

	@Test
	void unavailableEconomyDisplaysConfiguredMessage() {
		PlayerMock player = server.addPlayer();
		DropHandle handle = rejected(
				player, "Starter", DropRejectionReason.ECONOMY_PROVIDER_UNAVAILABLE,
				PaymentStatus.REJECTED);

		try (MockedStatic<DropController> controller = mockStatic(DropController.class)) {
			controller.when(() -> DropController.requestPlayerDrop(
					player, "Starter", DropRequestOptions.defaults())).thenReturn(handle);
			DropCommand.onCommand(player, new String[]{"Starter"});
		}

		assertTrue(nextMessage(player).contains("Priced packages are unavailable"));
	}

	private DropHandle rejected(
			PlayerMock player,
			String packageName,
			DropRejectionReason reason,
			PaymentStatus payment) {
		DropRequestDescriptor descriptor = new DropRequestDescriptor(
				UUID.randomUUID(), DropSource.PLAYER, player.getUniqueId(), packageName,
				new Location(world, 0, 100, 0));
		DropOutcome.Rejected outcome = new DropOutcome.Rejected(
				descriptor,
				Optional.empty(),
				DropRejection.of(reason, "test rejection"),
				payment);
		DropHandle handle = mock(DropHandle.class);
		when(handle.spawn()).thenReturn(CompletableFuture.completedFuture(
				new DropSpawnResult.NotSpawned(outcome)));
		when(handle.outcome()).thenReturn(CompletableFuture.completedFuture(outcome));
		return handle;
	}

	private String nextMessage(PlayerMock player) {
		Component message = player.nextComponentMessage();
		assertNotNull(message);
		return PlainTextComponentSerializer.plainText().serialize(message);
	}
}
