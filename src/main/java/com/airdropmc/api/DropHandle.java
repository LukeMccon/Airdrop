package com.airdropmc.api;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** A correlated, read-only view of one asynchronous drop request. */
public interface DropHandle {

	/**
	 * Returns the stable identity of this request.
	 *
	 * @return request correlation UUID
	 */
	UUID requestId();

	/**
	 * Returns the descriptor allocated before operational validation.
	 *
	 * @return immutable request descriptor
	 */
	DropRequestDescriptor descriptor();

	/**
	 * Returns the context after package, target, and settings resolution.
	 *
	 * @return resolved context when resolution succeeded
	 */
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
