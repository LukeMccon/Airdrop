package com.airdropmc.api.event;

import com.airdropmc.api.ResolvedDropContext;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired after request resolution and before admission, payment, cooldown, or
 * entity side effects.
 *
 * <p>Cancelling this synchronous primary-thread event rejects the request as
 * {@code CANCELLED}.</p>
 */
public final class AirdropRequestEvent extends AbstractAirdropEvent implements Cancellable {

	private static final HandlerList HANDLERS = new HandlerList();
	private boolean cancelled;

	/**
	 * Creates a request event.
	 *
	 * @param context immutable resolved request context
	 */
	public AirdropRequestEvent(ResolvedDropContext context) {
		super(context);
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		cancelled = cancel;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	/**
	 * Returns the shared request-event handler list.
	 *
	 * @return this event type's handler list
	 */
	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
