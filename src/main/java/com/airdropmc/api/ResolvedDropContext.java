package com.airdropmc.api;

import org.bukkit.Location;

import java.util.Objects;

/**
 * The complete detached context available after package, target, and option
 * resolution succeed.
 */
public final class ResolvedDropContext {

	private final DropRequestDescriptor descriptor;
	private final AirdropPackage airdropPackage;
	private final Location spawnLocation;
	private final Location landingLocation;
	private final WorldPosition spawnPosition;
	private final WorldPosition landingPosition;
	private final ResolvedDropSettings settings;

	/**
	 * Creates a resolved context and detaches both Bukkit locations.
	 *
	 * <p>This constructor must be called on the primary server thread because
	 * it accepts Bukkit {@link Location} values.</p>
	 *
	 * @param descriptor original request descriptor
	 * @param airdropPackage detached resolved package
	 * @param spawnLocation resolved falling spawn location
	 * @param landingLocation resolved intended landing location
	 * @param settings complete resolved settings snapshot
	 * @throws IllegalStateException if called off the primary server thread
	 */
	public ResolvedDropContext(
			DropRequestDescriptor descriptor,
			AirdropPackage airdropPackage,
			Location spawnLocation,
			Location landingLocation,
			ResolvedDropSettings settings) {
		ApiThreadGuard.requirePrimaryThread("ResolvedDropContext.<init>");
		this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
		this.airdropPackage = Objects.requireNonNull(airdropPackage, "airdropPackage");
		this.spawnLocation = Objects.requireNonNull(spawnLocation, "spawnLocation").clone();
		this.landingLocation = Objects.requireNonNull(landingLocation, "landingLocation").clone();
		this.spawnPosition = WorldPosition.from(this.spawnLocation);
		this.landingPosition = WorldPosition.from(this.landingLocation);
		this.settings = Objects.requireNonNull(settings, "settings");
		WorldPosition requestedPosition = descriptor.requestedPosition();
		if (!requestedPosition.isSameWorld(this.spawnPosition)
				|| !requestedPosition.isSameWorld(this.landingPosition)) {
			throw new IllegalArgumentException(
					"Requested, spawn, and landing positions must belong to the same world");
		}
	}

	/**
	 * Returns the original request descriptor.
	 *
	 * @return original request descriptor
	 */
	public DropRequestDescriptor descriptor() {
		return descriptor;
	}

	/**
	 * Returns the resolved package snapshot.
	 *
	 * @return detached resolved package
	 */
	public AirdropPackage airdropPackage() {
		return airdropPackage;
	}

	/**
	 * Returns a fresh copy of the spawn location.
	 *
	 * <p>This method must be called on the primary server thread because it
	 * returns a Bukkit value. Use {@link #spawnPosition()} off-thread.</p>
	 *
	 * @return detached spawn location copy
	 * @throws IllegalStateException if called off the primary server thread
	 */
	public Location spawnLocation() {
		ApiThreadGuard.requirePrimaryThread("ResolvedDropContext.spawnLocation");
		return spawnLocation.clone();
	}

	/**
	 * Returns a fresh copy of the intended landing location.
	 *
	 * <p>This method must be called on the primary server thread because it
	 * returns a Bukkit value. Use {@link #landingPosition()} off-thread.</p>
	 *
	 * @return detached intended landing location copy
	 * @throws IllegalStateException if called off the primary server thread
	 */
	public Location landingLocation() {
		ApiThreadGuard.requirePrimaryThread("ResolvedDropContext.landingLocation");
		return landingLocation.clone();
	}

	/**
	 * Returns the pure spawn position.
	 *
	 * @return pure spawn position
	 */
	public WorldPosition spawnPosition() {
		return spawnPosition;
	}

	/**
	 * Returns the pure intended landing position.
	 *
	 * @return pure intended landing position
	 */
	public WorldPosition landingPosition() {
		return landingPosition;
	}

	/**
	 * Returns the complete settings snapshot.
	 *
	 * @return complete resolved settings snapshot
	 */
	public ResolvedDropSettings settings() {
		return settings;
	}
}
