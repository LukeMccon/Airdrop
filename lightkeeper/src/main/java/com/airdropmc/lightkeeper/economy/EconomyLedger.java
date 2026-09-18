package com.airdropmc.lightkeeper.economy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class EconomyLedger {

	record Transaction(boolean success, BigDecimal balance, String errorMessage) {
	}

	record Snapshot(BigDecimal balance, int affordabilityChecks, int withdrawals, int deposits) {
	}

	private final Map<UUID, Account> accounts = new HashMap<>();

	synchronized void reset(UUID playerId, BigDecimal balance) {
		accounts.put(playerId, new Account(balance));
	}

	synchronized Transaction canWithdraw(UUID playerId, BigDecimal amount) {
		Account account = account(playerId);
		account.affordabilityChecks++;
		return result(account, amount);
	}

	synchronized Transaction withdraw(UUID playerId, BigDecimal amount) {
		Account account = account(playerId);
		account.withdrawals++;
		if (account.balance.compareTo(amount) < 0) {
			return rejected(account);
		}
		account.balance = account.balance.subtract(amount);
		return successful(account);
	}

	synchronized Transaction deposit(UUID playerId, BigDecimal amount) {
		Account account = account(playerId);
		account.deposits++;
		account.balance = account.balance.add(amount);
		return successful(account);
	}

	synchronized Snapshot snapshot(UUID playerId) {
		Account account = account(playerId);
		return new Snapshot(account.balance, account.affordabilityChecks, account.withdrawals, account.deposits);
	}

	private Account account(UUID playerId) {
		return accounts.computeIfAbsent(playerId, ignored -> new Account(BigDecimal.ZERO));
	}

	private static Transaction result(Account account, BigDecimal amount) {
		return account.balance.compareTo(amount) >= 0 ? successful(account) : rejected(account);
	}

	private static Transaction successful(Account account) {
		return new Transaction(true, account.balance, "");
	}

	private static Transaction rejected(Account account) {
		return new Transaction(false, account.balance, "Insufficient funds");
	}

	private static final class Account {
		private BigDecimal balance;
		private int affordabilityChecks;
		private int withdrawals;
		private int deposits;

		private Account(BigDecimal balance) {
			this.balance = balance;
		}
	}
}
