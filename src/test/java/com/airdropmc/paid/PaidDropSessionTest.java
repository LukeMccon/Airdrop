package com.airdropmc.paid;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.economy.EconomyPlayer;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.economy.EconomyResult;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLimitSettings;
import com.airdropmc.limits.DropLocationKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaidDropSessionTest {

	private ServerMock server;
	private MockPlugin plugin;
	private PlayerMock player;
	private DropAdmissionController admission;
	private DropAdmissionController.Lease lease;
	private ControlledEconomyProvider economy;
	private AtomicInteger spawns;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		plugin = MockBukkit.createMockPlugin("AirdropSessionHarness");
		player = server.addPlayer("Luke");
		admission = new DropAdmissionController();
		lease = admission.acquirePlayer(player.getUniqueId(), false,
				new DropLocationKey(UUID.randomUUID(), 0, 65, 0),
				new DropLimitSettings(Duration.ofSeconds(30), 3, 10, Duration.ofMinutes(10)));
		economy = new ControlledEconomyProvider(true);
		spawns = new AtomicInteger();
		Airdrop.setPluginInstance(null);
		setStatic("shuttingDown", false);
	}

	@AfterEach
	void tearDown() throws Exception {
		admission.clear();
		Airdrop.setPluginInstance(null);
		setStatic("shuttingDown", false);
		MockBukkit.unmock();
	}

	@Test
	void confirmedWithdrawalSpawnsExactlyOnce() {
		PaidDropSession session = session(BigDecimal.TEN);

		session.start();
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		economy.withdrawal.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		assertEquals(1, spawns.get());
		assertEquals(1, economy.withdrawals);
		assertTrue(nextMessage().contains("taken from your account"));
	}

	@Test
	void insufficientFundsStopsBeforeWithdrawalAndReleasesLease() {
		PaidDropSession session = session(BigDecimal.TEN);

		session.start();
		economy.affordability.complete(EconomyResult.rejected("insufficient funds"));
		server.getScheduler().performOneTick();

		assertEquals(0, economy.withdrawals);
		assertEquals(0, spawns.get());
		assertEquals(0, admission.snapshot().pending());
		assertTrue(nextMessage().contains("cannot afford"));
	}

	@Test
	void affordabilityTimeoutCreatesNoCrateAndReleasesLease() {
		PaidDropSession session = session(BigDecimal.TEN);
		session.start();

		server.getScheduler().performTicks(PaidDropSession.PAYMENT_TIMEOUT_TICKS);

		assertEquals(0, economy.withdrawals);
		assertEquals(0, spawns.get());
		assertEquals(0, admission.snapshot().pending());
		assertTrue(nextMessage().contains("no crate was created"));
	}

	@Test
	void withdrawalTimeoutCancelsDropAndLateSuccessRefundsOnce() {
		PaidDropSession session = session(BigDecimal.TEN);
		session.start();
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		server.getScheduler().performTicks(PaidDropSession.PAYMENT_TIMEOUT_TICKS);

		assertEquals(0, spawns.get());
		assertEquals(0, admission.snapshot().pending());
		assertTrue(nextMessage().contains("no crate was created"));

		economy.withdrawal.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		assertEquals(1, economy.deposits);
		economy.refund.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		assertEquals(1, economy.deposits);
		assertTrue(nextMessage().contains("payment was refunded"));
	}

	@Test
	void lateWithdrawalSuccessAndRejectedRefundDoesNotRepeatGenericFailure() {
		PaidDropSession session = session(BigDecimal.TEN);
		session.start();
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		server.getScheduler().performTicks(PaidDropSession.PAYMENT_TIMEOUT_TICKS);
		nextMessage();

		economy.withdrawal.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		economy.refund.complete(EconomyResult.rejected("refund rejected"));
		server.getScheduler().performOneTick();

		assertEquals(1, economy.deposits);
		assertNull(player.nextComponentMessage());
	}

	@Test
	void lateWithdrawalSuccessAndRefundTimeoutDoesNotRepeatGenericFailure() {
		PaidDropSession session = session(BigDecimal.TEN);
		session.start();
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		server.getScheduler().performTicks(PaidDropSession.PAYMENT_TIMEOUT_TICKS);
		nextMessage();

		economy.withdrawal.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		server.getScheduler().performTicks(PaidDropSession.PAYMENT_TIMEOUT_TICKS);

		assertEquals(1, economy.deposits);
		assertNull(player.nextComponentMessage());
	}

	@Test
	void exceptionalWithdrawalCreatesNoCrateAndDoesNotGuessAtRefund() {
		PaidDropSession session = session(BigDecimal.TEN);
		session.start();
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		economy.withdrawal.completeExceptionally(new IllegalStateException("database unavailable"));
		server.getScheduler().performOneTick();

		assertEquals(0, spawns.get());
		assertEquals(0, economy.deposits);
		assertEquals(0, admission.snapshot().pending());
		assertTrue(nextMessage().contains("no crate was created"));
	}

	@Test
	void rejectedWithdrawalCreatesNoCrateAndDoesNotRefund() {
		PaidDropSession session = session(BigDecimal.TEN);
		session.start();
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		economy.withdrawal.complete(EconomyResult.rejected("withdrawal rejected"));
		server.getScheduler().performOneTick();

		assertEquals(0, spawns.get());
		assertEquals(0, economy.deposits);
		assertEquals(0, admission.snapshot().pending());
		assertTrue(nextMessage().contains("no crate was created"));
	}

	@Test
	void disconnectDoesNotCancelConfirmedWithdrawalOrSpawn() {
		PaidDropSession session = session(BigDecimal.TEN);
		session.start();
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		player.disconnect();

		economy.withdrawal.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		assertEquals(1, spawns.get());
		assertEquals(1, economy.withdrawals);
	}

	@Test
	void zeroPriceBypassesEconomy() {
		PaidDropSession session = session(BigDecimal.ZERO);

		session.start();

		assertEquals(1, spawns.get());
		assertEquals(0, economy.affordabilityChecks);
		assertEquals(0, economy.withdrawals);
		assertNull(player.nextComponentMessage());
	}

	@Test
	void failedZeroPriceCrateDoesNotDepositOrClaimRefund() {
		PaidDropSession session = session(BigDecimal.ZERO);
		session.start();

		session.failed();

		assertEquals(0, economy.deposits);
		assertTrue(nextMessage().contains("no crate was created"));
		assertNull(player.nextComponentMessage());
	}

	@Test
	void landedCrateIsNeverRefundedByLaterCleanup() {
		PaidDropSession session = session(BigDecimal.TEN);
		advanceToFalling(session);

		session.landed();
		session.failed();

		assertEquals(0, economy.deposits);
	}

	@Test
	void knownCrateFailureStartsOneRefund() {
		PaidDropSession session = session(BigDecimal.TEN);
		advanceToFalling(session);
		nextMessage();

		session.failed();
		session.failed();

		assertEquals(1, economy.deposits);
		assertNull(player.nextComponentMessage());
	}

	@Test
	void rejectedRefundEndsWithOneGenericFailureMessage() {
		PaidDropSession session = session(BigDecimal.TEN);
		advanceToFalling(session);
		nextMessage();
		session.failed();
		assertNull(player.nextComponentMessage());

		economy.refund.complete(EconomyResult.rejected("refund rejected"));
		server.getScheduler().performOneTick();

		assertTrue(nextMessage().contains("no crate was created"));
		assertNull(player.nextComponentMessage());
	}

	@Test
	void exceptionalRefundIsNotRetried() {
		PaidDropSession session = session(BigDecimal.TEN);
		advanceToFalling(session);
		nextMessage();
		session.failed();

		economy.refund.completeExceptionally(new IllegalStateException("refund unavailable"));
		server.getScheduler().performTicks(PaidDropSession.PAYMENT_TIMEOUT_TICKS + 1L);

		assertEquals(1, economy.deposits);
		assertTrue(nextMessage().contains("no crate was created"));
		assertNull(player.nextComponentMessage());
	}

	@Test
	void refundTimeoutEndsWithOneGenericFailureMessage() {
		PaidDropSession session = session(BigDecimal.TEN);
		advanceToFalling(session);
		nextMessage();
		session.failed();
		assertNull(player.nextComponentMessage());

		server.getScheduler().performTicks(PaidDropSession.PAYMENT_TIMEOUT_TICKS);

		assertTrue(nextMessage().contains("no crate was created"));
		assertNull(player.nextComponentMessage());
	}

	@Test
	void shutdownDoesNotStartRefundForFallingCrate() throws Exception {
		PaidDropSession session = session(BigDecimal.TEN);
		advanceToFalling(session);
		setStatic("shuttingDown", true);

		session.failed();

		assertEquals(0, economy.deposits);
	}

	@Test
	void crateCleanupDuringSpawnDoesNotDuplicateFailureMessageOrRefund() {
		PaidDropSession session = new PaidDropSession(
				plugin,
				economy,
				new EconomyPlayer(player.getUniqueId(), player.getName()),
				BigDecimal.TEN,
				lease,
				paidSession -> {
					paidSession.failed();
					throw new IllegalStateException("spawn failed");
				});

		session.start();
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		economy.withdrawal.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		assertEquals(1, economy.deposits);
		assertNull(player.nextComponentMessage());
		economy.refund.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		assertTrue(nextMessage().contains("payment was refunded"));
		assertNull(player.nextComponentMessage());
	}

	@Test
	void nativeCompletionChecksPluginStateOnlyAfterReturningToServerThread() throws Exception {
		Plugin threadCheckedPlugin = mock(Plugin.class);
		when(threadCheckedPlugin.getLogger()).thenReturn(Logger.getLogger("PaidDropSessionThreadTest"));
		when(threadCheckedPlugin.isEnabled()).thenAnswer(invocation -> {
			assertTrue(org.bukkit.Bukkit.isPrimaryThread());
			return true;
		});
		PaidDropSession session = new PaidDropSession(
				threadCheckedPlugin,
				economy,
				new EconomyPlayer(player.getUniqueId(), player.getName()),
				BigDecimal.TEN,
				lease,
				ignored -> spawns.incrementAndGet());
		session.start();

		Thread completionThread = new Thread(
				() -> economy.affordability.complete(EconomyResult.ok()),
				"economy-completion-test");
		completionThread.start();
		completionThread.join();
		server.getScheduler().performOneTick();

		assertEquals(1, economy.withdrawals);
	}

	private void advanceToFalling(PaidDropSession session) {
		session.start();
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		economy.withdrawal.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
	}

	private PaidDropSession session(BigDecimal amount) {
		return new PaidDropSession(
				plugin,
				economy,
				new EconomyPlayer(player.getUniqueId(), player.getName()),
				amount,
				lease,
				ignored -> spawns.incrementAndGet());
	}

	private String nextMessage() {
		Component message = player.nextComponentMessage();
		assertNotNull(message);
		return PlainTextComponentSerializer.plainText().serialize(message);
	}

	private static void setStatic(String fieldName, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}

	private static final class ControlledEconomyProvider implements EconomyProvider {

		private final boolean nativeAsync;
		private final CompletableFuture<EconomyResult> affordability = new CompletableFuture<>();
		private final CompletableFuture<EconomyResult> withdrawal = new CompletableFuture<>();
		private final CompletableFuture<EconomyResult> refund = new CompletableFuture<>();
		private int affordabilityChecks;
		private int withdrawals;
		private int deposits;

		private ControlledEconomyProvider(boolean nativeAsync) {
			this.nativeAsync = nativeAsync;
		}

		@Override
		public boolean nativeAsync() {
			return nativeAsync;
		}

		@Override
		public CompletionStage<EconomyResult> canAfford(EconomyPlayer player, BigDecimal amount) {
			affordabilityChecks++;
			return affordability;
		}

		@Override
		public CompletionStage<EconomyResult> withdraw(EconomyPlayer player, BigDecimal amount) {
			withdrawals++;
			return withdrawal;
		}

		@Override
		public CompletionStage<EconomyResult> deposit(EconomyPlayer player, BigDecimal amount) {
			deposits++;
			return refund;
		}

		@Override
		public String getName() {
			return "Controlled";
		}
	}
}
