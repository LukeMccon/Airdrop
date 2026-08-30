package com.airdropmc.lightkeeper.economy;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public final class EconomyOperationEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();

	private final EconomyOperationType operation;
	private final String caller;
	private final UUID playerId;
	private final String amount;
	private final String balance;
	private final boolean successful;

	EconomyOperationEvent(VaultUnlockedEconomyService.Operation operation) {
		super(true);
		this.operation = operation.operation();
		this.caller = operation.caller();
		this.playerId = operation.playerId();
		this.amount = operation.amount().toPlainString();
		this.balance = operation.transaction().balance().toPlainString();
		this.successful = operation.transaction().success();
	}

	public EconomyOperationType getOperation() {
		return operation;
	}

	public String getCaller() {
		return caller;
	}

	public UUID getPlayerId() {
		return playerId;
	}

	public String getAmount() {
		return amount;
	}

	public String getBalance() {
		return balance;
	}

	public boolean isSuccessful() {
		return successful;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
