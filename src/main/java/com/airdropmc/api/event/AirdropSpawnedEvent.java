package com.airdropmc.api.event;

import com.airdropmc.api.FallingAirdropView;
import com.airdropmc.api.ResolvedDropContext;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.Optional;

/** Fired on the primary thread after a falling airdrop is registered and committed. */
public final class AirdropSpawnedEvent extends AbstractAirdropEvent {

	private static final HandlerList HANDLERS = new HandlerList();
	private final FallingAirdropView airdrop;

	/**
	 * Creates a post-commit spawn event.
	 *
	 * @param context immutable resolved request context
	 * @param airdrop immutable falling airdrop snapshot
	 */
	public AirdropSpawnedEvent(ResolvedDropContext context, FallingAirdropView airdrop) {
		super(context);
		this.airdrop = Objects.requireNonNull(airdrop, "airdrop");
		if (!airdrop.requestId().equals(Optional.of(requestId()))) {
			throw new IllegalArgumentException("Falling view must belong to the event request");
		}
	}

	/**
	 * Returns the committed falling airdrop snapshot.
	 *
	 * @return immutable falling airdrop snapshot
	 */
	public FallingAirdropView airdrop() {
		return airdrop;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	/**
	 * Returns the shared spawned-event handler list.
	 *
	 * @return this event type's handler list
	 */
	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
