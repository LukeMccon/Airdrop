package com.airdropmc.api;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * An immutable, Bukkit-object-free snapshot of one actively tracked airdrop.
 */
public sealed interface AirdropView permits FallingAirdropView, LandedAirdropView {

	/**
	 * Returns the stable crate identity.
	 *
	 * @return stable crate UUID
	 */
	UUID crateId();

	/**
	 * Returns the originating request identity when known.
	 *
	 * @return originating request UUID when persisted context is available
	 */
	Optional<UUID> requestId();

	/**
	 * Returns the active physical phase.
	 *
	 * @return active physical phase
	 */
	DropState state();

	/**
	 * Returns the current detached position.
	 * Falling positions are sampled every two server ticks; a retained view
	 * keeps the position captured when that snapshot was published.
	 *
	 * @return current pure position snapshot
	 */
	WorldPosition position();

	/**
	 * Returns the package name when known.
	 *
	 * @return package name when persisted context is available
	 */
	Optional<String> packageName();

	/**
	 * Returns the package price when known.
	 *
	 * @return package price when persisted context is available
	 */
	Optional<BigDecimal> packagePrice();

	/**
	 * Returns the request source when known.
	 *
	 * @return original request source when persisted context is available
	 */
	Optional<DropSource> source();

	/**
	 * Returns the requesting player when applicable and known.
	 *
	 * @return requesting player UUID for a player request
	 */
	Optional<UUID> playerId();

	/**
	 * Returns whether the view originated from persisted recovery data.
	 *
	 * @return {@code true} for a recovered landed crate
	 */
	boolean recovered();

	/**
	 * Returns the persistence-safe request details for a schema-aware recovered
	 * crate. Normal live views and legacy recovered crates return empty.
	 *
	 * @return optional recovery descriptor
	 */
	Optional<RecoveredDropDescriptor> recoveryDescriptor();
}
