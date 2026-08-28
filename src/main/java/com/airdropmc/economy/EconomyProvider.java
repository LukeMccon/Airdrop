package com.airdropmc.economy;

import java.math.BigDecimal;
import java.util.concurrent.CompletionStage;

public interface EconomyProvider {

	boolean nativeAsync();

	CompletionStage<EconomyResult> canAfford(EconomyPlayer player, BigDecimal amount);

	CompletionStage<EconomyResult> withdraw(EconomyPlayer player, BigDecimal amount);

	CompletionStage<EconomyResult> deposit(EconomyPlayer player, BigDecimal amount);

	String getName();
}
