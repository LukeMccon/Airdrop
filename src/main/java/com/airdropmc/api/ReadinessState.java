package com.airdropmc.api;

/** Describes whether the Airdrop service can accept operational calls. */
public enum ReadinessState {
	/** The service is registered while configuration and integrations are prepared. */
	STARTING,
	/** Required configuration and runtime state have been published successfully. */
	READY,
	/** Startup failed before the service became ready. */
	FAILED,
	/** The plugin is disabling and no new work is accepted. */
	STOPPING
}
