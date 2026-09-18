package com.airdropmc.api.event;

import com.airdropmc.api.FallingAirdropView;
import com.airdropmc.api.ResolvedDropContext;
import com.airdropmc.api.WorldPosition;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.Optional;

/**
 * Fired on the primary thread before a falling airdrop mutates its candidate
 * landing block, inventory, indexes, lease, or delivery state.
 */
public final class AirdropLandingAttemptEvent extends AbstractAirdropEvent
		implements Cancellable {

	private static final HandlerList HANDLERS = new HandlerList();
	private final FallingAirdropView airdrop;
	private final WorldPosition candidatePosition;
	private boolean cancelled;

	/**
	 * Creates a cancellable landing-attempt event.
	 *
	 * @param context immutable resolved request context
	 * @param airdrop immutable falling airdrop snapshot
	 * @param candidatePosition pure candidate landing position
	 */
	public AirdropLandingAttemptEvent(
			ResolvedDropContext context,
			FallingAirdropView airdrop,
			WorldPosition candidatePosition) {
		super(context);
		this.airdrop = Objects.requireNonNull(airdrop, "airdrop");
		this.candidatePosition = Objects.requireNonNull(candidatePosition, "candidatePosition");
		if (!airdrop.requestId().equals(Optional.of(requestId()))) {
			throw new IllegalArgumentException("Falling view must belong to the event request");
		}
		if (!candidatePosition.isSameWorld(context.landingPosition())) {
			throw new IllegalArgumentException("Candidate position must match the request world");
		}
	}

	/**
	 * Returns the falling airdrop that is attempting to land.
	 *
	 * @return immutable falling airdrop snapshot
	 */
	public FallingAirdropView airdrop() {
		return airdrop;
	}

	/**
	 * Returns the candidate block position without exposing a live Bukkit block.
	 *
	 * @return pure candidate landing position
	 */
	public WorldPosition candidatePosition() {
		return candidatePosition;
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
	 * Returns the shared landing-attempt handler list.
	 *
	 * @return this event type's handler list
	 */
	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
