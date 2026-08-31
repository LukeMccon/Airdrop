package com.airdropmc.api.event;

import com.airdropmc.api.AirdropPackage;
import com.airdropmc.api.DeliveryStatus;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.DropRequestDescriptor;
import com.airdropmc.api.DropSource;
import com.airdropmc.api.FallingAirdropView;
import com.airdropmc.api.LandedAirdropView;
import com.airdropmc.api.PaymentStatus;
import com.airdropmc.api.ResolvedDropContext;
import com.airdropmc.api.ResolvedDropSettings;
import com.airdropmc.api.WorldPosition;
import org.bukkit.Location;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.math.BigDecimal;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AirdropEventImmutabilityTest {

	private ServerMock server;
	private WorldMock world;
	private ResolvedDropContext context;
	private FallingAirdropView falling;
	private LandedAirdropView landed;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("event_shape_world");
		context = context(UUID.randomUUID());
		falling = new FallingAirdropView(
				UUID.randomUUID(), UUID.randomUUID(), context.spawnPosition(), context);
		landed = new LandedAirdropView(
				falling.crateId(), context.landingPosition(), context,
				System.currentTimeMillis() + 60_000L, false);
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void concreteEventsAreSynchronousAndOwnIndependentHandlerLists() {
		DropOutcome outcome = new DropOutcome.Landed(
				context, landed, PaymentStatus.NOT_APPLICABLE);
		List<AbstractAirdropEvent> events = List.of(
				new AirdropRequestEvent(context),
				new AirdropSpawnedEvent(context, falling),
				new AirdropLandingAttemptEvent(
						context, falling, context.landingPosition()),
				new AirdropLandedEvent(context, landed),
				new AirdropOutcomeEvent(context, outcome));

		for (AbstractAirdropEvent event : events) {
			assertFalse(event.isAsynchronous());
			assertEquals(context.descriptor().requestId(), event.requestId());
			assertSame(context.descriptor(), event.descriptor());
			assertSame(context, event.context());
		}
		Set<HandlerList> handlers = Set.of(
				AirdropRequestEvent.getHandlerList(),
				AirdropSpawnedEvent.getHandlerList(),
				AirdropLandingAttemptEvent.getHandlerList(),
				AirdropLandedEvent.getHandlerList(),
				AirdropOutcomeEvent.getHandlerList());
		assertEquals(events.size(), handlers.size());
	}

	@Test
	void eventPayloadsExposeOnlyDetachedSupportedSnapshots() {
		AirdropLandingAttemptEvent attempt = new AirdropLandingAttemptEvent(
				context, falling, context.landingPosition());
		AirdropLandedEvent landedEvent = new AirdropLandedEvent(context, landed);
		AirdropOutcomeEvent outcomeEvent = new AirdropOutcomeEvent(
				context,
				new DropOutcome.Failed(
						context, DeliveryStatus.CANCELLED, PaymentStatus.NOT_APPLICABLE));

		Location first = attempt.context().landingLocation();
		first.setX(999);

		assertEquals(context.landingPosition(), attempt.candidatePosition());
		assertEquals(context.landingPosition().x(), attempt.context().landingLocation().getX());
		assertSame(falling, attempt.airdrop());
		assertSame(landed, landedEvent.airdrop());
		assertEquals(DeliveryStatus.CANCELLED, outcomeEvent.outcome().delivery());
		assertNotSame(first, attempt.context().landingLocation());
	}

	@Test
	void constructorsRejectMismatchedCorrelationsAndWorlds() {
		ResolvedDropContext other = context(UUID.randomUUID());
		FallingAirdropView otherFalling = new FallingAirdropView(
				UUID.randomUUID(), UUID.randomUUID(), other.spawnPosition(), other);
		LandedAirdropView otherLanded = new LandedAirdropView(
				UUID.randomUUID(), other.landingPosition(), other,
				System.currentTimeMillis() + 60_000L, false);

		assertThrows(IllegalArgumentException.class,
				() -> new AirdropSpawnedEvent(context, otherFalling));
		assertThrows(IllegalArgumentException.class,
				() -> new AirdropLandingAttemptEvent(
						context, otherFalling, context.landingPosition()));
		assertThrows(IllegalArgumentException.class,
				() -> new AirdropLandingAttemptEvent(
						context, falling,
						new WorldPosition(UUID.randomUUID(), 1, 2, 3, 0, 0)));
		assertThrows(IllegalArgumentException.class,
				() -> new AirdropLandedEvent(context, otherLanded));
		assertThrows(IllegalArgumentException.class,
				() -> new AirdropOutcomeEvent(
						context,
						new DropOutcome.Failed(
								other, DeliveryStatus.FAILED, PaymentStatus.NOT_APPLICABLE)));
	}

	@Test
	void publicEventSignaturesDoNotLeakMutableImplementationObjects() {
		List<String> forbidden = List.of(
				"com.airdropmc.Crate",
				"com.airdropmc.config.",
				"com.airdropmc.controllers.",
				"com.airdropmc.helpers.",
				"com.airdropmc.internal.",
				"com.airdropmc.packages.Package",
				"org.bukkit.Location",
				"org.bukkit.block.Block",
				"org.bukkit.entity.Entity");
		for (Class<?> eventType : List.of(
				AirdropRequestEvent.class,
				AirdropSpawnedEvent.class,
				AirdropLandingAttemptEvent.class,
				AirdropLandedEvent.class,
				AirdropOutcomeEvent.class)) {
			for (Constructor<?> constructor : eventType.getConstructors()) {
				assertNoForbiddenSignature(constructor.toGenericString(), forbidden);
			}
			for (Method method : eventType.getDeclaredMethods()) {
				if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
					assertNoForbiddenSignature(method.toGenericString(), forbidden);
				}
			}
		}
	}

	private static void assertNoForbiddenSignature(String signature, List<String> forbidden) {
		for (String fragment : forbidden) {
			assertFalse(signature.contains(fragment),
					() -> "Event signature leaks " + fragment + ": " + signature);
		}
	}

	private ResolvedDropContext context(UUID requestId) {
		DropRequestDescriptor descriptor = new DropRequestDescriptor(
				requestId,
				DropSource.SYSTEM,
				null,
				"starter",
				new Location(world, 4, 100, 8));
		return new ResolvedDropContext(
				descriptor,
				new AirdropPackage("starter", BigDecimal.ZERO, List.of()),
				new Location(world, 4.5, 100, 8.5),
				new Location(world, 4.5, 65, 8.5),
				new ResolvedDropSettings(
						1, 0.3, 35, false, false, false, false, 0,
						Duration.ofSeconds(30), 3, 10, Duration.ofMinutes(10)));
	}
}
