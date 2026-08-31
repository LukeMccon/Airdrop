package com.airdropmc.api;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** Immutable post-publication package registry snapshot and deterministic diff. */
public final class PackageRegistryChange {

	private final long revision;
	private final PackageRegistryCause cause;
	private final Map<String, AirdropPackage> packages;
	private final Map<String, AirdropPackage> created;
	private final Map<String, AirdropPackage> updated;
	private final Map<String, AirdropPackage> deleted;

	/**
	 * Creates an immutable registry change.
	 *
	 * <p>This constructor must be called on the primary server thread because it
	 * copies Bukkit item stacks contained by package snapshots.</p>
	 *
	 * @param revision new positive registry revision
	 * @param cause operation which published the registry
	 * @param packages complete new registry snapshot
	 * @param created packages introduced by this publication
	 * @param updated packages whose price or item values changed
	 * @param deleted packages removed by this publication
	 */
	public PackageRegistryChange(
			long revision,
			PackageRegistryCause cause,
			Map<String, AirdropPackage> packages,
			Map<String, AirdropPackage> created,
			Map<String, AirdropPackage> updated,
			Map<String, AirdropPackage> deleted) {
		if (revision < 1L) {
			throw new IllegalArgumentException("revision must be positive");
		}
		this.revision = revision;
		this.cause = Objects.requireNonNull(cause, "cause");
		this.packages = isolate(packages);
		this.created = isolate(created);
		this.updated = isolate(updated);
		this.deleted = isolate(deleted);
	}

	/**
	 * Returns the newly published registry revision.
	 *
	 * @return new monotonic in-memory registry revision
	 */
	public long revision() {
		return revision;
	}

	/**
	 * Returns the operation which published this revision.
	 *
	 * @return operation that published this revision
	 */
	public PackageRegistryCause cause() {
		return cause;
	}

	/**
	 * Returns every package in the newly published registry.
	 *
	 * @return complete immutable registry snapshot in deterministic name order
	 */
	public Map<String, AirdropPackage> packages() {
		return packages;
	}

	/**
	 * Returns packages introduced by this revision.
	 *
	 * @return immutable packages introduced by this revision
	 */
	public Map<String, AirdropPackage> created() {
		return created;
	}

	/**
	 * Returns replacement snapshots for packages changed by this revision.
	 *
	 * @return immutable replacement snapshots for changed packages
	 */
	public Map<String, AirdropPackage> updated() {
		return updated;
	}

	/**
	 * Returns the previous snapshots for packages removed by this revision.
	 *
	 * @return immutable previous snapshots for removed packages
	 */
	public Map<String, AirdropPackage> deleted() {
		return deleted;
	}

	private static Map<String, AirdropPackage> isolate(Map<String, AirdropPackage> packages) {
		Objects.requireNonNull(packages, "packages");
		Map<String, AirdropPackage> copy = new LinkedHashMap<>();
		for (Map.Entry<String, AirdropPackage> entry : packages.entrySet()) {
			String name = Objects.requireNonNull(entry.getKey(), "package name");
			AirdropPackage pkg = Objects.requireNonNull(entry.getValue(), "package snapshot");
			copy.put(name, new AirdropPackage(pkg.name(), pkg.price(), pkg.items()));
		}
		return Collections.unmodifiableMap(copy);
	}
}
