package com.airdropmc.paid;

import com.airdropmc.economy.EconomyPlayer;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.economy.EconomyResult;
import com.airdropmc.internal.diagnostics.AirdropDiagnostics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Narrow economy sequencer for one priced drop.
 *
 * <p>This helper owns no admission lease, crate, controller, command, or chat
 * behavior. Every provider completion is normalized and returned to its owner
 * on the primary server thread.</p>
 */
@ApiStatus.Internal
public final class PaidDropSession {

	public static final long PAYMENT_TIMEOUT_TICKS = 100L;

	public enum Operation {
		AFFORDABILITY,
		WITHDRAWAL,
		REFUND
	}

	public enum State {
		NEW,
		CHECKING,
		WITHDRAWING,
		CHARGED,
		REFUNDING,
		TERMINAL,
		STOPPED
	}

	@FunctionalInterface
	public interface Listener {
		void complete(Operation operation, EconomyResult result);
	}

	private final Plugin plugin;
	private final EconomyProvider economy;
	private final EconomyPlayer player;
	private final BigDecimal amount;
	private final UUID requestId;
	private final Listener listener;
	private final Logger logger;
	private final String providerName;

	private State state = State.NEW;
	private BukkitTask timeoutTask;

	public PaidDropSession(
			Plugin plugin,
			EconomyProvider economy,
			EconomyPlayer player,
			BigDecimal amount,
			UUID requestId,
			Listener listener) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.economy = Objects.requireNonNull(economy, "economy");
		this.player = Objects.requireNonNull(player, "player");
		this.amount = Objects.requireNonNull(amount, "amount");
		this.requestId = Objects.requireNonNull(requestId, "requestId");
		this.listener = Objects.requireNonNull(listener, "listener");
		if (amount.signum() <= 0) {
			throw new IllegalArgumentException("amount must be positive");
		}
		Logger pluginLogger = plugin.getLogger();
		this.logger = pluginLogger != null
				? pluginLogger
				: Logger.getLogger(PaidDropSession.class.getName());
		this.providerName = providerLabel(economy);
	}

	public void start() {
		requirePrimaryThread("start");
		if (state != State.NEW) {
			throw new IllegalStateException("Paid drop session already started");
		}
		state = State.CHECKING;
		startOperation(
				Operation.AFFORDABILITY,
				State.CHECKING,
				() -> economy.canAfford(player, amount));
	}

	/** Starts one best-effort refund after a confirmed withdrawal. */
	public boolean refund() {
		requirePrimaryThread("refund");
		if (state != State.CHARGED) {
			return false;
		}
		state = State.REFUNDING;
		startOperation(Operation.REFUND, State.REFUNDING, () -> economy.deposit(player, amount));
		return true;
	}

	/**
	 * Stops this helper without inventing an economy result.
	 *
	 * @return phase that was active immediately before stopping
	 */
	public State stop() {
		requirePrimaryThread("stop");
		State previous = state;
		if (state != State.STOPPED) {
			cancelTimeout();
			state = State.STOPPED;
			if (previous == State.WITHDRAWING || previous == State.REFUNDING) {
				warnPayment(previous == State.WITHDRAWING ? Operation.WITHDRAWAL : Operation.REFUND,
						EconomyResult.unknown("Plugin stopped before the operation was confirmed"));
			}
		}
		return previous;
	}

	public State state() {
		return state;
	}

	private void startOperation(
			Operation operation,
			State expectedState,
			Supplier<CompletionStage<EconomyResult>> invocation) {
		try {
			timeoutTask = Bukkit.getScheduler().runTaskLater(
					plugin,
					() -> acceptTimeout(operation, expectedState),
					PAYMENT_TIMEOUT_TICKS);
		} catch (RuntimeException schedulingFailure) {
			accept(operation, notStarted(operation, schedulingFailure));
			return;
		}

		CompletionStage<EconomyResult> stage;
		try {
			stage = invocation.get();
		} catch (RuntimeException | LinkageError failure) {
			post(operation, EconomyResult.unknown(message(failure)));
			return;
		}
		if (stage == null) {
			post(operation, EconomyResult.unknown("Economy provider returned no stage"));
			return;
		}

		stage.whenComplete((result, failure) -> post(operation, normalize(result, failure)));
	}

	private void post(Operation operation, EconomyResult result) {
		try {
			Bukkit.getScheduler().runTask(plugin, () -> accept(operation, result));
		} catch (RuntimeException schedulingFailure) {
			if (Bukkit.isPrimaryThread()) {
				accept(operation, result);
				return;
			}
			if (operation != Operation.AFFORDABILITY) {
				warnPayment(operation, new EconomyResult(result.outcome(),
						"Could not marshal economy result to the primary thread: " + result.message()));
				return;
			}
			logger.log(Level.WARNING,
					"Could not marshal economy result to the primary thread", schedulingFailure);
		}
	}

	private void accept(Operation operation, EconomyResult result) {
		if (!matches(operation)) {
			if (operation != Operation.AFFORDABILITY && result.outcome() == EconomyResult.Outcome.SUCCESS) {
				warnPayment(operation, new EconomyResult(result.outcome(), "Ignoring late successful result"));
			}
			return;
		}
		cancelTimeout();
		switch (operation) {
			case AFFORDABILITY -> acceptAffordability(result);
			case WITHDRAWAL -> acceptWithdrawal(result);
			case REFUND -> acceptRefund(result);
		}
	}

	private void acceptAffordability(EconomyResult result) {
		if (result.outcome() != EconomyResult.Outcome.SUCCESS) {
			state = State.TERMINAL;
			listener.complete(Operation.AFFORDABILITY, result);
			return;
		}
		state = State.WITHDRAWING;
		startOperation(Operation.WITHDRAWAL, State.WITHDRAWING,
				() -> economy.withdraw(player, amount));
	}

	private void acceptWithdrawal(EconomyResult result) {
		state = result.outcome() == EconomyResult.Outcome.SUCCESS
				? State.CHARGED
				: State.TERMINAL;
		if (result.outcome() == EconomyResult.Outcome.UNKNOWN) {
			warnPayment(Operation.WITHDRAWAL, result);
		}
		listener.complete(Operation.WITHDRAWAL, result);
	}

	private void acceptRefund(EconomyResult result) {
		state = State.TERMINAL;
		if (result.outcome() != EconomyResult.Outcome.SUCCESS) {
			warnPayment(Operation.REFUND, result);
		}
		listener.complete(Operation.REFUND, result);
	}

	private void acceptTimeout(Operation operation, State expectedState) {
		if (state != expectedState) {
			return;
		}
		timeoutTask = null;
		accept(operation, EconomyResult.unknown(
				"Economy " + operation.name().toLowerCase() + " operation timed out"));
	}

	private void warnPayment(Operation operation, EconomyResult result) {
		logger.warning("Paid drop requires reconciliation: request=" + requestId
				+ " player=" + player.uniqueId() + " amount=" + amount
				+ " provider=" + providerName + " operation=" + operation + " result=" + result.outcome()
				+ " detail=" + AirdropDiagnostics.sanitizeLabel(result.message())
				+ "; no automatic retry or refund");
	}

	private static String providerLabel(EconomyProvider economy) {
		try {
			return AirdropDiagnostics.sanitizeLabel(economy.getName());
		} catch (RuntimeException | LinkageError unavailable) {
			return "unknown";
		}
	}

	private boolean matches(Operation operation) {
		return switch (operation) {
			case AFFORDABILITY -> state == State.CHECKING;
			case WITHDRAWAL -> state == State.WITHDRAWING;
			case REFUND -> state == State.REFUNDING;
		};
	}

	private void cancelTimeout() {
		if (timeoutTask != null && !timeoutTask.isCancelled()) {
			timeoutTask.cancel();
		}
		timeoutTask = null;
	}

	private static EconomyResult normalize(EconomyResult result, Throwable failure) {
		if (failure != null) {
			return EconomyResult.unknown(message(failure));
		}
		return result != null
				? result
				: EconomyResult.unknown("Economy provider returned no result");
	}

	private static EconomyResult notStarted(Operation operation, Throwable failure) {
		String diagnostic = "Could not schedule economy "
				+ operation.name().toLowerCase() + " operation: " + message(failure);
		return operation == Operation.AFFORDABILITY
				? EconomyResult.unknown(diagnostic)
				: EconomyResult.rejected(diagnostic);
	}

	private static String message(Throwable failure) {
		Throwable required = Objects.requireNonNull(failure, "failure");
		return required.getMessage() == null
				? required.getClass().getSimpleName()
				: required.getMessage();
	}

	private static void requirePrimaryThread(String method) {
		if (!Bukkit.isPrimaryThread()) {
			throw new IllegalStateException(
					"PaidDropSession." + method + " must run on the primary server thread");
		}
	}
}
