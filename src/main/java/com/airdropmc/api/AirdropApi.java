package com.airdropmc.api;

import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * The supported service entry point for Airdrop extensions.
 *
 * <p>Discover this service through Bukkit's {@code ServicesManager}. Pure
 * lifecycle, version, status, UUID, and aggregate snapshot methods may be used
 * off-thread. Methods that inspect or return Bukkit values document their
 * primary-thread requirement.</p>
 */
public interface AirdropApi {

	/**
	 * Returns a read-only stage that completes after required startup state is published.
	 *
	 * @return cached minimal readiness stage
	 */
	CompletionStage<AirdropApi> readiness();

	/**
	 * Returns the current service lifecycle state.
	 *
	 * @return current service lifecycle state
	 */
	ReadinessState state();

	/**
	 * Returns version values captured with this provider.
	 *
	 * @return immutable version snapshot captured with this provider
	 */
	AirdropVersions versions();

	/**
	 * Returns the latest operational status snapshot.
	 *
	 * @return latest immutable operational status snapshot
	 */
	AirdropStatus status();

	/**
	 * Returns detached package snapshots in deterministic name order.
	 *
	 * <p>This method must be called on the primary server thread because package
	 * snapshots contain copied Bukkit item stacks.</p>
	 *
	 * @return immutable package snapshot list
	 */
	List<AirdropPackage> listPackages();

	/**
	 * Finds a package case-insensitively.
	 *
	 * <p>This method must be called on the primary server thread because a found
	 * package contains copied Bukkit item stacks.</p>
	 *
	 * @param name package name
	 * @return detached package snapshot when present
	 */
	Optional<AirdropPackage> findPackage(String name);

	/**
	 * Returns the latest successful package publication revision.
	 *
	 * @return monotonic in-memory package registry revision, initially zero
	 */
	long packageRevision();

	/**
	 * Requests a permission- and economy-aware drop for a player.
	 *
	 * <p>This method must be called on the primary server thread because it
	 * inspects the supplied Bukkit player and location.</p>
	 *
	 * @param player requesting player
	 * @param packageName package name
	 * @param options immutable per-request overrides
	 * @return correlated request handle
	 */
	DropHandle requestPlayerDrop(
			Player player, String packageName, DropRequestOptions options);

	/**
	 * Requests an explicitly unpaid system drop.
	 *
	 * <p>This method must be called on the primary server thread because it
	 * accepts a Bukkit location.</p>
	 *
	 * @param location requested target
	 * @param packageName package name
	 * @param options immutable per-request overrides
	 * @return correlated request handle
	 */
	DropHandle requestSystemDrop(
			Location location, String packageName, DropRequestOptions options);

	/**
	 * Returns the latest immutable aggregate active-drop snapshot.
	 *
	 * @return immutable active-drop snapshots
	 */
	Collection<AirdropView> activeDrops();

	/**
	 * Finds an active drop by request identity.
	 *
	 * @param requestId request UUID
	 * @return active view when indexed
	 */
	Optional<AirdropView> findByRequestId(UUID requestId);

	/**
	 * Finds an active drop by crate identity.
	 *
	 * @param crateId crate UUID
	 * @return active view when indexed
	 */
	Optional<AirdropView> findByCrateId(UUID crateId);

	/**
	 * Finds an active drop by falling entity.
	 *
	 * <p>This method must be called on the primary server thread because it
	 * accepts and inspects a Bukkit entity.</p>
	 *
	 * @param entity falling-block entity
	 * @return active view when indexed
	 */
	Optional<AirdropView> findByFallingEntity(FallingBlock entity);

	/**
	 * Finds an active drop by landed block.
	 *
	 * <p>This method must be called on the primary server thread because it
	 * accepts and inspects a Bukkit block.</p>
	 *
	 * @param block landed barrel block
	 * @return active view when indexed
	 */
	Optional<AirdropView> findByLandedBlock(Block block);
}
