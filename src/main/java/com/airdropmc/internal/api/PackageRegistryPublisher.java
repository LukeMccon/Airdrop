package com.airdropmc.internal.api;

import com.airdropmc.api.AirdropPackage;
import com.airdropmc.api.PackageRegistryCause;
import com.airdropmc.api.PackageRegistryChange;
import com.airdropmc.api.event.PackageRegistryChangedEvent;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.ApiStatus;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Owns the volatile supported package snapshot and monotonic publication revision. */
@ApiStatus.Internal
public final class PackageRegistryPublisher {
	/** Atomic revision/count pair for operational status snapshots. */
	public record Summary(long revision, int packageCount) {
	}

	private record Snapshot(long revision, Map<String, AirdropPackage> packages) {
	}

	private final Consumer<PackageRegistryChangedEvent> eventDispatcher;
	private volatile Snapshot snapshot = new Snapshot(0L, Map.of());

	/** Creates a publisher which dispatches synchronous Bukkit events. */
	public PackageRegistryPublisher() {
		this(event -> Bukkit.getPluginManager().callEvent(event));
	}

	/** Internal seam for deterministic publication tests. */
	public PackageRegistryPublisher(Consumer<PackageRegistryChangedEvent> eventDispatcher) {
		this.eventDispatcher = Objects.requireNonNull(eventDispatcher, "eventDispatcher");
	}

	/** @return latest successful in-memory revision */
	public long revision() {
		return snapshot.revision();
	}

	/** @return latest complete immutable package snapshot */
	public Map<String, AirdropPackage> packages() {
		return snapshot.packages();
	}

	/** @return revision and package count from one immutable publication */
	public Summary summary() {
		Snapshot current = snapshot;
		return new Summary(current.revision(), current.packages().size());
	}

	/**
	 * Publishes a successful operation and then dispatches one post-publication event.
	 * The caller is responsible for invoking this on the primary server thread.
	 */
	public void publish(Map<String, AirdropPackage> candidate, PackageRegistryCause cause) {
		PackageRegistryCause requiredCause = Objects.requireNonNull(cause, "cause");
		Map<String, AirdropPackage> nextPackages = orderedIsolated(candidate);
		Snapshot previous = snapshot;
		long nextRevision = Math.addExact(previous.revision(), 1L);
		PackageRegistryChange change = change(
				nextRevision, requiredCause, previous.packages(), nextPackages);
		snapshot = new Snapshot(nextRevision, change.packages());
		eventDispatcher.accept(new PackageRegistryChangedEvent(change));
	}

	private static PackageRegistryChange change(
			long revision,
			PackageRegistryCause cause,
			Map<String, AirdropPackage> previous,
			Map<String, AirdropPackage> next) {
		Map<String, AirdropPackage> created = new LinkedHashMap<>();
		Map<String, AirdropPackage> updated = new LinkedHashMap<>();
		Map<String, AirdropPackage> deleted = new LinkedHashMap<>();
		for (Map.Entry<String, AirdropPackage> entry : next.entrySet()) {
			AirdropPackage old = previous.get(entry.getKey());
			if (old == null) {
				created.put(entry.getKey(), entry.getValue());
			} else if (!samePackage(old, entry.getValue())) {
				updated.put(entry.getKey(), entry.getValue());
			}
		}
		for (Map.Entry<String, AirdropPackage> entry : previous.entrySet()) {
			if (!next.containsKey(entry.getKey())) {
				deleted.put(entry.getKey(), entry.getValue());
			}
		}
		return new PackageRegistryChange(revision, cause, next, created, updated, deleted);
	}

	private static Map<String, AirdropPackage> orderedIsolated(
			Map<String, AirdropPackage> candidate) {
		Objects.requireNonNull(candidate, "candidate");
		List<AirdropPackage> sorted = candidate.values().stream()
				.map(pkg -> Objects.requireNonNull(pkg, "package snapshot"))
				.sorted(Comparator.comparing(AirdropPackage::name, String.CASE_INSENSITIVE_ORDER)
						.thenComparing(AirdropPackage::name))
				.toList();
		Map<String, AirdropPackage> copy = new LinkedHashMap<>();
		for (AirdropPackage pkg : sorted) {
			AirdropPackage isolated = new AirdropPackage(pkg.name(), pkg.price(), pkg.items());
			if (copy.putIfAbsent(isolated.name(), isolated) != null) {
				throw new IllegalArgumentException("Duplicate package name: " + isolated.name());
			}
		}
		return copy;
	}

	private static boolean samePackage(AirdropPackage first, AirdropPackage second) {
		return first.name().equals(second.name())
				&& first.price().compareTo(second.price()) == 0
				&& first.items().equals(second.items());
	}
}
