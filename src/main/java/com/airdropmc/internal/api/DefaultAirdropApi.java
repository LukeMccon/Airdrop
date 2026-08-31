package com.airdropmc.internal.api;

import com.airdropmc.api.AirdropApi;
import com.airdropmc.api.AirdropPackage;
import com.airdropmc.api.AirdropStatus;
import com.airdropmc.api.AirdropVersions;
import com.airdropmc.api.AirdropView;
import com.airdropmc.api.EconomyState;
import com.airdropmc.api.ReadinessState;
import com.airdropmc.api.PackageRegistryCause;
import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.internal.drop.DropRequestCoordinator;
import com.airdropmc.internal.drop.InternalDropRequests;
import com.airdropmc.internal.diagnostics.AirdropDiagnostics;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.helpers.AirdropLogger;
import com.airdropmc.limits.DropLimitSettings;
import com.airdropmc.limits.DropLocationKey;
import com.airdropmc.packages.Package;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Default plugin-owned implementation of the supported service. */
@ApiStatus.Internal
final class DefaultAirdropApi implements AirdropApi, InternalDropRequests {

	private final AirdropVersions versions;
	private final DropRequestCoordinator requests;
	private final PackageRegistryPublisher packageRegistry = new PackageRegistryPublisher();
	private final AirdropDiagnostics diagnostics = new AirdropDiagnostics();
	private final CompletableFuture<AirdropApi> readiness = new CompletableFuture<>();
	private final CompletionStage<AirdropApi> readinessView = readiness.minimalCompletionStage();

	private volatile ReadinessState state = ReadinessState.STARTING;
	private volatile EconomySnapshot economy = new EconomySnapshot(
			EconomyState.STARTING, null, List.of());
	private volatile PublishedLimits limits;

	private record EconomySnapshot(
			EconomyState state, String providerName, List<String> degradedReasons) {
	}

	private record PublishedLimits(int maxFalling, int maxLanded) {
	}

	DefaultAirdropApi(Plugin plugin, AirdropVersions versions) {
		this.versions = Objects.requireNonNull(versions, "versions");
		this.requests = new DropRequestCoordinator(Objects.requireNonNull(plugin, "plugin"));
	}

	@Override
	public CompletionStage<AirdropApi> readiness() {
		return readinessView;
	}

	@Override
	public ReadinessState state() {
		return state;
	}

	@Override
	public AirdropVersions versions() {
		return versions;
	}

	@Override
	public AirdropStatus status() {
		return snapshot(state, economy);
	}

	@Override
	public List<AirdropPackage> listPackages() {
		requirePrimaryThread("listPackages");
		return List.copyOf(packageRegistry.packages().values());
	}

	@Override
	public Optional<AirdropPackage> findPackage(String name) {
		requirePrimaryThread("findPackage");
		String requiredName = Objects.requireNonNull(name, "name");
		return packageRegistry.packages().values().stream()
				.filter(pkg -> pkg.name().equalsIgnoreCase(requiredName))
				.findFirst();
	}

	@Override
	public long packageRevision() {
		return packageRegistry.revision();
	}

	@Override
	public DropHandle requestPlayerDrop(
			Player player, String packageName, DropRequestOptions options) {
		requirePrimaryThread("requestPlayerDrop");
		return requests.requestPlayerDrop(player, packageName, options);
	}

	@Override
	public DropHandle requestSystemDrop(
			Location location, String packageName, DropRequestOptions options) {
		requirePrimaryThread("requestSystemDrop");
		return requests.requestSystemDrop(location, packageName, options);
	}

	@Override
	public DropHandle requestPlayerDrop(
			Player player, Package pkg, DropRequestOptions options) {
		requirePrimaryThread("requestPlayerDrop");
		return requests.requestPlayerDrop(player, pkg, options);
	}

	@Override
	public DropHandle requestSystemDrop(
			Location location, Package pkg, DropRequestOptions options) {
		requirePrimaryThread("requestSystemDrop");
		return requests.requestSystemDrop(location, pkg, options);
	}

	@Override
	public Collection<AirdropView> activeDrops() {
		return CrateManager.activeDrops();
	}

	@Override
	public Optional<AirdropView> findByRequestId(UUID requestId) {
		Objects.requireNonNull(requestId, "requestId");
		return CrateManager.findByRequestId(requestId);
	}

	@Override
	public Optional<AirdropView> findByCrateId(UUID crateId) {
		Objects.requireNonNull(crateId, "crateId");
		return CrateManager.findByCrateId(crateId);
	}

	@Override
	public Optional<AirdropView> findByFallingEntity(FallingBlock entity) {
		requirePrimaryThread("findByFallingEntity");
		Objects.requireNonNull(entity, "entity");
		return CrateManager.findByFallingEntityId(entity.getUniqueId());
	}

	@Override
	public Optional<AirdropView> findByLandedBlock(Block block) {
		requirePrimaryThread("findByLandedBlock");
		Objects.requireNonNull(block, "block");
		return CrateManager.findByLandedLocation(DropLocationKey.from(block.getLocation()));
	}

	void publishEconomy(
			EconomyState economy,
			String providerName,
			List<String> degradedReasons) {
		EconomyState requiredEconomy = Objects.requireNonNull(economy, "economy");
		String safeProviderName = providerName == null
				? null
				: AirdropDiagnostics.sanitizeLabel(providerName);
		this.economy = new EconomySnapshot(
				requiredEconomy,
				safeProviderName,
				List.copyOf(Objects.requireNonNull(degradedReasons, "degradedReasons")));
	}

	void publishLimits(DropLimitSettings settings) {
		DropLimitSettings required = Objects.requireNonNull(settings, "settings");
		limits = new PublishedLimits(required.maxFalling(), required.maxLanded());
	}

	void recordDiagnostic(AirdropDiagnostics.Category category, Throwable failure) {
		diagnostics.record(category, failure);
	}

	void clearDiagnostic(AirdropDiagnostics.Category category) {
		diagnostics.clear(category);
	}

	long publishPackages(Map<String, Package> packages, PackageRegistryCause cause) {
		Map<String, AirdropPackage> snapshots = packages.entrySet().stream()
				.collect(java.util.stream.Collectors.toMap(
						Map.Entry::getKey,
						entry -> ApiModelMapper.packageSnapshot(entry.getValue()),
						(first, ignored) -> first,
						java.util.LinkedHashMap::new));
		packageRegistry.publish(snapshots, cause);
		PackageRegistryPublisher.Summary summary = packageRegistry.summary();
		AirdropLogger.debugPublication(
				AirdropLogger.Publication.PACKAGE_REGISTRY,
				cause,
				summary.revision(),
				summary.packageCount());
		return summary.revision();
	}

	synchronized void publishReady() {
		if (state != ReadinessState.STARTING) {
			return;
		}
		requests.startAccepting();
		state = ReadinessState.READY;
		AirdropLogger.debugReadiness(ReadinessState.STARTING, ReadinessState.READY);
		diagnostics.clear(AirdropDiagnostics.Category.STARTUP);
		readiness.complete(this);
	}

	synchronized void publishFailure(Throwable failure) {
		Objects.requireNonNull(failure, "failure");
		if (state != ReadinessState.STARTING) {
			return;
		}
		diagnostics.record(AirdropDiagnostics.Category.STARTUP, failure);
		state = ReadinessState.FAILED;
		AirdropLogger.debugReadiness(ReadinessState.STARTING, ReadinessState.FAILED);
		readiness.completeExceptionally(failure);
	}

	synchronized void publishStopping() {
		if (state == ReadinessState.STOPPING) {
			return;
		}
		ReadinessState previous = state;
		state = ReadinessState.STOPPING;
		AirdropLogger.debugReadiness(previous, ReadinessState.STOPPING);
		readiness.completeExceptionally(
				new IllegalStateException("Airdrop stopped before becoming ready"));
	}

	void stopRequests() {
		requests.stop();
	}

	private AirdropStatus snapshot(
			ReadinessState state,
			EconomySnapshot economy) {
		List<String> effectiveReasons = new ArrayList<>(economy.degradedReasons());
		if (CrateManager.recoveryReport().degraded()
				&& !effectiveReasons.contains("crate-recovery-degraded")) {
			effectiveReasons.add("crate-recovery-degraded");
		}
		PublishedLimits publishedLimits = limits;
		Optional<AirdropDiagnostics.Snapshot> diagnostic = diagnostics.snapshot();
		PackageRegistryPublisher.Summary packageSummary = packageRegistry.summary();
		com.airdropmc.internal.drop.ActiveDropRegistry.Counts activeCounts =
				CrateManager.activeCounts();
		return new AirdropStatus(
				state,
				economy.state(),
				economy.providerName(),
				packageSummary.revision(),
				packageSummary.packageCount(),
				requests.pendingCount(),
				activeCounts.falling(),
				activeCounts.landed(),
				publishedLimits == null
						? OptionalInt.empty()
						: OptionalInt.of(publishedLimits.maxFalling()),
				publishedLimits == null
						? OptionalInt.empty()
						: OptionalInt.of(publishedLimits.maxLanded()),
				diagnostic.map(snapshot -> snapshot.category().name()),
				diagnostic.map(AirdropDiagnostics.Snapshot::message),
				effectiveReasons);
	}

	private static void requirePrimaryThread(String method) {
		if (!Bukkit.isPrimaryThread()) {
			throw new IllegalStateException(
					"AirdropApi." + method + " must be called on the primary server thread");
		}
	}
}
