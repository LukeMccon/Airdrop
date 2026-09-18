package com.airdropmc.api.event;

import com.airdropmc.api.AirdropView;
import com.airdropmc.api.RetirementReason;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/** Fired on the primary thread exactly once after an airdrop leaves every active index. */
public final class AirdropRetiredEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();
	private final AirdropView airdrop;
	private final RetirementReason reason;

	/**
	 * Creates a retirement event for a view already absent from active indexes.
	 *
	 * @param airdrop final immutable view that was removed
	 * @param reason typed terminal tracking reason
	 */
	public AirdropRetiredEvent(AirdropView airdrop, RetirementReason reason) {
		super(false);
		this.airdrop = Objects.requireNonNull(airdrop, "airdrop");
		this.reason = Objects.requireNonNull(reason, "reason");
	}

	/**
	 * Returns the final immutable view removed from active indexes.
	 *
	 * @return final immutable view removed from active indexes
	 */
	public AirdropView airdrop() {
		return airdrop;
	}

	/**
	 * Returns why active tracking ended.
	 *
	 * @return typed reason tracking ended
	 */
	public RetirementReason reason() {
		return reason;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	/**
	 * Returns the shared retirement-event handler list.
	 *
	 * @return this event type's shared handler list
	 */
	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
