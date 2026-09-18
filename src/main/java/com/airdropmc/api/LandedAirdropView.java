package com.airdropmc.api;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable snapshot of an actively tracked landed airdrop. */
public final class LandedAirdropView implements AirdropView {

	private final UUID crateId;
	private final WorldPosition position;
	private final UUID requestId;
	private final String packageName;
	private final BigDecimal packagePrice;
	private final DropSource source;
	private final UUID playerId;
	private final long expiresAtMillis;
	private final boolean opened;
	private final boolean recovered;
	private final RecoveredDropDescriptor recoveryDescriptor;

	/**
	 * Creates a landed view from a resolved request.
	 *
	 * @param crateId stable crate UUID
	 * @param position current pure landed position
	 * @param context resolved request context
	 * @param expiresAtMillis absolute expiry time in Unix epoch milliseconds
	 * @param opened whether a player has opened the crate
	 */
	public LandedAirdropView(
			UUID crateId,
			WorldPosition position,
			ResolvedDropContext context,
			long expiresAtMillis,
			boolean opened) {
		this(
				Objects.requireNonNull(crateId, "crateId"),
				Objects.requireNonNull(position, "position"),
				Objects.requireNonNull(context, "context").descriptor().requestId(),
				context.airdropPackage().name(),
				context.airdropPackage().price(),
				context.descriptor().source(),
				context.descriptor().playerId().orElse(null),
				expiresAtMillis,
				opened,
				false,
				null);
		if (!position.isSameWorld(context.landingPosition())) {
			throw new IllegalArgumentException("Landed position must match the request world");
		}
	}

	private LandedAirdropView(
			UUID crateId,
			WorldPosition position,
			UUID requestId,
			String packageName,
			BigDecimal packagePrice,
			DropSource source,
			UUID playerId,
			long expiresAtMillis,
			boolean opened,
			boolean recovered,
			RecoveredDropDescriptor recoveryDescriptor) {
		if (expiresAtMillis <= 0L) {
			throw new IllegalArgumentException("expiresAtMillis must be positive");
		}
		this.crateId = crateId;
		this.position = position;
		this.requestId = requestId;
		this.packageName = packageName;
		this.packagePrice = packagePrice;
		this.source = source;
		this.playerId = playerId;
		this.expiresAtMillis = expiresAtMillis;
		this.opened = opened;
		this.recovered = recovered;
		this.recoveryDescriptor = recoveryDescriptor;
	}

	/**
	 * Creates the minimal safe view for legacy persisted data that predates
	 * request-context persistence.
	 *
	 * @param crateId stable crate UUID
	 * @param position current pure landed position
	 * @param expiresAtMillis absolute expiry time in Unix epoch milliseconds
	 * @param opened whether a player has opened the crate
	 * @return recovered legacy view
	 */
	public static LandedAirdropView recovered(
			UUID crateId, WorldPosition position, long expiresAtMillis, boolean opened) {
		return new LandedAirdropView(
				Objects.requireNonNull(crateId, "crateId"),
				Objects.requireNonNull(position, "position"),
				null, null, null, null, null, expiresAtMillis, opened, true, null);
	}

	/**
	 * Creates a recovered view with validated schema-v1 request details.
	 *
	 * @param crateId stable crate UUID
	 * @param position current pure landed position
	 * @param expiresAtMillis absolute expiry time in Unix epoch milliseconds
	 * @param opened whether a player has opened the crate
	 * @param recoveryDescriptor persistence-safe original request details
	 * @return recovered schema-aware view
	 */
	public static LandedAirdropView recovered(
			UUID crateId,
			WorldPosition position,
			long expiresAtMillis,
			boolean opened,
			RecoveredDropDescriptor recoveryDescriptor) {
		RecoveredDropDescriptor descriptor = Objects.requireNonNull(
				recoveryDescriptor, "recoveryDescriptor");
		return new LandedAirdropView(
				Objects.requireNonNull(crateId, "crateId"),
				Objects.requireNonNull(position, "position"),
				descriptor.requestId(),
				descriptor.packageName(),
				descriptor.packagePrice(),
				descriptor.source(),
				descriptor.playerId().orElse(null),
				expiresAtMillis,
				opened,
				true,
				descriptor);
	}

	@Override
	public UUID crateId() {
		return crateId;
	}

	@Override
	public Optional<UUID> requestId() {
		return Optional.ofNullable(requestId);
	}

	@Override
	public DropState state() {
		return DropState.LANDED;
	}

	@Override
	public WorldPosition position() {
		return position;
	}

	@Override
	public Optional<String> packageName() {
		return Optional.ofNullable(packageName);
	}

	@Override
	public Optional<BigDecimal> packagePrice() {
		return Optional.ofNullable(packagePrice);
	}

	@Override
	public Optional<DropSource> source() {
		return Optional.ofNullable(source);
	}

	@Override
	public Optional<UUID> playerId() {
		return Optional.ofNullable(playerId);
	}

	/**
	 * Returns the absolute expiry time.
	 *
	 * @return absolute expiry time in Unix epoch milliseconds
	 */
	public long expiresAtMillis() {
		return expiresAtMillis;
	}

	/**
	 * Returns whether a player has opened the crate.
	 *
	 * @return whether a player has opened the crate
	 */
	public boolean opened() {
		return opened;
	}

	@Override
	public boolean recovered() {
		return recovered;
	}

	@Override
	public Optional<RecoveredDropDescriptor> recoveryDescriptor() {
		return Optional.ofNullable(recoveryDescriptor);
	}
}
