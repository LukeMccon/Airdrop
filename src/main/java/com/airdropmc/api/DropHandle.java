package com.airdropmc.api;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** A correlated, read-only view of one asynchronous drop request. */
public interface DropHandle {

	/** @return request correlation UUID */
	UUID requestId();

	/** @return immutable descriptor allocated before operational validation */
	DropRequestDescriptor descriptor();

	/** @return resolved context after package, target, and settings resolution */
	Optional<ResolvedDropContext> context();

	/**
	 * Returns a cached read-only spawn-stage view.
	 *
	 * @return minimal stage completed once the crate spawns or cannot spawn
	 */
	CompletionStage<DropSpawnResult> spawn();

	/**
	 * Returns a cached read-only terminal-stage view.
	 *
	 * @return minimal stage completed once delivery and payment are final
	 */
	CompletionStage<DropOutcome> outcome();
}
