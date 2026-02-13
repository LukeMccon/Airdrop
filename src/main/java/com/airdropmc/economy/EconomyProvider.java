package com.airdropmc.economy;

import org.bukkit.entity.Player;

public interface EconomyProvider {

	double getBalance(Player player);

	EconomyResult withdraw(Player player, double amount);

	EconomyResult deposit(Player player, double amount);

	String getName();
}
