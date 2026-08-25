package com.airdropmc.limits;

import com.airdropmc.exceptions.DropLimitException;
import com.airdropmc.exceptions.DropLimitException.Reason;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class DropAdmissionController {

	public enum LeaseState {
		RESERVED,
		FALLING,
		LANDED,
		CLOSED
	}

	public record Snapshot(
			int falling,
			int landedClaims,
			int locations,
			int pending,
			int cooldowns,
			boolean accepting) {
	}

	private record Cooldown(long startedAt, long durationNanos) {
	}

	private final LongSupplier nanoTime;
	private final Map<UUID, Cooldown> cooldowns = new HashMap<>();
	private final Set<UUID> pending = new HashSet<>();
	private final Set<DropLocationKey> locations = new HashSet<>();
	private final Set<Lease> liveLeases = Collections.newSetFromMap(new IdentityHashMap<>());
	private int falling;
	private int landedClaims;
	private boolean accepting = true;

	public DropAdmissionController() {
		this(System::nanoTime);
	}

	DropAdmissionController(LongSupplier nanoTime) {
		this.nanoTime = nanoTime;
	}

	public synchronized Lease acquirePlayer(UUID playerId, boolean cooldownBypass,
			DropLocationKey location, DropLimitSettings settings) throws DropLimitException {
		if (playerId == null) {
			throw new IllegalArgumentException("playerId is required");
		}
		return acquire(playerId, cooldownBypass, location, settings);
	}

	public synchronized Lease acquireSystem(DropLocationKey location, DropLimitSettings settings)
			throws DropLimitException {
		return acquire(null, true, location, settings);
	}

	/**
	 * Restores an existing physical paid-crate obligation. Recovery deliberately
	 * bypasses admission caps; subsequent new admissions remain blocked while the
	 * restored claim keeps occupancy at or above the configured limit.
	 */
	public synchronized Lease restoreLanded(DropLocationKey location) {
		if (location == null) {
			throw new IllegalArgumentException("location is required");
		}
		if (locations.contains(location)) {
			throw new IllegalStateException("Drop location is already reserved");
		}

		Lease lease = new Lease(null, true, location, Duration.ZERO);
		lease.state = LeaseState.LANDED;
		lease.requestCommitted = true;
		liveLeases.add(lease);
		landedClaims++;
		locations.add(location);
		return lease;
	}

	public synchronized void stopAccepting() {
		accepting = false;
	}

	public synchronized void clear() {
		for (Lease lease : new ArrayList<>(liveLeases)) {
			release(lease);
		}
		cooldowns.clear();
		pending.clear();
		locations.clear();
		falling = 0;
		landedClaims = 0;
	}

	public synchronized Snapshot snapshot() {
		return new Snapshot(
				falling, landedClaims, locations.size(), pending.size(), cooldowns.size(), accepting);
	}

	private Lease acquire(UUID playerId, boolean cooldownBypass,
			DropLocationKey location, DropLimitSettings settings) throws DropLimitException {
		if (location == null || settings == null) {
			throw new IllegalArgumentException("location and settings are required");
		}
		long now = nanoTime.getAsLong();
		if (!accepting) {
			throw new DropLimitException(Reason.SHUTTING_DOWN);
		}
		cooldowns.entrySet().removeIf(entry -> elapsed(now, entry.getValue()) >= entry.getValue().durationNanos());
		if (!cooldownBypass && pending.contains(playerId)) {
			throw new DropLimitException(Reason.REQUEST_PENDING);
		}
		Cooldown cooldown = cooldownBypass ? null : cooldowns.get(playerId);
		if (cooldown != null) {
			long remaining = cooldown.durationNanos() - elapsed(now, cooldown);
			throw new DropLimitException(Reason.COOLDOWN,
					Math.max(1, (remaining + 999_999_999L) / 1_000_000_000L));
		}
		if (falling >= settings.maxFalling()) {
			throw new DropLimitException(Reason.FALLING_CAPACITY);
		}
		if (landedClaims >= settings.maxLanded()) {
			throw new DropLimitException(Reason.LANDED_CAPACITY);
		}
		if (locations.contains(location)) {
			throw new DropLimitException(Reason.LOCATION_RESERVED);
		}

		Lease lease = new Lease(playerId, cooldownBypass, location, settings.requestCooldown());
		liveLeases.add(lease);
		falling++;
		landedClaims++;
		locations.add(location);
		if (!cooldownBypass) {
			pending.add(playerId);
		}
		return lease;
	}

	private static long elapsed(long now, Cooldown cooldown) {
		return now - cooldown.startedAt();
	}

	private synchronized void commitSpawn(Lease lease) {
		if (lease.state != LeaseState.RESERVED) {
			throw new IllegalStateException("Lease is not reserved");
		}
		lease.state = LeaseState.FALLING;
		lease.requestCommitted = true;
		if (lease.playerId == null || lease.cooldownBypass) {
			return;
		}
		pending.remove(lease.playerId);
		cooldowns.put(lease.playerId, new Cooldown(nanoTime.getAsLong(), lease.cooldown.toNanos()));
	}

	private synchronized void markLanded(Lease lease) {
		if (lease.state != LeaseState.FALLING) {
			throw new IllegalStateException("Lease is not falling");
		}
		lease.state = LeaseState.LANDED;
		falling--;
	}

	private synchronized void release(Lease lease) {
		if (lease.state == LeaseState.CLOSED) {
			return;
		}
		if (lease.state == LeaseState.RESERVED || lease.state == LeaseState.FALLING) {
			falling--;
		}
		landedClaims--;
		locations.remove(lease.location);
		if (lease.playerId != null && !lease.requestCommitted) {
			pending.remove(lease.playerId);
		}
		liveLeases.remove(lease);
		lease.state = LeaseState.CLOSED;
	}

	public final class Lease implements AutoCloseable {

		private final UUID playerId;
		private final boolean cooldownBypass;
		private final DropLocationKey location;
		private final Duration cooldown;
		private LeaseState state = LeaseState.RESERVED;
		private boolean requestCommitted;

		private Lease(UUID playerId, boolean cooldownBypass, DropLocationKey location, Duration cooldown) {
			this.playerId = playerId;
			this.cooldownBypass = cooldownBypass;
			this.location = location;
			this.cooldown = cooldown;
		}

		public void commitSpawn() {
			DropAdmissionController.this.commitSpawn(this);
		}

		public void markLanded() {
			DropAdmissionController.this.markLanded(this);
		}

		public DropLocationKey location() {
			return location;
		}

		public boolean owns(DropLocationKey candidate) {
			return location.equals(candidate);
		}

		public LeaseState state() {
			synchronized (DropAdmissionController.this) {
				return state;
			}
		}

		@Override
		public void close() {
			DropAdmissionController.this.release(this);
		}
	}
}
