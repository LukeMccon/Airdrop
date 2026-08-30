package com.airdropmc.lightkeeper.economy;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class EconomyStateEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final String correlationId;
	private final UUID playerId;
	private final String balance;
	private final int affordabilityChecks;
	private final int withdrawals;
	private final int deposits;

	EconomyStateEvent(String correlationId, UUID playerId, EconomyLedger.Snapshot snapshot) {
		this.correlationId = correlationId;
		this.playerId = playerId;
		this.balance = snapshot.balance().toPlainString();
		this.affordabilityChecks = snapshot.affordabilityChecks();
		this.withdrawals = snapshot.withdrawals();
		this.deposits = snapshot.deposits();
	}

	public String getCorrelationId() {
		return correlationId;
	}

	public UUID getPlayerId() {
		return playerId;
	}

	public String getBalance() {
		return balance;
	}

	public int getAffordabilityChecks() {
		return affordabilityChecks;
	}

	public int getWithdrawals() {
		return withdrawals;
	}

	public int getDeposits() {
		return deposits;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
