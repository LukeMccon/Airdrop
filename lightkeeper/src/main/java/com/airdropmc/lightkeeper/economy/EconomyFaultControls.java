package com.airdropmc.lightkeeper.economy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Account-scoped, one-shot provider responses. A hold delays confirmation, never ledger execution. */
final class EconomyFaultControls implements AutoCloseable {
	static final String REJECTED_REFUND = "Fixture rejected refund";
	static final String EXCEPTIONAL_REFUND = "Fixture exceptional refund";

	enum Mode { NORMAL, HOLD, REJECT, EXCEPTION }

	record Response(Mode mode, CompletableFuture<Void> confirmation) {
	}

	private record Key(UUID playerId, EconomyOperationType operation) {
	}

	private final Map<Key, Mode> armed = new HashMap<>();
	private final Map<Key, CompletableFuture<Void>> held = new HashMap<>();
	private final Set<CompletableFuture<?>> pending = new HashSet<>();
	private boolean closed;

	synchronized boolean hasPending() {
		return !pending.isEmpty();
	}

	synchronized void track(CompletableFuture<?> completion) {
		if (closed) {
			completion.completeExceptionally(new IllegalStateException("Economy fault controls are closed"));
		} else {
			pending.add(completion);
		}
	}

	synchronized void finished(CompletableFuture<?> completion) {
		pending.remove(completion);
	}

	synchronized void arm(UUID playerId, EconomyOperationType operation, Mode mode) {
		if (closed || mode == Mode.NORMAL || operation == EconomyOperationType.CAN_WITHDRAW
				|| (mode != Mode.HOLD && operation != EconomyOperationType.DEPOSIT)) {
			throw new IllegalArgumentException("Unsupported fault control");
		}
		Key key = new Key(playerId, operation);
		if (armed.containsKey(key) || held.containsKey(key)) {
			throw new IllegalArgumentException("An account operation already has a control");
		}
		armed.put(key, mode);
	}

	synchronized Response claim(UUID playerId, EconomyOperationType operation) {
		if (closed) {
			throw new IllegalStateException("Economy fault controls are closed");
		}
		Key key = new Key(playerId, operation);
		Mode mode = armed.remove(key);
		if (mode == null) {
			return new Response(Mode.NORMAL, CompletableFuture.completedFuture(null));
		}
		CompletableFuture<Void> confirmation = new CompletableFuture<>();
		if (mode == Mode.HOLD) {
			held.put(key, confirmation);
		} else {
			confirmation.complete(null);
		}
		return new Response(mode, confirmation);
	}

	boolean release(UUID playerId, EconomyOperationType operation) {
		CompletableFuture<Void> confirmation;
		synchronized (this) {
			confirmation = held.remove(new Key(playerId, operation));
		}
		return confirmation != null && confirmation.complete(null);
	}

	void clear(UUID playerId) {
		Map<Key, CompletableFuture<Void>> released = new HashMap<>();
		synchronized (this) {
			armed.keySet().removeIf(key -> key.playerId().equals(playerId));
			held.entrySet().removeIf(entry -> {
				if (!entry.getKey().playerId().equals(playerId)) {
					return false;
				}
				released.put(entry.getKey(), entry.getValue());
				return true;
			});
		}
		released.values().forEach(confirmation -> confirmation.complete(null));
	}

	@Override
	public void close() {
		Map<Key, CompletableFuture<Void>> released;
		Set<CompletableFuture<?>> unfinished;
		synchronized (this) {
			closed = true;
			armed.clear();
			released = new HashMap<>(held);
			held.clear();
			unfinished = Set.copyOf(pending);
		}
		// Already-applied operations can confirm normally. Queued tasks must not leave dangling futures
		// when the plugin subsequently shuts its executor down.
		released.values().forEach(confirmation -> confirmation.complete(null));
		unfinished.forEach(completion -> completion.completeExceptionally(
				new IllegalStateException("Fixture closed before operation completed")));
	}
}
