package com.airdropmc.api;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable snapshot of an actively falling airdrop. */
public final class FallingAirdropView implements AirdropView {

	private final UUID crateId;
	private final UUID fallingEntityId;
	private final WorldPosition position;
	private final UUID requestId;
	private final String packageName;
	private final BigDecimal packagePrice;
	private final DropSource source;
	private final UUID playerId;

	/**
	 * Creates a falling view from a resolved request.
	 *
	 * @param crateId stable crate UUID
	 * @param fallingEntityId current falling-block entity UUID
	 * @param position current pure position snapshot
	 * @param context resolved request context
	 */
	public FallingAirdropView(
			UUID crateId,
			UUID fallingEntityId,
			WorldPosition position,
			ResolvedDropContext context) {
		this.crateId = Objects.requireNonNull(crateId, "crateId");
		this.fallingEntityId = Objects.requireNonNull(fallingEntityId, "fallingEntityId");
		this.position = Objects.requireNonNull(position, "position");
		ResolvedDropContext requiredContext = Objects.requireNonNull(context, "context");
		if (!position.isSameWorld(requiredContext.spawnPosition())) {
			throw new IllegalArgumentException("Falling position must match the request world");
		}
		DropRequestDescriptor descriptor = requiredContext.descriptor();
		this.requestId = descriptor.requestId();
		this.packageName = requiredContext.airdropPackage().name();
		this.packagePrice = requiredContext.airdropPackage().price();
		this.source = descriptor.source();
		this.playerId = descriptor.playerId().orElse(null);
	}

	@Override
	public UUID crateId() {
		return crateId;
	}

	/**
	 * Returns the current falling-block entity identity.
	 *
	 * @return current falling-block entity UUID
	 */
	public UUID fallingEntityId() {
		return fallingEntityId;
	}

	@Override
	public Optional<UUID> requestId() {
		return Optional.of(requestId);
	}

	@Override
	public DropState state() {
		return DropState.FALLING;
	}

	@Override
	public WorldPosition position() {
		return position;
	}

	@Override
	public Optional<String> packageName() {
		return Optional.of(packageName);
	}

	@Override
	public Optional<BigDecimal> packagePrice() {
		return Optional.of(packagePrice);
	}

	@Override
	public Optional<DropSource> source() {
		return Optional.of(source);
	}

	@Override
	public Optional<UUID> playerId() {
		return Optional.ofNullable(playerId);
	}

	@Override
	public boolean recovered() {
		return false;
	}

	@Override
	public Optional<RecoveredDropDescriptor> recoveryDescriptor() {
		return Optional.empty();
	}
}
