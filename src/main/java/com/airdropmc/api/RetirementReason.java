package com.airdropmc.api;

/** Identifies why an airdrop stopped being actively tracked. */
public enum RetirementReason {
	/** An opened crate became empty when its inventory closed. */
	CLOSED_EMPTY,
	/** A hopper emptied or invalidated the tracked barrel. */
	HOPPER_EMPTY,
	/** The tracked barrel was broken. */
	BROKEN,
	/** The tracked barrel burned. */
	BURNED,
	/** The tracked barrel was removed by an explosion. */
	EXPLODED,
	/** The landed lifetime elapsed. */
	EXPIRED,
	/** Landing was cancelled by Paper or an Airdrop API consumer. */
	CANCELLED,
	/** Tracking ended because an operation failed. */
	FAILED,
	/** A non-persisted drop was removed for a chunk unload. */
	CHUNK_UNLOAD,
	/** A non-persisted drop was removed for a world unload. */
	WORLD_UNLOAD,
	/** A non-persisted drop was removed during server shutdown. */
	SHUTDOWN,
	/** A drop was purged during a live plugin disable. */
	HOT_DISABLE,
	/** An active drop conflicted with persisted recovery data and was purged. */
	RECOVERY_PURGED
}
