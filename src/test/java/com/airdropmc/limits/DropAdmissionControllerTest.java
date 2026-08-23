package com.airdropmc.limits;

import com.airdropmc.exceptions.DropLimitException;
import com.airdropmc.exceptions.DropLimitException.Reason;
import com.airdropmc.limits.DropAdmissionController.Lease;
import com.airdropmc.limits.DropAdmissionController.Snapshot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropAdmissionControllerTest {

	private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID WORLD = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final DropLocationKey LOCATION = new DropLocationKey(WORLD, 10, 65, 10);
	private static final DropLocationKey OTHER_LOCATION = new DropLocationKey(WORLD, 20, 65, 20);
	private static final DropLocationKey THIRD_LOCATION = new DropLocationKey(WORLD, 30, 65, 30);
	private static final DropLimitSettings LIMITS = new DropLimitSettings(
			Duration.ofSeconds(30), 2, 3, Duration.ofSeconds(600));

	private final AtomicLong clock = new AtomicLong();

	@Test
	void acquirePlayer_reservesAndReleasesEveryClaimExactlyOnce() throws Exception {
		DropAdmissionController controller = new DropAdmissionController(clock::get);
		Lease lease = controller.acquirePlayer(PLAYER, false, LOCATION, LIMITS);

		assertEquals(new Snapshot(1, 1, 1, 1, 0, true), controller.snapshot());
		lease.commitSpawn();
		lease.markLanded();
		assertEquals(new Snapshot(0, 1, 1, 0, 1, true), controller.snapshot());
		lease.close();
		lease.close();
		assertEquals(new Snapshot(0, 0, 0, 0, 1, true), controller.snapshot());
	}

	@Test
	void commitSpawn_startsCooldownOnlyAfterSuccessfulSpawn() throws Exception {
		DropAdmissionController controller = new DropAdmissionController(clock::get);
		Lease failed = controller.acquirePlayer(PLAYER, false, LOCATION, LIMITS);
		failed.close();
		assertDoesNotThrow(() -> controller.acquirePlayer(PLAYER, false, LOCATION, LIMITS).close());

		Lease successful = controller.acquirePlayer(PLAYER, false, OTHER_LOCATION, LIMITS);
		successful.commitSpawn();
		DropLimitException rejection = assertThrows(DropLimitException.class,
				() -> controller.acquirePlayer(PLAYER, false, THIRD_LOCATION, LIMITS));
		assertEquals(Reason.COOLDOWN, rejection.getReason());
		assertEquals(30, rejection.getRetryAfterSeconds());
	}

	@Test
	void bypass_skipsCooldownButNeverCapacity() throws Exception {
		DropAdmissionController controller = new DropAdmissionController(clock::get);
		controller.acquirePlayer(PLAYER, true, LOCATION, LIMITS);
		controller.acquirePlayer(PLAYER, true, OTHER_LOCATION, LIMITS);

		DropLimitException rejection = assertThrows(DropLimitException.class,
				() -> controller.acquirePlayer(PLAYER, true, THIRD_LOCATION, LIMITS));
		assertEquals(Reason.FALLING_CAPACITY, rejection.getReason());
	}

	@Test
	void futureLandedClaims_preventInFlightOvercommit() throws Exception {
		DropAdmissionController controller = new DropAdmissionController(clock::get);
		DropLimitSettings oneLanded = new DropLimitSettings(
				Duration.ofSeconds(30), 3, 1, Duration.ofSeconds(600));
		controller.acquireSystem(LOCATION, oneLanded);

		DropLimitException rejection = assertThrows(DropLimitException.class,
				() -> controller.acquireSystem(OTHER_LOCATION, oneLanded));
		assertEquals(Reason.LANDED_CAPACITY, rejection.getReason());
	}

	@Test
	void locationClaim_rejectsDuplicateBlock() throws Exception {
		DropAdmissionController controller = new DropAdmissionController(clock::get);
		controller.acquireSystem(LOCATION, LIMITS);

		DropLimitException rejection = assertThrows(DropLimitException.class,
				() -> controller.acquireSystem(LOCATION, LIMITS));
		assertEquals(Reason.LOCATION_RESERVED, rejection.getReason());
	}

	@Test
	void closedOrClearedLease_cannotCommitOrRepopulateCooldowns() throws Exception {
		DropAdmissionController controller = new DropAdmissionController(clock::get);
		Lease closed = controller.acquirePlayer(PLAYER, false, LOCATION, LIMITS);
		closed.close();
		assertThrows(IllegalStateException.class, closed::commitSpawn);

		Lease cleared = controller.acquirePlayer(PLAYER, false, OTHER_LOCATION, LIMITS);
		controller.clear();
		assertThrows(IllegalStateException.class, cleared::commitSpawn);
		assertEquals(new Snapshot(0, 0, 0, 0, 0, true), controller.snapshot());
	}

	@Test
	void clearRacingWithCommit_neverLeavesCooldownOrCapacity() throws Exception {
		DropAdmissionController controller = new DropAdmissionController(clock::get);
		Lease lease = controller.acquirePlayer(PLAYER, false, LOCATION, LIMITS);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch start = new CountDownLatch(1);
			Future<?> commit = executor.submit(() -> {
				await(start);
				try {
					lease.commitSpawn();
				} catch (IllegalStateException clearedFirst) {
					// Legal race outcome.
				}
			});
			Future<?> clear = executor.submit(() -> {
				await(start);
				controller.clear();
			});
			start.countDown();
			commit.get(5, TimeUnit.SECONDS);
			clear.get(5, TimeUnit.SECONDS);
			assertEquals(new Snapshot(0, 0, 0, 0, 0, true), controller.snapshot());
		} finally {
			executor.shutdownNow();
			controller.clear();
		}
	}

	@Test
	void cooldown_usesWrapSafeElapsedTime() throws Exception {
		clock.set(Long.MAX_VALUE - Duration.ofSeconds(10).toNanos());
		DropAdmissionController controller = new DropAdmissionController(clock::get);
		Lease lease = controller.acquirePlayer(PLAYER, false, LOCATION, LIMITS);
		lease.commitSpawn();
		clock.addAndGet(Duration.ofSeconds(31).toNanos());

		assertDoesNotThrow(() -> controller.acquirePlayer(PLAYER, false, OTHER_LOCATION, LIMITS).close());
	}

	@Test
	void concurrentAcquire_neverOversubscribes() throws Exception {
		DropAdmissionController controller = new DropAdmissionController(clock::get);
		DropLimitSettings twoSlots = new DropLimitSettings(
				Duration.ofSeconds(30), 2, 20, Duration.ofSeconds(600));
		ExecutorService executor = Executors.newFixedThreadPool(20);
		try {
			CountDownLatch ready = new CountDownLatch(20);
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Boolean>> attempts = new ArrayList<>();
			for (int i = 0; i < 20; i++) {
				int coordinate = i;
				attempts.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					try {
						controller.acquireSystem(new DropLocationKey(WORLD, coordinate, 65, coordinate), twoSlots);
						return true;
					} catch (DropLimitException expected) {
						return false;
					}
				}));
			}
			assertTrue(ready.await(5, TimeUnit.SECONDS));
			start.countDown();
			assertEquals(2, attempts.stream().filter(DropAdmissionControllerTest::get).count());
			assertEquals(2, controller.snapshot().falling());
			assertEquals(2, controller.snapshot().landedClaims());
		} finally {
			executor.shutdownNow();
			controller.clear();
		}
	}

	@Test
	void stopAccepting_rejectsNewWorkAndClearPreservesStoppedState() throws Exception {
		DropAdmissionController controller = new DropAdmissionController(clock::get);
		controller.stopAccepting();
		controller.clear();

		DropLimitException rejection = assertThrows(DropLimitException.class,
				() -> controller.acquireSystem(LOCATION, LIMITS));
		assertEquals(Reason.SHUTTING_DOWN, rejection.getReason());
	}

	private static boolean get(Future<Boolean> future) {
		try {
			return future.get(5, TimeUnit.SECONDS);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new AssertionError(interrupted);
		} catch (ExecutionException | TimeoutException failure) {
			throw new AssertionError(failure);
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new AssertionError(interrupted);
		}
	}
}
