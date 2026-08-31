package com.airdropmc.api.event;

import com.airdropmc.api.PackageRegistryCause;
import com.airdropmc.api.PackageRegistryChange;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

/** Fired on the primary thread after a package registry revision is published. */
public final class PackageRegistryChangedEvent extends Event {

	private static final HandlerList HANDLERS = new HandlerList();
	private final PackageRegistryChange change;

	/**
	 * Creates a post-publication package registry event.
	 *
	 * @param change immutable complete snapshot and diff
	 */
	public PackageRegistryChangedEvent(PackageRegistryChange change) {
		super(false);
		this.change = Objects.requireNonNull(change, "change");
	}

	/**
	 * Returns the complete registry snapshot and deterministic diff.
	 *
	 * @return immutable complete snapshot and diff
	 */
	public PackageRegistryChange change() {
		return change;
	}

	/**
	 * Returns the newly published registry revision.
	 *
	 * @return new registry revision
	 */
	public long revision() {
		return change.revision();
	}

	/**
	 * Returns the operation which published this revision.
	 *
	 * @return operation that published the revision
	 */
	public PackageRegistryCause cause() {
		return change.cause();
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	/**
	 * Returns the shared package-registry event handler list.
	 *
	 * @return this event type's shared handler list
	 */
	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
