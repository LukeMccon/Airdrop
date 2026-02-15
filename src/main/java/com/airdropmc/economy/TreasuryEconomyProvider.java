package com.airdropmc.economy;

import java.math.BigDecimal;
import java.util.Optional;

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
		if (service.isEmpty()) {
			return Optional.empty();
		}

		me.lokka30.treasury.api.economy.EconomyProvider provider = service.get().get();
		if (provider == null) {
			return Optional.empty();
		}

		return Optional.of(new TreasuryEconomyProvider(provider, service.get().registrarName()));
	}

	@Override
	public double getBalance(Player player) {
		try {
			PlayerAccount account = resolveAccount(player);
			return account.retrieveBalance(primaryCurrency).join().doubleValue();
		} catch (RuntimeException ex) {
			return 0.0;
		}
	}

	@Override
	public EconomyResult withdraw(Player player, double amount) {
		if (!Double.isFinite(amount) || Double.compare(amount, 0.0) < 0) {
			return EconomyResult.fail("Invalid withdrawal amount");
		}
		if (Double.compare(amount, 0.0) == 0) {
			return EconomyResult.ok();
		}

		try {
			PlayerAccount account = resolveAccount(player);
			BigDecimal amountValue = BigDecimal.valueOf(amount);
			BigDecimal balance = account.retrieveBalance(primaryCurrency).join();
			if (balance.compareTo(amountValue) < 0) {
				return EconomyResult.fail("Insufficient funds");
			}
			account.withdrawBalance(amountValue, CAUSE, primaryCurrency).join();
			return EconomyResult.ok();
		} catch (RuntimeException ex) {
			return EconomyResult.fail(ex.getMessage() != null ? ex.getMessage() : "Treasury withdrawal failed");
		}
	}

	@Override
	public EconomyResult deposit(Player player, double amount) {
		if (!Double.isFinite(amount) || Double.compare(amount, 0.0) < 0) {
			return EconomyResult.fail("Invalid deposit amount");
		}
		if (Double.compare(amount, 0.0) == 0) {
			return EconomyResult.ok();
		}

		try {
			PlayerAccount account = resolveAccount(player);
			account.depositBalance(BigDecimal.valueOf(amount), CAUSE, primaryCurrency).join();
			return EconomyResult.ok();
		} catch (RuntimeException ex) {
			return EconomyResult.fail(ex.getMessage() != null ? ex.getMessage() : "Treasury deposit failed");
		}
	}

	@Override
	public String getName() {
		return "Treasury (" + registrarName + ")";
	}

	private PlayerAccount resolveAccount(Player player) {
		return treasury.accountAccessor()
				.player()
				.withUniqueId(player.getUniqueId())
				.get()
				.join();
	}
}
