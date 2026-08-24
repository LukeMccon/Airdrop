package com.airdropmc.economy;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import me.lokka30.treasury.api.common.Cause;
import me.lokka30.treasury.api.common.NamespacedKey;
import me.lokka30.treasury.api.common.service.Service;
import me.lokka30.treasury.api.common.service.ServiceRegistry;
import me.lokka30.treasury.api.economy.account.PlayerAccount;
import me.lokka30.treasury.api.economy.currency.Currency;
import org.bukkit.entity.Player;

public class TreasuryEconomyProvider implements EconomyProvider {

	private static final Cause<?> CAUSE = Cause.plugin(NamespacedKey.of("airdrop", "economy"));
	private final me.lokka30.treasury.api.economy.EconomyProvider treasury;
	private final Currency primaryCurrency;
	private final String registrarName;

	private TreasuryEconomyProvider(me.lokka30.treasury.api.economy.EconomyProvider treasury, String registrarName) {
		this.treasury = treasury;
		this.primaryCurrency = treasury.getPrimaryCurrency();
		this.registrarName = registrarName;
	}

	public static Optional<TreasuryEconomyProvider> fromServiceRegistry() {
		Optional<Service<me.lokka30.treasury.api.economy.EconomyProvider>> service =
				ServiceRegistry.INSTANCE.serviceFor(me.lokka30.treasury.api.economy.EconomyProvider.class);
		if (service.isEmpty() || service.get().get() == null) {
			return Optional.empty();
		}
		return Optional.of(new TreasuryEconomyProvider(service.get().get(), service.get().registrarName()));
	}

	@Override
	public boolean nativeAsync() {
		return false;
	}

	@Override
	public CompletionStage<EconomyResult> canAfford(EconomyPlayer player, BigDecimal amount) {
		throw new UnsupportedOperationException("Treasury is pending removal");
	}

	@Override
	public CompletionStage<EconomyResult> withdraw(EconomyPlayer player, BigDecimal amount) {
		throw new UnsupportedOperationException("Treasury is pending removal");
	}

	@Override
	public CompletionStage<EconomyResult> deposit(EconomyPlayer player, BigDecimal amount) {
		throw new UnsupportedOperationException("Treasury is pending removal");
	}

	@Override
	public double getBalance(Player player) {
		try {
			return resolveAccount(player).retrieveBalance(primaryCurrency).join().doubleValue();
		} catch (RuntimeException ex) {
			return 0.0;
		}
	}

	@Override
	public EconomyResult withdraw(Player player, double amount) {
		if (!Double.isFinite(amount) || Double.compare(amount, 0.0) < 0) {
			return EconomyResult.rejected("Invalid withdrawal amount");
		}
		if (Double.compare(amount, 0.0) == 0) {
			return EconomyResult.ok();
		}
		try {
			PlayerAccount account = resolveAccount(player);
			BigDecimal value = BigDecimal.valueOf(amount);
			if (account.retrieveBalance(primaryCurrency).join().compareTo(value) < 0) {
				return EconomyResult.rejected("Insufficient funds");
			}
			account.withdrawBalance(value, CAUSE, primaryCurrency).join();
			return EconomyResult.ok();
		} catch (RuntimeException ex) {
			return EconomyResult.rejected(message(ex, "Treasury withdrawal failed"));
		}
	}

	@Override
	public EconomyResult deposit(Player player, double amount) {
		if (!Double.isFinite(amount) || Double.compare(amount, 0.0) < 0) {
			return EconomyResult.rejected("Invalid deposit amount");
		}
		if (Double.compare(amount, 0.0) == 0) {
			return EconomyResult.ok();
		}
		try {
			resolveAccount(player).depositBalance(BigDecimal.valueOf(amount), CAUSE, primaryCurrency).join();
			return EconomyResult.ok();
		} catch (RuntimeException ex) {
			return EconomyResult.rejected(message(ex, "Treasury deposit failed"));
		}
	}

	@Override
	public String getName() {
		return "Treasury (" + registrarName + ")";
	}

	private PlayerAccount resolveAccount(Player player) {
		return treasury.accountAccessor().player().withUniqueId(player.getUniqueId()).get().join();
	}

	private static String message(RuntimeException failure, String fallback) {
		return failure.getMessage() == null ? fallback : failure.getMessage();
	}
}
