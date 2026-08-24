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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
		setStatic("shuttingDown", false);
	}

	@AfterEach
	void tearDown() throws Exception {
		admission.clear();
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
	void zeroPriceBypassesEconomy() {
		PaidDropSession session = session(BigDecimal.ZERO);

		session.start();

		assertEquals(1, spawns.get());
		assertEquals(0, economy.affordabilityChecks);
		assertEquals(0, economy.withdrawals);
		assertNull(player.nextComponentMessage());
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
