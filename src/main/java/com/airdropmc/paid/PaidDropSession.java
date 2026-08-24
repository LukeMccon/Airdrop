package com.airdropmc.paid;

import com.airdropmc.Airdrop;
import com.airdropmc.economy.EconomyPlayer;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.economy.EconomyResult;
import com.airdropmc.helpers.AirdropLogger;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.limits.DropAdmissionController;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PaidDropSession {

	public static final long PAYMENT_TIMEOUT_TICKS = 100L;

	private enum Phase {
		NEW,
		CHECKING,
		WITHDRAWING,
		FALLING,
		DELIVERED,
		CANCELLED,
		REFUNDING
	}

	private final Plugin plugin;
	private final EconomyProvider economy;
	private final EconomyPlayer player;
	private final BigDecimal amount;
	private final DropAdmissionController.Lease lease;
	private final Consumer<PaidDropSession> spawner;
	private final Logger logger;

	private Phase phase = Phase.NEW;
	private BukkitTask timeoutTask;
	private boolean withdrawalTimedOut;
	private boolean refundStarted;
	private boolean charged;
	private boolean failureMessageSent;

	public PaidDropSession(Plugin plugin, EconomyProvider economy, EconomyPlayer player, BigDecimal amount,
			DropAdmissionController.Lease lease, Consumer<PaidDropSession> spawner) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.economy = Objects.requireNonNull(economy, "economy");
		this.player = Objects.requireNonNull(player, "player");
		this.amount = Objects.requireNonNull(amount, "amount");
		this.lease = Objects.requireNonNull(lease, "lease");
		this.spawner = Objects.requireNonNull(spawner, "spawner");
		Logger pluginLogger = plugin.getLogger();
		this.logger = pluginLogger != null ? pluginLogger : Logger.getLogger(Airdrop.PLUGIN_NAME);
		if (amount.signum() < 0) {
			throw new IllegalArgumentException("amount must be non-negative");
		}
	}

	public void start() {
		if (phase != Phase.NEW) {
			throw new IllegalStateException("Paid drop session already started");
		}
		if (amount.signum() == 0) {
			spawn(false);
			return;
		}

		phase = Phase.CHECKING;
		startOperation(Phase.CHECKING, () -> economy.canAfford(player, amount), this::acceptAffordability);
	}

	public void landed() {
		if (phase == Phase.FALLING) {
			phase = Phase.DELIVERED;
		}
	}

	public void failed() {
		if (phase != Phase.FALLING) {
			return;
		}
		if (Airdrop.isShuttingDown()) {
			phase = Phase.CANCELLED;
			return;
		}
		if (!charged) {
			phase = Phase.CANCELLED;
			sendFailure();
			return;
		}
		startRefund("Falling crate failed before landing");
	}

	private void acceptAffordability(EconomyResult result) {
		if (phase != Phase.CHECKING) {
			return;
		}
		cancelTimeout();
		if (result.outcome() == EconomyResult.Outcome.SUCCESS) {
			phase = Phase.WITHDRAWING;
			startOperation(Phase.WITHDRAWING, () -> economy.withdraw(player, amount), this::acceptWithdrawal);
			return;
		}
		if (result.outcome() == EconomyResult.Outcome.REJECTED) {
			phase = Phase.CANCELLED;
			lease.close();
			sendError(MessageKey.ERROR_CANNOT_AFFORD, Map.of(
					"player", player.lastKnownName(),
					"price", amount.toPlainString()));
			return;
		}
		cancelWithoutCharge("Affordability check was inconclusive: " + result.message());
	}

	private void acceptWithdrawal(EconomyResult result) {
		if (phase == Phase.CANCELLED && withdrawalTimedOut) {
			if (result.outcome() == EconomyResult.Outcome.SUCCESS) {
				startRefund("Timed-out withdrawal later succeeded");
			}
			return;
		}
		if (phase != Phase.WITHDRAWING) {
			return;
		}
		cancelTimeout();
		if (result.outcome() == EconomyResult.Outcome.SUCCESS) {
			spawn(true);
			return;
		}
		cancelWithoutCharge("Withdrawal did not confirm success: " + result.message());
	}

	private void spawn(boolean charged) {
		if (Airdrop.isShuttingDown()) {
			phase = Phase.CANCELLED;
			lease.close();
			if (charged) {
				startRefund("Plugin began shutting down before spawn");
			}
			return;
		}

		phase = Phase.FALLING;
		this.charged = charged;
		try {
			spawner.accept(this);
			if (charged) {
				send(MessageKey.DROP_CHARGED, Map.of("amount", amount.toPlainString()));
			}
		} catch (RuntimeException failure) {
			lease.close();
			if (charged && phase == Phase.FALLING) {
				startRefund("Crate creation failed: " + message(failure));
			} else if (!charged) {
				phase = Phase.CANCELLED;
				sendFailure();
			}
		}
	}

	private void startRefund(String reason) {
		if (refundStarted || Airdrop.isShuttingDown()) {
			return;
		}
		refundStarted = true;
		phase = Phase.REFUNDING;
		AirdropLogger.warning(reason + " for paid drop requested by " + player.uniqueId());
		startOperation(Phase.REFUNDING, () -> economy.deposit(player, amount), this::acceptRefund);
	}

	private void acceptRefund(EconomyResult result) {
		if (phase != Phase.REFUNDING) {
			return;
		}
		cancelTimeout();
		phase = Phase.CANCELLED;
		if (result.outcome() == EconomyResult.Outcome.SUCCESS) {
			sendError(MessageKey.DROP_REFUNDED, Map.of());
			return;
		}
		sendFailure();
		AirdropLogger.warning("Paid drop refund was not confirmed for " + player.uniqueId()
				+ ": " + result.message());
	}

	private void startOperation(Phase expectedPhase, Supplier<CompletionStage<EconomyResult>> operation,
			Consumer<EconomyResult> completion) {
		CompletionStage<EconomyResult> stage;
		try {
			stage = operation.get();
		} catch (RuntimeException failure) {
			post(() -> completion.accept(EconomyResult.unknown(message(failure))));
			return;
		}
		if (stage == null) {
			post(() -> completion.accept(EconomyResult.unknown("Economy provider returned no stage")));
			return;
		}

		stage.whenComplete((result, failure) -> post(() -> completion.accept(
				failure == null && result != null
						? result
						: EconomyResult.unknown(failure == null
								? "Economy provider returned no result"
								: message(failure)))));

		if (economy.nativeAsync()) {
			timeoutTask = Bukkit.getScheduler().runTaskLater(plugin,
					() -> acceptTimeout(expectedPhase), PAYMENT_TIMEOUT_TICKS);
		}
	}

	private void acceptTimeout(Phase expectedPhase) {
		if (phase != expectedPhase) {
			return;
		}
		timeoutTask = null;
		if (expectedPhase == Phase.WITHDRAWING) {
			withdrawalTimedOut = true;
		}
		if (expectedPhase == Phase.REFUNDING) {
			phase = Phase.CANCELLED;
			sendFailure();
			AirdropLogger.warning("Paid drop refund timed out for " + player.uniqueId()
					+ "; it will not be retried automatically");
			return;
		}
		cancelWithoutCharge("Paid drop payment operation timed out");
	}

	private void cancelWithoutCharge(String reason) {
		phase = Phase.CANCELLED;
		lease.close();
		AirdropLogger.warning(reason + " for " + player.uniqueId());
		sendFailure();
	}

	private void sendFailure() {
		if (failureMessageSent) {
			return;
		}
		failureMessageSent = true;
		sendError(MessageKey.DROP_FAILED, Map.of());
	}

	private void cancelTimeout() {
		if (timeoutTask != null && !timeoutTask.isCancelled()) {
			timeoutTask.cancel();
		}
		timeoutTask = null;
	}

	private void post(Runnable action) {
		try {
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (!Airdrop.isShuttingDown() && plugin.isEnabled()) {
					action.run();
				}
			});
		} catch (RuntimeException failure) {
			logger.log(Level.WARNING,
					"Could not schedule paid drop economy result for " + player.uniqueId(), failure);
		}
	}

	private void send(MessageKey key, Map<String, String> placeholders) {
		Player online = Bukkit.getPlayer(player.uniqueId());
		if (online != null && online.isOnline()) {
			ChatHandler.send(online, key, placeholders);
		}
	}

	private void sendError(MessageKey key, Map<String, String> placeholders) {
		Player online = Bukkit.getPlayer(player.uniqueId());
		if (online != null && online.isOnline()) {
			ChatHandler.sendError(online, key, placeholders);
		}
	}

	private static String message(Throwable failure) {
		return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
	}
}
