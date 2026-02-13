package com.airdropmc.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;

public class VaultEconomyProvider implements EconomyProvider {

	private final Economy vault;

	public VaultEconomyProvider(Economy vault) {
		this.vault = vault;
	}

	@Override
	public double getBalance(Player player) {
		return vault.getBalance(player);
	}

	@Override
	public EconomyResult withdraw(Player player, double amount) {
		EconomyResponse response = vault.withdrawPlayer(player, amount);
		if (response.transactionSuccess()) {
			return EconomyResult.ok();
		}
		return EconomyResult.fail(response.errorMessage);
	}

	@Override
	public EconomyResult deposit(Player player, double amount) {
		EconomyResponse response = vault.depositPlayer(player, amount);
		if (response.transactionSuccess()) {
			return EconomyResult.ok();
		}
		return EconomyResult.fail(response.errorMessage);
	}

	@Override
	public String getName() {
		return vault.getName();
	}
}
