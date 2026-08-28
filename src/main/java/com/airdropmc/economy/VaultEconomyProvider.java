package com.airdropmc.economy;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class VaultEconomyProvider implements EconomyProvider {

	private final Economy vault;

	public VaultEconomyProvider(Economy vault) {
		this.vault = vault;
	}

	@Override
	public boolean nativeAsync() {
		return false;
	}

	@Override
	public CompletionStage<EconomyResult> canAfford(EconomyPlayer player, BigDecimal amount) {
		EconomyResult invalid = validateAmount(amount);
		if (invalid != null) {
			return CompletableFuture.completedFuture(invalid);
		}
		if (amount.signum() == 0) {
			return CompletableFuture.completedFuture(EconomyResult.ok());
		}
		return invoke(() -> vault.has(resolve(player), amount.doubleValue())
				? EconomyResult.ok()
				: EconomyResult.rejected("Insufficient funds"));
	}

	@Override
	public CompletionStage<EconomyResult> withdraw(EconomyPlayer player, BigDecimal amount) {
		EconomyResult invalid = validateAmount(amount);
		if (invalid != null) {
			return CompletableFuture.completedFuture(invalid);
		}
		if (amount.signum() == 0) {
			return CompletableFuture.completedFuture(EconomyResult.ok());
		}
		return invoke(() -> fromResponse(vault.withdrawPlayer(resolve(player), amount.doubleValue())));
	}

	@Override
	public CompletionStage<EconomyResult> deposit(EconomyPlayer player, BigDecimal amount) {
		EconomyResult invalid = validateAmount(amount);
		if (invalid != null) {
			return CompletableFuture.completedFuture(invalid);
		}
		if (amount.signum() == 0) {
			return CompletableFuture.completedFuture(EconomyResult.ok());
		}
		return invoke(() -> fromResponse(vault.depositPlayer(resolve(player), amount.doubleValue())));
	}

	@Override
	public String getName() {
		return vault.getName();
	}

	private OfflinePlayer resolve(EconomyPlayer player) {
		return Bukkit.getOfflinePlayer(player.uniqueId());
	}

	private static CompletionStage<EconomyResult> invoke(Supplier<EconomyResult> operation) {
		try {
			return CompletableFuture.completedFuture(operation.get());
		} catch (RuntimeException failure) {
			return CompletableFuture.completedFuture(EconomyResult.unknown(message(failure)));
		}
	}

	private static EconomyResult fromResponse(EconomyResponse response) {
		if (response == null) {
			return EconomyResult.unknown("Economy provider returned no response");
		}
		return response.transactionSuccess()
				? EconomyResult.ok()
				: EconomyResult.rejected(response.errorMessage);
	}

	private static EconomyResult validateAmount(BigDecimal amount) {
		if (amount == null || amount.signum() < 0) {
			return EconomyResult.rejected("Amount must be non-negative");
		}
		return null;
	}

	private static String message(Throwable failure) {
		return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
	}
}
