package com.airdropmc.api;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Correlation data allocated before operational request validation begins.
 */
public final class DropRequestDescriptor {

	private final UUID requestId;
	private final DropSource source;
	private final UUID playerId;
	private final String requestedPackageName;
	private final Location requestedLocation;
	private final WorldPosition requestedPosition;

	/**
	 * Creates a request descriptor and detaches the requested location.
	 *
	 * <p>This constructor must be called on the primary server thread because
	 * it accepts a Bukkit {@link Location}. A {@link DropSource#PLAYER} request
	 * requires a player ID; a {@link DropSource#SYSTEM} request forbids one.</p>
	 *
	 * @param requestId request correlation UUID
	 * @param source request initiator
	 * @param playerId requesting player UUID, or {@code null} for a system request
	 * @param requestedPackageName package name supplied by the caller
	 * @param requestedLocation target location supplied by the caller
	 * @throws IllegalStateException if called off the primary server thread
	 */
	public DropRequestDescriptor(
			UUID requestId,
			DropSource source,
			UUID playerId,
			String requestedPackageName,
			Location requestedLocation) {
		ApiThreadGuard.requirePrimaryThread("DropRequestDescriptor.<init>");
		this.requestId = Objects.requireNonNull(requestId, "requestId");
		this.source = Objects.requireNonNull(source, "source");
		this.playerId = validatePlayer(source, playerId);
		this.requestedPackageName = requirePackageName(requestedPackageName);
		this.requestedLocation = copyLocation(requestedLocation, "requestedLocation");
		this.requestedPosition = WorldPosition.from(this.requestedLocation);
	}

	/**
	 * Returns the request correlation identity.
	 *
	 * @return request correlation UUID
	 */
	public UUID requestId() {
		return requestId;
	}

	/**
	 * Returns the request initiator.
	 *
	 * @return request initiator
	 */
	public DropSource source() {
		return source;
	}

	/**
	 * Returns the requesting player when applicable.
	 *
	 * @return requesting player UUID for a player request
	 */
	public Optional<UUID> playerId() {
		return Optional.ofNullable(playerId);
	}

	/**
	 * Returns the requested package name.
	 *
	 * @return package name supplied by the caller
	 */
	public String requestedPackageName() {
		return requestedPackageName;
	}

	/**
	 * Returns a fresh copy of the requested Bukkit location.
	 *
	 * <p>This method must be called on the primary server thread because it
	 * returns a Bukkit value. Use {@link #requestedPosition()} for pure
	 * off-thread reads.</p>
	 *
	 * @return detached requested location copy
	 * @throws IllegalStateException if called off the primary server thread
	 */
	public Location requestedLocation() {
		ApiThreadGuard.requirePrimaryThread("DropRequestDescriptor.requestedLocation");
		return requestedLocation.clone();
	}

	/**
	 * Returns the pure requested position.
	 *
	 * @return pure requested position
	 */
	public WorldPosition requestedPosition() {
		return requestedPosition;
	}

	private static UUID validatePlayer(DropSource source, UUID playerId) {
		if (source == DropSource.PLAYER && playerId == null) {
			throw new IllegalArgumentException("PLAYER requests require a playerId");
		}
		if (source == DropSource.SYSTEM && playerId != null) {
			throw new IllegalArgumentException("SYSTEM requests cannot have a playerId");
		}
		return playerId;
	}

	private static String requirePackageName(String value) {
		String required = Objects.requireNonNull(value, "requestedPackageName");
		if (required.isBlank()) {
			throw new IllegalArgumentException("requestedPackageName must not be blank");
		}
		return required;
	}

	private static Location copyLocation(Location value, String name) {
		Location copy = Objects.requireNonNull(value, name).clone();
		World world = copy.getWorld();
		if (world == null) {
			throw new IllegalArgumentException(name + " must have a world");
		}
		if (world.getUID() == null) {
			throw new IllegalArgumentException(name + " world must have a UUID");
		}
		return copy;
	}
}
