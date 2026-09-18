package com.airdropmc.api;

/** A stable, machine-readable reason that a drop did not start. */
public enum DropRejectionReason {
	/** The requested package is not published. */
	UNKNOWN_PACKAGE,
	/** The requested target could not be resolved safely. */
	INVALID_TARGET,
	/** The target column is not open to the sky. */
	SKY_NOT_CLEAR,
	/** The player lacks permission for the package. */
	INSUFFICIENT_PERMISSION,
	/** The player already has a request awaiting completion. */
	REQUEST_PENDING,
	/** The player's request cooldown has not expired. */
	COOLDOWN,
	/** The configured falling-crate capacity is full. */
	FALLING_CAPACITY,
	/** The configured landed-crate capacity is full. */
	LANDED_CAPACITY,
	/** Another active request owns the intended landing location. */
	LOCATION_RESERVED,
	/** A priced package was requested while economy support is disabled. */
	ECONOMY_DISABLED,
	/** A priced package was requested without an economy provider. */
	ECONOMY_PROVIDER_UNAVAILABLE,
	/** The provider reported that the player cannot afford the package. */
	INSUFFICIENT_FUNDS,
	/** An economy operation definitively failed without taking payment. */
	PAYMENT_REJECTED,
	/** The provider could not determine affordability before any withdrawal. */
	AFFORDABILITY_UNKNOWN,
	/** Airdrop has not finished starting. */
	SERVICE_UNAVAILABLE,
	/** Airdrop has stopped accepting requests. */
	SHUTTING_DOWN,
	/** A future request event cancelled the request before side effects. */
	CANCELLED
}
