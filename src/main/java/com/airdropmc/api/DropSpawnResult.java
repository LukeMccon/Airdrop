package com.airdropmc.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** The result of the crate-spawn phase of one request. */
public sealed interface DropSpawnResult
		permits DropSpawnResult.Spawned, DropSpawnResult.NotSpawned {

	/**
	 * Returns the stable identity of this request.
	 *
	 * @return request correlation UUID
	 */
	UUID requestId();

	/**
	 * Returns the resolved request context when one was produced.
	 *
	 * @return resolved context when resolution succeeded
	 */
	Optional<ResolvedDropContext> context();

	/**
	 * A falling crate that was registered and committed to admission.
	 *
	 * @param resolvedContext complete resolved request context
	 * @param airdrop falling airdrop snapshot
	 * @param payment payment state at spawn
	 */
	record Spawned(
			ResolvedDropContext resolvedContext,
			FallingAirdropView airdrop,
			PaymentStatus payment) implements DropSpawnResult {

		/** Validates a spawned result and its correlated falling view. */
		public Spawned {
			resolvedContext = Objects.requireNonNull(resolvedContext, "resolvedContext");
			airdrop = Objects.requireNonNull(airdrop, "airdrop");
			payment = Objects.requireNonNull(payment, "payment");
			if (!airdrop.requestId().equals(Optional.of(resolvedContext.descriptor().requestId()))) {
				throw new IllegalArgumentException("Falling view must belong to the resolved request");
			}
			if (payment != PaymentStatus.NOT_APPLICABLE && payment != PaymentStatus.CHARGED) {
				throw new IllegalArgumentException(
						"Spawned delivery requires NOT_APPLICABLE or CHARGED payment");
			}
		}

		@Override
		public UUID requestId() {
			return resolvedContext.descriptor().requestId();
		}

		@Override
		public Optional<ResolvedDropContext> context() {
			return Optional.of(resolvedContext);
		}
	}

	/**
	 * A terminal outcome reached before a crate was committed.
	 *
	 * @param outcome terminal non-landed outcome
	 */
	record NotSpawned(DropOutcome outcome) implements DropSpawnResult {

		/** Validates a result that terminated before a crate was committed. */
		public NotSpawned {
			outcome = Objects.requireNonNull(outcome, "outcome");
			if (outcome instanceof DropOutcome.Landed) {
				throw new IllegalArgumentException("A landed outcome necessarily spawned");
			}
		}

		@Override
		public UUID requestId() {
			return outcome.requestId();
		}

		@Override
		public Optional<ResolvedDropContext> context() {
			return outcome.context();
		}
	}
}
