/**
 * Synchronous, primary-thread lifecycle events for the supported Airdrop API.
 *
 * <p>Payloads are immutable detached API snapshots. Event callbacks may retain
 * those payloads and read their pure values off-thread, but Bukkit dispatch and
 * any payload accessor documented as returning a Bukkit value remain confined
 * to the primary server thread.</p>
 */
package com.airdropmc.api.event;
