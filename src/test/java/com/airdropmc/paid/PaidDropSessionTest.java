package com.airdropmc.paid;

import com.airdropmc.economy.EconomyPlayer;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.economy.EconomyResult;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaidDropSessionTest {

	private ServerMock server;
	private PluginMock plugin;
	private PlayerMock player;
	private ControlledEconomyProvider economy;
	private List<Completion> completions;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		plugin = MockBukkit.createMockPlugin("PaidSessionHarness");
		player = server.addPlayer("Luke");
		economy = new ControlledEconomyProvider();
		completions = new ArrayList<>();
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
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
				plugin, economy, playerIdentity(), BigDecimal.ZERO, this::record));
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
				plugin, economy, playerIdentity(), BigDecimal.TEN, this::record);
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
			return "Controlled";
		}
	}
}
