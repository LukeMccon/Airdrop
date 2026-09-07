package com.airdropmc.api;

/** Identifies who initiated an airdrop request. */
public enum DropSource {
	/** A player requested the drop and player permission/economy policy applies. */
	PLAYER,
	/** The server or another plugin requested an explicitly unpaid drop. */
	SYSTEM
}
