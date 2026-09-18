package com.airdropmc.api;

import com.airdropmc.internal.drop.DefaultDropHandle;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropHandleTest {

	private ServerMock server;
	private DefaultDropHandle handle;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		WorldMock world = server.addSimpleWorld("handle_world");
		DropRequestDescriptor descriptor = new DropRequestDescriptor(
				UUID.randomUUID(), DropSource.SYSTEM, null, "starter",
				new Location(world, 0, 80, 0));
		handle = new DefaultDropHandle(descriptor);
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void descriptorAndCachedMinimalStagesAreAlwaysAvailable() {
		assertEquals(handle.descriptor().requestId(), handle.requestId());
		assertEquals(Optional.empty(), handle.context());
		assertTrue(handle.spawn() == handle.spawn());
		assertTrue(handle.outcome() == handle.outcome());
		assertNotSame(handle.spawn().toCompletableFuture(), handle.spawn().toCompletableFuture());
	}

	@Test
	void consumerCancellationCannotAffectBackingStages() {
		CompletableFuture<DropSpawnResult> spawnView = handle.spawn().toCompletableFuture();
		CompletableFuture<DropOutcome> outcomeView = handle.outcome().toCompletableFuture();

		assertTrue(spawnView.cancel(true));
		assertTrue(outcomeView.cancel(true));
		assertFalse(handle.spawn().toCompletableFuture().isDone());
		assertFalse(handle.outcome().toCompletableFuture().isDone());
	}

	@Test
	void consumerCompletionCannotAffectBackingStages() {
		CompletableFuture<DropSpawnResult> spawnView = handle.spawn().toCompletableFuture();
		CompletableFuture<DropOutcome> outcomeView = handle.outcome().toCompletableFuture();

		assertTrue(spawnView.complete(null));
		assertTrue(outcomeView.complete(null));
		assertFalse(handle.spawn().toCompletableFuture().isDone());
		assertFalse(handle.outcome().toCompletableFuture().isDone());
	}

	@Test
	void oneTerminalRejectionCompletesBothStagesExactlyOnce() {
		DropOutcome.Rejected first = new DropOutcome.Rejected(
				handle.descriptor(), Optional.empty(),
				DropRejection.of(DropRejectionReason.UNKNOWN_PACKAGE, "missing"),
				PaymentStatus.NOT_APPLICABLE);
		DropOutcome.Rejected duplicate = new DropOutcome.Rejected(
				handle.descriptor(), Optional.empty(),
				DropRejection.of(DropRejectionReason.SHUTTING_DOWN, "stopping"),
				PaymentStatus.NOT_APPLICABLE);

		assertTrue(handle.completeNotSpawned(first));
		assertFalse(handle.completeNotSpawned(duplicate));
		assertEquals(first, handle.outcome().toCompletableFuture().join());
		assertEquals(first,
				((DropSpawnResult.NotSpawned) handle.spawn().toCompletableFuture().join()).outcome());
		assertThrows(NullPointerException.class,
				() -> handle.publishContext(null));
	}
}
