package com.airdropmc.economy;

import java.math.BigDecimal;
import java.util.concurrent.CompletionStage;

import org.bukkit.entity.Player;

public interface EconomyProvider {

	boolean nativeAsync();

	CompletionStage<EconomyResult> canAfford(EconomyPlayer player, BigDecimal amount);

	CompletionStage<EconomyResult> withdraw(EconomyPlayer player, BigDecimal amount);

	CompletionStage<EconomyResult> deposit(EconomyPlayer player, BigDecimal amount);

	@Deprecated
	double getBalance(Player player);

	@Deprecated
	EconomyResult withdraw(Player player, double amount);

	@Deprecated
	EconomyResult deposit(Player player, double amount);

	String getName();
}
