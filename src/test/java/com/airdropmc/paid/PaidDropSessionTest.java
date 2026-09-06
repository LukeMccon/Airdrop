package com.airdropmc.paid;

import com.airdropmc.economy.EconomyPlayer;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.economy.EconomyResult;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaidDropSessionTest {

	private ServerMock server;
	private PluginMock plugin;
	private PlayerMock player;
	private ControlledEconomyProvider economy;
	private List<Completion> completions;
	private UUID requestId;
	private final List<LogRecord> logs = new ArrayList<>();
	private final Handler logCapture = new Handler() {
		@Override
		public void publish(LogRecord logRecord) {
			logs.add(logRecord);
		}

		@Override
		public void flush() {
			// Records are captured immediately in memory.
		}

		@Override
		public void close() {
			// The capture handler owns no resources to close.
		}
	};

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		plugin = MockBukkit.createMockPlugin("PaidSessionHarness");
		player = server.addPlayer("Luke");
		economy = new ControlledEconomyProvider();
		completions = new ArrayList<>();
		requestId = UUID.randomUUID();
		plugin.getLogger().addHandler(logCapture);
	}

	@AfterEach
	void tearDown() {
		plugin.getLogger().removeHandler(logCapture);
		MockBukkit.unmock();
	}

	@Test
	void ambiguousWithdrawalWarnsWithCorrelationWithoutDebugLogging() {
		PaidDropSession session = session();
		session.start();
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		economy.withdrawal.completeExceptionally(new IllegalStateException("provider offline"));
		server.getScheduler().performTicks(PaidDropSession.PAYMENT_TIMEOUT_TICKS + 1L);

		assertEquals(1, warnings().size());
		assertWarning(warnings().getFirst(), "WITHDRAWAL", "UNKNOWN");
		assertTrue(warnings().getFirst().contains("provider offline"));
		assertEquals(1, completions.size());
		assertEquals(1, economy.withdrawals);
		assertEquals(0, economy.deposits);
	}

	@ParameterizedTest
	@EnumSource(value = EconomyResult.Outcome.class, names = {"REJECTED", "UNKNOWN"})
	void unsuccessfulRefundWarnsOnceWithBoundedSanitizedDiagnostics(EconomyResult.Outcome outcome) {
		economy.name = "§aControlled\npassword=hidden /plugins/private.yml";
		PaidDropSession session = chargedSession();
		session.refund();
		String unsafe = "deposit failed\ntoken=secret /server/private.yml " + "details ".repeat(100);
		economy.refund.complete(outcome == EconomyResult.Outcome.REJECTED
				? EconomyResult.rejected(unsafe) : EconomyResult.unknown(unsafe));
		server.getScheduler().performTicks(PaidDropSession.PAYMENT_TIMEOUT_TICKS + 1L);

		assertEquals(1, warnings().size());
		String warning = warnings().getFirst();
		assertWarning(warning, "REFUND", outcome.name());
		assertTrue(warning.contains("deposit failed"));
		for (String forbidden : List.of("hidden", "secret", "/plugins/", "/server/", "§", "\n")) {
			assertFalse(warning.contains(forbidden), warning);
		}
		assertTrue(warning.length() < 600, warning);
		assertEquals(1, economy.deposits);
		assertEquals(2, completions.size());
	}

	@Test
	void withdrawalTimeoutAndLateSuccessRemainVisibleWithoutRecovery() {
		PaidDropSession session = session();
		session.start();
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		server.getScheduler().performTicks(PaidDropSession.PAYMENT_TIMEOUT_TICKS);
		assertEquals(1, warnings().size());
		assertWarning(warnings().getFirst(), "WITHDRAWAL", "UNKNOWN");
		economy.withdrawal.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		assertEquals(2, warnings().size());
		assertWarning(warnings().getLast(), "WITHDRAWAL", "SUCCESS");
		assertTrue(warnings().getLast().contains("late"));
		assertEquals(1, completions.size());
		assertEquals(0, economy.deposits);
	}

	@Test
	void refundTimeoutWarnsWithoutRetrying() {
		PaidDropSession session = chargedSession();
		session.refund();
		server.getScheduler().performTicks(PaidDropSession.PAYMENT_TIMEOUT_TICKS + 1L);

		assertEquals(1, warnings().size());
		assertWarning(warnings().getFirst(), "REFUND", "UNKNOWN");
		assertEquals(1, economy.deposits);
		assertEquals(2, completions.size());
	}

	@Test
	void stoppingOutstandingWithdrawalWarnsOnce() {
		PaidDropSession session = session();
		session.start();
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		session.stop();
		session.stop();

		assertEquals(1, warnings().size());
		assertWarning(warnings().getFirst(), "WITHDRAWAL", "UNKNOWN");
		assertTrue(completions.isEmpty());
		assertEquals(0, economy.deposits);
	}

	@ParameterizedTest
	@EnumSource(value = PaidDropSession.Operation.class, names = {"WITHDRAWAL", "REFUND"})
	void lateAsyncSuccessAfterSchedulerShutdownRetainsCorrelation(PaidDropSession.Operation operation)
			throws Exception {
		PaidDropSession session;
		if (operation == PaidDropSession.Operation.REFUND) {
			session = chargedSession();
			session.refund();
		} else {
			session = session();
			session.start();
			economy.affordability.complete(EconomyResult.ok());
			server.getScheduler().performOneTick();
		}
		session.stop();
		int completedBeforeStop = completions.size();
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(scheduler.runTask(eq(plugin), any(Runnable.class)))
				.thenThrow(new IllegalStateException("plugin disabled"));

		CompletableFuture.runAsync(() -> {
			try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class, Mockito.CALLS_REAL_METHODS)) {
				bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
				assertFalse(Bukkit.isPrimaryThread());
				(operation == PaidDropSession.Operation.REFUND ? economy.refund : economy.withdrawal)
						.complete(EconomyResult.ok());
			}
		}).get(5, TimeUnit.SECONDS);

		assertEquals(2, warnings().size());
		assertWarning(warnings().getLast(), operation.name(), "SUCCESS");
		assertTrue(warnings().getLast().contains("Could not marshal"));
		assertEquals(PaidDropSession.State.STOPPED, session.state());
		assertEquals(completedBeforeStop, completions.size());
		assertEquals(1, economy.withdrawals);
		assertEquals(operation == PaidDropSession.Operation.REFUND ? 1 : 0, economy.deposits);
	}

	@Test
	void successfulPaymentAndRefundDoNotWarn() {
		PaidDropSession session = chargedSession();
		session.refund();
		economy.refund.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		assertTrue(warnings().isEmpty());
	}

	@Test
	void brokenProviderLabelCannotPreventAnAmbiguousPaymentResult() {
		economy.nameFailure = new IllegalStateException("unavailable label");
		PaidDropSession session = session();
		session.start();
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		economy.withdrawal.complete(EconomyResult.unknown("offline"));
		server.getScheduler().performOneTick();

		assertEquals(EconomyResult.Outcome.UNKNOWN, completions.getFirst().result().outcome());
		assertEquals(1, warnings().size());
		assertTrue(warnings().getFirst().contains("provider=unknown"));
	}

	private List<String> warnings() {
		return logs.stream().filter(logRecord -> logRecord.getLevel().intValue() >= Level.WARNING.intValue())
				.map(LogRecord::getMessage).toList();
	}

	private void assertWarning(String warning, String operation, String result) {
		for (String expected : List.of("request=" + requestId, "player=" + player.getUniqueId(),
				"amount=10", "provider=Controlled", "operation=" + operation, "result=" + result,
				"no automatic retry or refund")) {
			assertTrue(warning.contains(expected), "Missing " + expected + " in " + warning);
		}
	}

	@Test
	void confirmedAffordabilityAndWithdrawalReportOneChargeOnPrimaryThread() {
		PaidDropSession session = session();

		session.start();
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		economy.withdrawal.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		assertEquals(PaidDropSession.State.CHARGED, session.state());
		assertEquals(List.of(new Completion(
				PaidDropSession.Operation.WITHDRAWAL, EconomyResult.ok())), completions);
		assertEquals(1, economy.withdrawals);
		assertTrue(completions.getFirst().primaryThread());
		assertNull(player.nextComponentMessage(), "the economy helper must never send chat");
	}

	@Test
	void backgroundProviderCallbacksAreMarshalledBeforeStateMutation() throws Exception {
		PaidDropSession session = session();
		session.start();

		Thread affordability = new Thread(
				() -> economy.affordability.complete(EconomyResult.ok()),
				"economy-affordability");
		affordability.start();
		affordability.join();
		assertEquals(PaidDropSession.State.CHECKING, session.state());
		assertEquals(0, economy.withdrawals);

		server.getScheduler().performOneTick();
		assertEquals(PaidDropSession.State.WITHDRAWING, session.state());
		Thread withdrawal = new Thread(
				() -> economy.withdrawal.complete(EconomyResult.ok()),
				"economy-withdrawal");
		withdrawal.start();
		withdrawal.join();
		assertTrue(completions.isEmpty());

		server.getScheduler().performOneTick();
		assertEquals(PaidDropSession.State.CHARGED, session.state());
		assertTrue(completions.getFirst().primaryThread());
	}

	@Test
	void insufficientFundsStopsBeforeWithdrawal() {
		PaidDropSession session = session();

		session.start();
		economy.affordability.complete(EconomyResult.rejected("insufficient"));
		server.getScheduler().performOneTick();

		assertEquals(PaidDropSession.State.TERMINAL, session.state());
		assertEquals(PaidDropSession.Operation.AFFORDABILITY, completions.getFirst().operation());
		assertEquals(EconomyResult.Outcome.REJECTED, completions.getFirst().result().outcome());
		assertEquals(0, economy.withdrawals);
	}

	@Test
	void withdrawalTimeoutIsAmbiguousAndLateSuccessIsIgnored() {
		PaidDropSession session = session();
		session.start();
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		server.getScheduler().performTicks(PaidDropSession.PAYMENT_TIMEOUT_TICKS);

		assertEquals(PaidDropSession.State.TERMINAL, session.state());
		assertEquals(PaidDropSession.Operation.WITHDRAWAL, completions.getFirst().operation());
		assertEquals(EconomyResult.Outcome.UNKNOWN, completions.getFirst().result().outcome());

		economy.withdrawal.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		assertEquals(1, completions.size());
		assertEquals(0, economy.deposits);
	}

	@Test
	void timeoutSchedulerRejectionTerminatesBeforeProviderInvocation() {
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong()))
				.thenThrow(new IllegalStateException("scheduler rejected task"));
		PaidDropSession session = session();

		try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class, Mockito.CALLS_REAL_METHODS)) {
			bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
			assertDoesNotThrow(session::start);
			economy.affordability.complete(EconomyResult.ok());
		}

		assertEquals(PaidDropSession.State.TERMINAL, session.state());
		assertEquals(0, economy.withdrawals);
		assertEquals(1, completions.size());
		assertEquals(PaidDropSession.Operation.AFFORDABILITY, completions.getFirst().operation());
		assertEquals(EconomyResult.Outcome.UNKNOWN, completions.getFirst().result().outcome());
	}

	@Test
	void exceptionalAndNullProviderResultsBecomeUnknown() {
		PaidDropSession exceptional = session();
		exceptional.start();
		economy.affordability.completeExceptionally(new IllegalStateException("offline"));
		server.getScheduler().performOneTick();

		assertEquals(EconomyResult.Outcome.UNKNOWN, completions.getFirst().result().outcome());

		completions.clear();
		economy = ControlledEconomyProvider.withNullAffordabilityResult();
		PaidDropSession nullResult = session();
		nullResult.start();
		server.getScheduler().performOneTick();

		assertEquals(EconomyResult.Outcome.UNKNOWN, completions.getFirst().result().outcome());
	}

	@Test
	void nullStageAndProviderThrowBecomeUnknown() {
		economy = ControlledEconomyProvider.withNullAffordabilityStage();
		PaidDropSession nullStage = session();
		nullStage.start();
		server.getScheduler().performOneTick();

		assertEquals(EconomyResult.Outcome.UNKNOWN, completions.getFirst().result().outcome());

		completions.clear();
		economy = ControlledEconomyProvider.withThrowingAffordability();
		PaidDropSession throwing = session();
		throwing.start();
		server.getScheduler().performOneTick();

		assertEquals(EconomyResult.Outcome.UNKNOWN, completions.getFirst().result().outcome());
	}

	@Test
	void confirmedChargeCanStartExactlyOneRefund() {
		PaidDropSession session = chargedSession();
		completions.clear();

		assertTrue(session.refund());
		assertFalse(session.refund());
		economy.refund.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		assertEquals(1, economy.deposits);
		assertEquals(PaidDropSession.State.TERMINAL, session.state());
		assertEquals(List.of(new Completion(
				PaidDropSession.Operation.REFUND, EconomyResult.ok())), completions);
	}

	@Test
	void refundTimeoutIsUnknownAndNeverRetried() {
		PaidDropSession session = chargedSession();
		completions.clear();
		session.refund();

		server.getScheduler().performTicks(PaidDropSession.PAYMENT_TIMEOUT_TICKS);
		economy.refund.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();

		assertEquals(1, economy.deposits);
		assertEquals(1, completions.size());
		assertEquals(EconomyResult.Outcome.UNKNOWN, completions.getFirst().result().outcome());
	}

	@Test
	void stopCancelsOutstandingWorkAndSuppressesLateCallbacks() {
		PaidDropSession session = session();
		session.start();

		assertEquals(PaidDropSession.State.CHECKING, session.stop());
		assertEquals(PaidDropSession.State.STOPPED, session.state());
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performTicks(PaidDropSession.PAYMENT_TIMEOUT_TICKS + 1L);

		assertTrue(completions.isEmpty());
		assertEquals(0, economy.withdrawals);
	}

	@Test
	void rejectsNonPositiveAmountsAndRepeatedStart() {
		assertThrows(IllegalArgumentException.class, () -> new PaidDropSession(
				plugin, economy, playerIdentity(), BigDecimal.ZERO, requestId, this::record));
		PaidDropSession session = session();
		session.start();
		assertThrows(IllegalStateException.class, session::start);
	}

	private PaidDropSession chargedSession() {
		PaidDropSession session = session();
		session.start();
		economy.affordability.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		economy.withdrawal.complete(EconomyResult.ok());
		server.getScheduler().performOneTick();
		return session;
	}

	private PaidDropSession session() {
		return new PaidDropSession(
				plugin, economy, playerIdentity(), BigDecimal.TEN, requestId, this::record);
	}

	private EconomyPlayer playerIdentity() {
		return new EconomyPlayer(player.getUniqueId(), player.getName());
	}

	private void record(PaidDropSession.Operation operation, EconomyResult result) {
		completions.add(new Completion(operation, result, Bukkit.isPrimaryThread()));
	}

	private record Completion(
			PaidDropSession.Operation operation,
			EconomyResult result,
			boolean primaryThread) {
		private Completion(PaidDropSession.Operation operation, EconomyResult result) {
			this(operation, result, true);
		}
	}

	private static final class ControlledEconomyProvider implements EconomyProvider {

		private CompletionStage<EconomyResult> affordabilityStage;
		private RuntimeException affordabilityFailure;
		private String name = "Controlled";
		private RuntimeException nameFailure;
		private final CompletableFuture<EconomyResult> affordability;
		private final CompletableFuture<EconomyResult> withdrawal = new CompletableFuture<>();
		private final CompletableFuture<EconomyResult> refund = new CompletableFuture<>();
		private int withdrawals;
		private int deposits;

		private ControlledEconomyProvider() {
			affordability = new CompletableFuture<>();
			affordabilityStage = affordability;
		}

		private static ControlledEconomyProvider withNullAffordabilityResult() {
			ControlledEconomyProvider provider = new ControlledEconomyProvider();
			provider.affordabilityStage = CompletableFuture.completedFuture(null);
			return provider;
		}

		private static ControlledEconomyProvider withNullAffordabilityStage() {
			ControlledEconomyProvider provider = new ControlledEconomyProvider();
			provider.affordabilityStage = null;
			return provider;
		}

		private static ControlledEconomyProvider withThrowingAffordability() {
			ControlledEconomyProvider provider = new ControlledEconomyProvider();
			provider.affordabilityFailure = new IllegalStateException("provider offline");
			return provider;
		}

		@Override
		public boolean nativeAsync() {
			return true;
		}

		@Override
		public CompletionStage<EconomyResult> canAfford(EconomyPlayer player, BigDecimal amount) {
			if (affordabilityFailure != null) {
				throw affordabilityFailure;
			}
			return affordabilityStage;
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
			if (nameFailure != null) {
				throw nameFailure;
			}
			return name;
		}
	}
}
