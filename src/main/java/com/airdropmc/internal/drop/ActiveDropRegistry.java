package com.airdropmc.internal.drop;

import com.airdropmc.api.AirdropView;
import com.airdropmc.api.FallingAirdropView;
import com.airdropmc.api.LandedAirdropView;
import com.airdropmc.limits.DropLocationKey;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Volatile immutable active-drop read model. Mutations validate a complete
 * replacement before publishing it with one assignment.
 */
@ApiStatus.Internal
public final class ActiveDropRegistry {
	/** Atomic falling/landed counts from one immutable registry publication. */
	public record Counts(int falling, int landed) {
	}

	private record Snapshot(
			List<AirdropView> ordered,
			Map<UUID, AirdropView> byCrate,
			Map<UUID, AirdropView> byRequest,
			Map<UUID, FallingAirdropView> byFallingEntity,
			Map<DropLocationKey, LandedAirdropView> byLandedLocation,
			int fallingCount,
			int landedCount) {

		private static Snapshot empty() {
			return new Snapshot(List.of(), Map.of(), Map.of(), Map.of(), Map.of(), 0, 0);
		}
	}

	private volatile Snapshot snapshot = Snapshot.empty();

	/** Registers a new falling view after validating every identity index. */
	public synchronized void registerFalling(FallingAirdropView view) {
		FallingAirdropView required = Objects.requireNonNull(view, "view");
		List<AirdropView> replacement = new ArrayList<>(snapshot.ordered());
		replacement.add(required);
		snapshot = build(replacement);
	}

	/**
	 * Atomically replaces one falling view with its landed form.
	 *
	 * @param crateId expected stable crate identity
	 * @param fallingEntityId expected falling entity identity
	 * @param location expected landed block identity
	 * @param landed replacement landed view
	 */
	public synchronized void transitionToLanded(
			UUID crateId,
			UUID fallingEntityId,
			DropLocationKey location,
			LandedAirdropView landed) {
		UUID requiredCrateId = Objects.requireNonNull(crateId, "crateId");
		UUID requiredEntityId = Objects.requireNonNull(fallingEntityId, "fallingEntityId");
		DropLocationKey requiredLocation = Objects.requireNonNull(location, "location");
		LandedAirdropView requiredLanded = Objects.requireNonNull(landed, "landed");
		AirdropView current = snapshot.byCrate().get(requiredCrateId);
		if (!(current instanceof FallingAirdropView falling)
				|| !falling.fallingEntityId().equals(requiredEntityId)) {
			throw new IllegalStateException("Expected falling crate is not active");
		}
		if (!requiredLanded.crateId().equals(requiredCrateId)
				|| !requiredLanded.requestId().equals(falling.requestId())) {
			throw new IllegalArgumentException("Landed view must preserve falling identities");
		}
		requireLocation(requiredLocation, requiredLanded);
		List<AirdropView> replacement = replace(requiredCrateId, requiredLanded);
		snapshot = build(replacement);
	}

	/** Registers a recovered landed view which has no preceding falling view. */
	public synchronized void registerRecovered(
			DropLocationKey location, LandedAirdropView landed) {
		DropLocationKey requiredLocation = Objects.requireNonNull(location, "location");
		LandedAirdropView requiredLanded = Objects.requireNonNull(landed, "landed");
		requireLocation(requiredLocation, requiredLanded);
		List<AirdropView> replacement = new ArrayList<>(snapshot.ordered());
		replacement.add(requiredLanded);
		snapshot = build(replacement);
	}

	/** Replaces a landed snapshot, for example after its opened flag changes. */
	public synchronized void replaceLanded(
			DropLocationKey location, LandedAirdropView replacementView) {
		DropLocationKey requiredLocation = Objects.requireNonNull(location, "location");
		LandedAirdropView requiredView = Objects.requireNonNull(replacementView, "replacementView");
		LandedAirdropView current = snapshot.byLandedLocation().get(requiredLocation);
		if (current == null || !current.crateId().equals(requiredView.crateId())
				|| !current.requestId().equals(requiredView.requestId())) {
			throw new IllegalStateException("Expected landed crate is not active at the location");
		}
		requireLocation(requiredLocation, requiredView);
		snapshot = build(replace(requiredView.crateId(), requiredView));
	}

	/** Removes an active view exactly once. */
	public synchronized Optional<AirdropView> remove(UUID crateId) {
		UUID requiredCrateId = Objects.requireNonNull(crateId, "crateId");
		AirdropView removed = snapshot.byCrate().get(requiredCrateId);
		if (removed == null) {
			return Optional.empty();
		}
		List<AirdropView> replacement = snapshot.ordered().stream()
				.filter(view -> !view.crateId().equals(requiredCrateId))
				.toList();
		snapshot = build(replacement);
		return Optional.of(removed);
	}

	/** Clears all indexes with one empty snapshot assignment. */
	public synchronized List<AirdropView> clear() {
		List<AirdropView> removed = snapshot.ordered();
		snapshot = Snapshot.empty();
		return removed;
	}

	/** @return immutable ordered aggregate snapshot safe for off-thread reads */
	public List<AirdropView> activeDrops() {
		return snapshot.ordered();
	}

	/** @return view indexed by request identity */
	public Optional<AirdropView> findByRequestId(UUID requestId) {
		return Optional.ofNullable(snapshot.byRequest().get(
				Objects.requireNonNull(requestId, "requestId")));
	}

	/** @return view indexed by crate identity */
	public Optional<AirdropView> findByCrateId(UUID crateId) {
		return Optional.ofNullable(snapshot.byCrate().get(
				Objects.requireNonNull(crateId, "crateId")));
	}

	/** @return falling view indexed by entity identity */
	public Optional<AirdropView> findByFallingEntityId(UUID entityId) {
		return Optional.ofNullable(snapshot.byFallingEntity().get(
				Objects.requireNonNull(entityId, "entityId")));
	}

	/** @return landed view indexed by block identity */
	public Optional<AirdropView> findByLandedLocation(DropLocationKey location) {
		return Optional.ofNullable(snapshot.byLandedLocation().get(
				Objects.requireNonNull(location, "location")));
	}

	/** @return active falling view count */
	public int fallingCount() {
		return snapshot.fallingCount();
	}

	/** @return active landed view count */
	public int landedCount() {
		return snapshot.landedCount();
	}

	/** @return falling and landed counts from one immutable registry publication */
	public Counts counts() {
		Snapshot current = snapshot;
		return new Counts(current.fallingCount(), current.landedCount());
	}

	private List<AirdropView> replace(UUID crateId, AirdropView replacement) {
		List<AirdropView> views = new ArrayList<>(snapshot.ordered().size());
		boolean found = false;
		for (AirdropView view : snapshot.ordered()) {
			if (view.crateId().equals(crateId)) {
				views.add(replacement);
				found = true;
			} else {
				views.add(view);
			}
		}
		if (!found) {
			throw new IllegalStateException("Expected active crate is not indexed");
		}
		return views;
	}

	private static Snapshot build(Collection<AirdropView> views) {
		List<AirdropView> ordered = List.copyOf(views);
		Map<UUID, AirdropView> byCrate = new LinkedHashMap<>();
		Map<UUID, AirdropView> byRequest = new LinkedHashMap<>();
		Map<UUID, FallingAirdropView> byFalling = new LinkedHashMap<>();
		Map<DropLocationKey, LandedAirdropView> byLanded = new LinkedHashMap<>();
		int fallingCount = 0;
		int landedCount = 0;
		for (AirdropView view : ordered) {
			AirdropView required = Objects.requireNonNull(view, "view");
			putUnique(byCrate, required.crateId(), required, "crate UUID");
			required.requestId().ifPresent(requestId ->
					putUnique(byRequest, requestId, required, "request UUID"));
			if (required instanceof FallingAirdropView falling) {
				putUnique(byFalling, falling.fallingEntityId(), falling, "falling entity UUID");
				fallingCount++;
			} else if (required instanceof LandedAirdropView landed) {
				putUnique(byLanded, keyFor(landed), landed, "landed block");
				landedCount++;
			}
		}
		return new Snapshot(
				ordered,
				Map.copyOf(byCrate),
				Map.copyOf(byRequest),
				Map.copyOf(byFalling),
				Map.copyOf(byLanded),
				fallingCount,
				landedCount);
	}

	private static <K, V> void putUnique(Map<K, V> target, K key, V value, String identity) {
		if (target.putIfAbsent(Objects.requireNonNull(key, identity), value) != null) {
			throw new IllegalStateException("Duplicate active " + identity + ": " + key);
		}
	}

	private static void requireLocation(DropLocationKey key, LandedAirdropView view) {
		if (!key.equals(keyFor(view))) {
			throw new IllegalArgumentException("Landed view position must match its block index");
		}
	}

	private static DropLocationKey keyFor(LandedAirdropView view) {
		return new DropLocationKey(
				view.position().worldId(),
				floor(view.position().x()),
				floor(view.position().y()),
				floor(view.position().z()));
	}

	private static int floor(double coordinate) {
		return (int) Math.floor(coordinate);
	}
}
