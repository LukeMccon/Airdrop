package com.airdropmc.api;

/** The active physical phase represented by an {@link AirdropView}. */
public enum DropState {
	/** The barrel entity is descending. */
	FALLING,
	/** The barrel block has landed and remains tracked. */
	LANDED
}
