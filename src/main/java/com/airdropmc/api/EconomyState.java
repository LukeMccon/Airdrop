package com.airdropmc.api;

/** Describes the public availability of paid-drop economy support. */
public enum EconomyState {
	/** Economy configuration or provider discovery has not yet committed. */
	STARTING,
	/** Economy support is disabled by configuration. */
	DISABLED,
	/** Economy support is enabled, but no supported provider is available. */
	UNAVAILABLE,
	/** A supported economy provider is active. */
	ACTIVE
}
