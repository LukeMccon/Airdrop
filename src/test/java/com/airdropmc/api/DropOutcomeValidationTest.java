package com.airdropmc.api;

import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DropOutcomeValidationTest {

	private ServerMock server;
	private WorldMock world;
	private ResolvedDropContext context;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("outcome_world");
		DropRequestDescriptor descriptor = new DropRequestDescriptor(
				UUID.randomUUID(), DropSource.PLAYER, UUID.randomUUID(), "starter",
				new Location(world, 4, 100, 8));
		context = new ResolvedDropContext(
				descriptor,
				new AirdropPackage("starter", BigDecimal.TEN, List.of()),
				new Location(world, 4.5, 100, 8.5),
				new Location(world, 4.5, 65, 8.5),
				new ResolvedDropSettings(
						1, 0.3, 35, false, false, false, false, 0,
						Duration.ofSeconds(30), 3, 10, Duration.ofMinutes(10)));
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void rejectedOutcomeAllowsOnlyKnownUnchargedPayment() {
		DropRejection rejection = DropRejection.of(
				DropRejectionReason.INSUFFICIENT_FUNDS, "insufficient funds");

		assertEquals(PaymentStatus.REJECTED,
				new DropOutcome.Rejected(
						context.descriptor(), Optional.of(context), rejection,
						PaymentStatus.REJECTED).payment());
		assertThrows(IllegalArgumentException.class, () -> new DropOutcome.Rejected(
				context.descriptor(), Optional.of(context), rejection, PaymentStatus.UNKNOWN));
		assertThrows(IllegalArgumentException.class, () -> new DropOutcome.Rejected(
				context.descriptor(), Optional.of(context), rejection, PaymentStatus.CHARGED));
	}

	@Test
	void rejectedOutcomeRejectsContextFromAnotherRequest() {
		DropRequestDescriptor otherDescriptor = new DropRequestDescriptor(
				UUID.randomUUID(), DropSource.PLAYER, UUID.randomUUID(), "starter",
				new Location(world, 4, 100, 8));

		assertThrows(IllegalArgumentException.class, () -> new DropOutcome.Rejected(
				otherDescriptor,
				Optional.of(context),
				DropRejection.of(DropRejectionReason.INSUFFICIENT_PERMISSION, "denied"),
				PaymentStatus.REJECTED));
	}

	@Test
	void landedOutcomeRequiresDeliveredPaymentState() {
		LandedAirdropView view = new LandedAirdropView(
				UUID.randomUUID(), context.landingPosition(), context,
				System.currentTimeMillis() + 60_000L, false);

		assertEquals(DeliveryStatus.LANDED,
				new DropOutcome.Landed(context, view, PaymentStatus.CHARGED).delivery());
		assertEquals(DeliveryStatus.LANDED,
				new DropOutcome.Landed(context, view, PaymentStatus.NOT_APPLICABLE).delivery());
		assertThrows(IllegalArgumentException.class,
				() -> new DropOutcome.Landed(context, view, PaymentStatus.REFUNDED));
	}

	@Test
	void spawnedAndLandedVariantsRejectViewsFromAnotherRequest() {
		DropRequestDescriptor otherDescriptor = new DropRequestDescriptor(
				UUID.randomUUID(), DropSource.SYSTEM, null, "starter",
				new Location(world, 4, 100, 8));
		ResolvedDropContext otherContext = new ResolvedDropContext(
				otherDescriptor,
				context.airdropPackage(),
				context.spawnLocation(),
				context.landingLocation(),
				context.settings());
		FallingAirdropView falling = new FallingAirdropView(
				UUID.randomUUID(), UUID.randomUUID(), otherContext.spawnPosition(), otherContext);
		LandedAirdropView landed = new LandedAirdropView(
				UUID.randomUUID(), otherContext.landingPosition(), otherContext,
				System.currentTimeMillis() + 60_000L, false);

		assertThrows(IllegalArgumentException.class,
				() -> new DropSpawnResult.Spawned(
						context, falling, PaymentStatus.NOT_APPLICABLE));
		assertThrows(IllegalArgumentException.class,
				() -> new DropOutcome.Landed(
						context, landed, PaymentStatus.NOT_APPLICABLE));
	}

	@Test
	void notSpawnedCannotWrapLandedOutcome() {
		LandedAirdropView landed = new LandedAirdropView(
				UUID.randomUUID(), context.landingPosition(), context,
				System.currentTimeMillis() + 60_000L, false);
		DropOutcome.Landed outcome = new DropOutcome.Landed(
				context, landed, PaymentStatus.CHARGED);

		assertThrows(IllegalArgumentException.class,
				() -> new DropSpawnResult.NotSpawned(outcome));
	}

	@Test
	void failedOutcomeRejectsImpossibleDeliveryAndPaymentPairs() {
		assertEquals(PaymentStatus.REFUNDED,
				new DropOutcome.Failed(
						context, DeliveryStatus.FAILED, PaymentStatus.REFUNDED).payment());
		assertEquals(PaymentStatus.CHARGED,
				new DropOutcome.Failed(
						context, DeliveryStatus.SHUTDOWN, PaymentStatus.CHARGED).payment());
		assertThrows(IllegalArgumentException.class, () -> new DropOutcome.Failed(
				context, DeliveryStatus.LANDED, PaymentStatus.NOT_APPLICABLE));
		assertThrows(IllegalArgumentException.class, () -> new DropOutcome.Failed(
				context, DeliveryStatus.FAILED, PaymentStatus.CHARGED));
	}

	@Test
	void cooldownRejectionCarriesOnlyPositiveRetryDuration() {
		DropRejection rejection = DropRejection.cooldown(Duration.ofSeconds(12));

		assertEquals(Optional.of(Duration.ofSeconds(12)), rejection.retryAfter());
		assertThrows(IllegalArgumentException.class,
				() -> DropRejection.cooldown(Duration.ZERO));
		assertThrows(IllegalArgumentException.class, () -> new DropRejection(
				DropRejectionReason.UNKNOWN_PACKAGE, "unknown", Optional.of(Duration.ofSeconds(1))));
		assertThrows(IllegalArgumentException.class,
				() -> DropRejection.of(DropRejectionReason.COOLDOWN, "cooldown"));
	}

	@ParameterizedTest
	@EnumSource(PaymentStatus.class)
	void rejectedPaymentMatrixIsExhaustive(PaymentStatus payment) {
		DropRejection rejection = DropRejection.of(
				DropRejectionReason.INSUFFICIENT_FUNDS, "insufficient");
		if (payment == PaymentStatus.NOT_APPLICABLE || payment == PaymentStatus.REJECTED) {
			assertEquals(payment, new DropOutcome.Rejected(
					context.descriptor(), Optional.of(context), rejection, payment).payment());
			return;
		}
		assertThrows(IllegalArgumentException.class, () -> new DropOutcome.Rejected(
				context.descriptor(), Optional.of(context), rejection, payment));
	}

	@ParameterizedTest
	@EnumSource(PaymentStatus.class)
	void landedPaymentMatrixIsExhaustive(PaymentStatus payment) {
		LandedAirdropView view = new LandedAirdropView(
				UUID.randomUUID(), context.landingPosition(), context,
				System.currentTimeMillis() + 60_000L, false);
		if (payment == PaymentStatus.NOT_APPLICABLE || payment == PaymentStatus.CHARGED) {
			assertEquals(payment, new DropOutcome.Landed(context, view, payment).payment());
			return;
		}
		assertThrows(IllegalArgumentException.class,
				() -> new DropOutcome.Landed(context, view, payment));
	}

	@ParameterizedTest
	@EnumSource(PaymentStatus.class)
	void failedPaymentMatrixIsExhaustive(PaymentStatus payment) {
		boolean allowed = payment == PaymentStatus.NOT_APPLICABLE
				|| payment == PaymentStatus.REFUNDED
				|| payment == PaymentStatus.REFUND_FAILED
				|| payment == PaymentStatus.UNKNOWN;
		if (allowed) {
			assertEquals(payment, new DropOutcome.Failed(
					context, DeliveryStatus.FAILED, payment).payment());
			assertEquals(payment, new DropOutcome.Failed(
					context, DeliveryStatus.CANCELLED, payment).payment());
			return;
		}
		assertThrows(IllegalArgumentException.class, () -> new DropOutcome.Failed(
				context, DeliveryStatus.FAILED, payment));
		assertThrows(IllegalArgumentException.class, () -> new DropOutcome.Failed(
				context, DeliveryStatus.CANCELLED, payment));
	}

	@ParameterizedTest
	@EnumSource(PaymentStatus.class)
	void shutdownPaymentMatrixIsExhaustive(PaymentStatus payment) {
		boolean allowed = payment == PaymentStatus.NOT_APPLICABLE
				|| payment == PaymentStatus.CHARGED
				|| payment == PaymentStatus.UNKNOWN;
		if (allowed) {
			assertEquals(payment, new DropOutcome.Failed(
					context, DeliveryStatus.SHUTDOWN, payment).payment());
			return;
		}
		assertThrows(IllegalArgumentException.class, () -> new DropOutcome.Failed(
				context, DeliveryStatus.SHUTDOWN, payment));
	}

	@ParameterizedTest
	@EnumSource(value = DropRejectionReason.class, names = "COOLDOWN", mode = EnumSource.Mode.EXCLUDE)
	void everyNonCooldownRejectionReasonHasAStableRepresentation(DropRejectionReason reason) {
		assertEquals(reason, DropRejection.of(reason, "diagnostic").reason());
	}
}
