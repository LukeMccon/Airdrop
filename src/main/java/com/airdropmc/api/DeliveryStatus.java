package com.airdropmc.api;

/** The terminal physical-delivery state of an airdrop request. */
public enum DeliveryStatus {
	/** The request was rejected before a crate was created. */
	REJECTED,
	/** Crate creation or delivery failed. */
	FAILED,
	/** A landing was cancelled by another plugin. */
	CANCELLED,
	/** Delivery stopped because Airdrop was disabled. */
	SHUTDOWN,
	/** The crate landed and its barrel was committed. */
	LANDED
}
