package com.airdropmc.api.event;

import com.airdropmc.api.LandedAirdropView;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/** Fired on the primary thread after a persisted landed crate is indexed. */
public final class AirdropRecoveredEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();
	private final LandedAirdropView airdrop;

	/**
	 * Creates a recovered event for an already committed landed snapshot.
	 *
	 * @param airdrop committed recovered landed snapshot
	 */
	public AirdropRecoveredEvent(LandedAirdropView airdrop) {
		super(false);
		this.airdrop = Objects.requireNonNull(airdrop, "airdrop");
		if (!airdrop.recovered()) {
			throw new IllegalArgumentException("Recovered event requires a recovered view");
		}
	}

	/**
	 * Returns the committed recovered landed snapshot.
	 *
	 * @return committed recovered landed snapshot
	 */
	public LandedAirdropView airdrop() {
		return airdrop;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	/**
	 * Returns the shared recovered-event handler list.
	 *
	 * @return this event type's shared handler list
	 */
	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
