package com.airdropmc.api;

import org.bukkit.Bukkit;

/** Shared fail-fast enforcement for public API operations that touch Bukkit values. */
final class ApiThreadGuard {

	private static final String PRIMARY_THREAD_REQUIRED =
			" must be called on the primary server thread";

	private ApiThreadGuard() {
	}

	static void requirePrimaryThread(String operation) {
		if (!Bukkit.isPrimaryThread()) {
			throw new IllegalStateException(operation + PRIMARY_THREAD_REQUIRED);
		}
	}
}
