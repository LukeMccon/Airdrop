package com.airdropmc.api.event;

import com.airdropmc.api.LandedAirdropView;
import com.airdropmc.api.ResolvedDropContext;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.Optional;

/** Fired on the primary thread after the landed barrel and indexes are committed. */
public final class AirdropLandedEvent extends AbstractAirdropEvent {

	private static final HandlerList HANDLERS = new HandlerList();
	private final LandedAirdropView airdrop;

	/**
	 * Creates a post-commit landed event.
	 *
	 * @param context immutable resolved request context
	 * @param airdrop immutable landed airdrop snapshot
	 */
	public AirdropLandedEvent(ResolvedDropContext context, LandedAirdropView airdrop) {
		super(context);
		this.airdrop = Objects.requireNonNull(airdrop, "airdrop");
		if (!airdrop.requestId().equals(Optional.of(requestId()))) {
			throw new IllegalArgumentException("Landed view must belong to the event request");
		}
	}

	/**
	 * Returns the committed landed airdrop snapshot.
	 *
	 * @return immutable landed airdrop snapshot
	 */
	public LandedAirdropView airdrop() {
		return airdrop;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	/**
	 * Returns the shared landed-event handler list.
	 *
	 * @return this event type's handler list
	 */
	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
