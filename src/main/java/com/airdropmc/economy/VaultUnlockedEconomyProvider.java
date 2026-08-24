package com.airdropmc.economy;

import net.milkbowl.vault2.economy.AsyncEconomy;
import net.milkbowl.vault2.economy.EconomyResponse;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class VaultUnlockedEconomyProvider implements EconomyProvider {

	private static final String CALLER = "Airdrop";

	private final AsyncEconomy economy;
	private final String name;

	public VaultUnlockedEconomyProvider(AsyncEconomy economy, String name) {
		this.economy = Objects.requireNonNull(economy, "economy");
		this.name = Objects.requireNonNull(name, "name");
	}

	@Override
	public boolean nativeAsync() {
		return true;
	}

	@Override
	public CompletionStage<EconomyResult> canAfford(EconomyPlayer player, BigDecimal amount) {
		return invoke(player, amount, () -> economy.canWithdraw(CALLER, player.uniqueId(), amount));
	}

	@Override
	public CompletionStage<EconomyResult> withdraw(EconomyPlayer player, BigDecimal amount) {
		return invoke(player, amount, () -> economy.withdraw(CALLER, player.uniqueId(), amount));
	}

	@Override
	public CompletionStage<EconomyResult> deposit(EconomyPlayer player, BigDecimal amount) {
		return invoke(player, amount, () -> economy.deposit(CALLER, player.uniqueId(), amount));
	}

	@Override
	public double getBalance(Player player) {
		throw new UnsupportedOperationException("VaultUnlocked balance is asynchronous");
	}

	@Override
	public EconomyResult withdraw(Player player, double amount) {
		throw new UnsupportedOperationException("VaultUnlocked withdrawal is asynchronous");
	}

	@Override
	public EconomyResult deposit(Player player, double amount) {
		throw new UnsupportedOperationException("VaultUnlocked deposit is asynchronous");
	}

	@Override
	public String getName() {
		return name;
	}

	private CompletionStage<EconomyResult> invoke(EconomyPlayer player, BigDecimal amount,
			Supplier<CompletableFuture<EconomyResponse>> operation) {
		Objects.requireNonNull(player, "player");
		if (amount == null || amount.signum() < 0) {
			return CompletableFuture.completedFuture(EconomyResult.rejected("Amount must be non-negative"));
		}
		if (amount.signum() == 0) {
			return CompletableFuture.completedFuture(EconomyResult.ok());
		}

		try {
			CompletableFuture<EconomyResponse> future = operation.get();
			if (future == null) {
				return CompletableFuture.completedFuture(EconomyResult.unknown(
						"Economy provider returned no future"));
			}
			return future.handle((response, failure) -> {
				if (failure != null) {
					return EconomyResult.unknown(message(failure));
				}
				if (response == null) {
					return EconomyResult.unknown("Economy provider returned no response");
				}
				return response.transactionSuccess()
						? EconomyResult.ok()
						: EconomyResult.rejected(response.errorMessage);
			});
		} catch (RuntimeException failure) {
			return CompletableFuture.completedFuture(EconomyResult.unknown(message(failure)));
		}
	}

	private static String message(Throwable failure) {
		Throwable cause = failure instanceof CompletionException && failure.getCause() != null
				? failure.getCause()
				: failure;
		return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
	}
}
