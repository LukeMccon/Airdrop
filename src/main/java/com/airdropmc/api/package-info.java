/**
 * The supported Airdrop extension API.
 *
 * <p>Types outside this package (and its {@code event} subpackage) are plugin
 * implementation details. Pure values such as identifiers, enums, prices,
 * durations, and {@link com.airdropmc.api.WorldPosition} are safe to read from
 * any thread. Constructors and accessors that accept or return Bukkit objects
 * state their primary-thread requirement explicitly and fail fast with
 * {@link java.lang.IllegalStateException} when called off-thread.</p>
 */
package com.airdropmc.api;
