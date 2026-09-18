package com.airdropmc.api.event;

import com.airdropmc.api.DropRequestDescriptor;
import com.airdropmc.api.ResolvedDropContext;
import org.bukkit.event.Event;

import java.util.Objects;
import java.util.UUID;

/**
 * Base for resolved, correlated Airdrop lifecycle events.
 *
 * <p>All subclasses are synchronous and are fired on the primary server
 * thread.</p>
 */
public abstract class AbstractAirdropEvent extends Event {

	private final ResolvedDropContext context;

	/**
	 * Creates a synchronous event for a resolved request.
	 *
	 * @param context immutable resolved request context
	 */
	protected AbstractAirdropEvent(ResolvedDropContext context) {
		super(false);
		this.context = Objects.requireNonNull(context, "context");
	}

	/**
	 * Returns the request correlation identity.
	 *
	 * @return request correlation UUID
	 */
	public final UUID requestId() {
		return context.descriptor().requestId();
	}

	/**
	 * Returns the descriptor allocated before request resolution.
	 *
	 * @return immutable request descriptor
	 */
	public final DropRequestDescriptor descriptor() {
		return context.descriptor();
	}

	/**
	 * Returns the complete resolved request snapshot.
	 *
	 * @return immutable resolved request context
	 */
	public final ResolvedDropContext context() {
		return context;
	}
}
