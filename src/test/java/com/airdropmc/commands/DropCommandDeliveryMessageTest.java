package com.airdropmc.commands;

import com.airdropmc.Airdrop;
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
import com.airdropmc.api.WorldPosition;
import com.airdropmc.controllers.DropController;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DropCommandDeliveryMessageTest {

	@TempDir
	Path tempDir;
	private ServerMock server;
	private WorldMock world;
	private PlayerMock player;
	private PlayerMock observer;
	private ResolvedDropContext context;
	private CompletableFuture<DropSpawnResult> spawn;
	private CompletableFuture<DropOutcome> outcome;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("delivery_world");
		player = server.addPlayer();
		observer = server.addPlayer();
		player.teleport(new Location(world, 4, 100, 8));
		spawn = new CompletableFuture<>();
		outcome = new CompletableFuture<>();
		Airdrop plugin = mock(Airdrop.class);
		when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
		Files.createDirectories(tempDir.resolve("lang"));
		Files.writeString(tempDir.resolve("lang/en.yml"), """
				prefix: '[Test]'
				drop:
				  incoming: 'Anflug {name}'
				  incoming-charged: 'Anflug {name}; bezahlt {amount}'
				  landed: 'Gelandet {name}: {x}, {y}, {z}'
				  landed-other-world: 'Gelandet {name}: {x}, {y}, {z} in {world}'
				  charged: 'Legacy charge {amount}'
				  failed: 'Lieferung fehlgeschlagen'
				  refunded: 'Zahlung erstattet'
				""");
		LanguageManager language = new LanguageManager(plugin);
		language.publishLanguage(language.prepareLanguage("en"));
		ChatHandler.init(language);
	}

	@AfterEach
	void tearDown() {
		ChatHandler.init(null);
		MockBukkit.unmock();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void successfulSpawnSendsExactlyOneLocalizedIncomingMessage(boolean paid) {
		request(paid);
		assertNoMessages();

		spawn.complete(spawned(paid));

		assertEquals(paid ? "[Test] Anflug Starter; bezahlt 12.50" : "[Test] Anflug Starter",
				nextMessage(player));
		assertNoMessages();
		assertFalse(outcome.isDone(), "Incoming feedback must not wait for landing");
	}

	@ParameterizedTest
	@CsvSource({"false, false", "false, true", "true, false"})
	void committedLandingReportsActualBlocksToRequesterAfterMovement(boolean paid, boolean changeWorld) {
		request(paid);
		spawn.complete(spawned(paid));
		nextMessage(player);
		WorldMock currentWorld = changeWorld ? server.addSimpleWorld("other_world") : world;
		player.teleport(new Location(currentWorld, 700, 100, 900));
		assertNoMessages();

		outcome.complete(landed());

		assertEquals("[Test] Gelandet Starter: 12, -4, -10"
				+ (changeWorld ? " in delivery_world" : ""), nextMessage(player));
		assertEquals(new Location(world, 4, 65, 8), context.landingLocation());
		assertNoMessages();
		assertFalse(outcome.complete(landed()));
		assertNoMessages();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void offlineRequesterDoesNotReceiveSpawnOrLandingFeedback(boolean disconnectBeforeSpawn) {
		request(true);
		if (disconnectBeforeSpawn) {
			player.disconnect();
		}
		spawn.complete(spawned(true));
		if (!disconnectBeforeSpawn) {
			nextMessage(player);
			player.disconnect();
		}

		outcome.complete(landed());

		assertNoMessages();
	}

	@ParameterizedTest
	@CsvSource({
			"FAILED, NOT_APPLICABLE, false, Lieferung fehlgeschlagen",
			"FAILED, REFUNDED, true, Zahlung erstattet",
			"FAILED, REFUND_FAILED, false, Lieferung fehlgeschlagen",
			"CANCELLED, NOT_APPLICABLE, true, Lieferung fehlgeschlagen",
			"SHUTDOWN, CHARGED, true, Lieferung fehlgeschlagen"
	})
	void unsuccessfulOutcomePreservesFailureFeedbackWithoutLandingSuccess(
			DeliveryStatus delivery, PaymentStatus payment, boolean afterSpawn, String message) {
		boolean paid = payment != PaymentStatus.NOT_APPLICABLE;
		request(paid);
		DropOutcome.Failed failed = new DropOutcome.Failed(context, delivery, payment);
		if (afterSpawn) {
			spawn.complete(spawned(paid));
			nextMessage(player);
		} else {
			spawn.complete(new DropSpawnResult.NotSpawned(failed));
		}
		assertNoMessages();

		outcome.complete(failed);

		assertEquals("[Test] " + message, nextMessage(player));
		assertNoMessages();
	}

	@ParameterizedTest
	@EnumSource(value = DropRejectionReason.class, names = {"CANCELLED", "PAYMENT_REJECTED"})
	void rejectedRequestSendsFailureWithoutIncomingOrLandedSuccess(DropRejectionReason reason) {
		request(false);
		DropOutcome.Rejected rejected = new DropOutcome.Rejected(context.descriptor(),
				Optional.of(context), DropRejection.of(reason, "rejected"), PaymentStatus.REJECTED);
		spawn.complete(new DropSpawnResult.NotSpawned(rejected));
		assertNoMessages();

		outcome.complete(rejected);

		assertEquals("[Test] Lieferung fehlgeschlagen", nextMessage(player));
		assertNoMessages();
	}

	@Test
	void offlineRequesterDoesNotReceiveFailureFeedback() {
		request(false);
		player.disconnect();
		DropOutcome.Failed failed = new DropOutcome.Failed(
				context, DeliveryStatus.FAILED, PaymentStatus.NOT_APPLICABLE);

		spawn.complete(new DropSpawnResult.NotSpawned(failed));
		outcome.complete(failed);

		assertNoMessages();
	}

	private void request(boolean paid) {
		DropRequestDescriptor descriptor = new DropRequestDescriptor(UUID.randomUUID(),
				DropSource.PLAYER, player.getUniqueId(), "STARTER", player.getLocation());
		context = new ResolvedDropContext(descriptor,
				new AirdropPackage("Starter", paid ? new BigDecimal("12.50") : BigDecimal.ZERO, List.of()),
				new Location(world, 4, 120, 8), new Location(world, 4, 65, 8),
				new ResolvedDropSettings(1, 0.1, 20, false, false, false, false, 0,
						Duration.ofSeconds(1), 5, 5, Duration.ofMinutes(5)));
		DropHandle handle = mock(DropHandle.class);
		when(handle.spawn()).thenReturn(spawn);
		when(handle.outcome()).thenReturn(outcome);
		try (MockedStatic<DropController> controller = mockStatic(DropController.class)) {
			controller.when(() -> DropController.requestPlayerDrop(
					player, "STARTER", DropRequestOptions.defaults())).thenReturn(handle);
			DropCommand.onCommand(player, new String[]{"STARTER"});
		}
	}

	private DropSpawnResult.Spawned spawned(boolean paid) {
		return new DropSpawnResult.Spawned(context,
				new FallingAirdropView(UUID.randomUUID(), UUID.randomUUID(), context.spawnPosition(), context),
				paid ? PaymentStatus.CHARGED : PaymentStatus.NOT_APPLICABLE);
	}

	private DropOutcome.Landed landed() {
		return new DropOutcome.Landed(context,
				new LandedAirdropView(UUID.randomUUID(),
						new WorldPosition(world.getUID(), 12.8, -3.2, -9.1, 0, 0), context, 1L, false),
				context.airdropPackage().price().signum() == 0
						? PaymentStatus.NOT_APPLICABLE : PaymentStatus.CHARGED);
	}

	private void assertNoMessages() {
		assertNull(player.nextComponentMessage(), "Requester received an unexpected message");
		assertNull(observer.nextComponentMessage(), "Delivery feedback must be requester-only");
	}

	private String nextMessage(PlayerMock recipient) {
		Component message = recipient.nextComponentMessage();
		assertNotNull(message);
		return PlainTextComponentSerializer.plainText().serialize(message);
	}
}
