package com.airdropmc.api.event;

import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.ResolvedDropContext;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/** Fired exactly once on the primary thread after the terminal handle completes. */
public final class AirdropOutcomeEvent extends AbstractAirdropEvent {

	private static final HandlerList HANDLERS = new HandlerList();
	private final DropOutcome outcome;

	/**
	 * Creates a terminal outcome event.
	 *
	 * @param context immutable resolved request context
	 * @param outcome immutable terminal outcome
	 */
	public AirdropOutcomeEvent(ResolvedDropContext context, DropOutcome outcome) {
		super(context);
		this.outcome = Objects.requireNonNull(outcome, "outcome");
		if (!requestId().equals(outcome.requestId())
				|| outcome.context().isEmpty()) {
			throw new IllegalArgumentException("Outcome must belong to the resolved event request");
		}
	}

	/**
	 * Returns the terminal result that already completed the request handle.
	 *
	 * @return immutable terminal outcome
	 */
	public DropOutcome outcome() {
		return outcome;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	/**
	 * Returns the shared outcome-event handler list.
	 *
	 * @return this event type's handler list
	 */
	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
