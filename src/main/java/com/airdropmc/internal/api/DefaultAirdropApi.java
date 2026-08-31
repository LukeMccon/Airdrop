package com.airdropmc.internal.api;

import com.airdropmc.api.AirdropApi;
import com.airdropmc.api.AirdropPackage;
import com.airdropmc.api.AirdropStatus;
import com.airdropmc.api.AirdropVersions;
import com.airdropmc.api.AirdropView;
import com.airdropmc.api.EconomyState;
import com.airdropmc.api.ReadinessState;
import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.internal.drop.DropRequestCoordinator;
import com.airdropmc.internal.drop.InternalDropRequests;
import com.airdropmc.packages.Package;
import com.airdropmc.exceptions.PackageNotFoundException;
import com.airdropmc.packages.PackageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Default plugin-owned implementation of the supported service. */
@ApiStatus.Internal
final class DefaultAirdropApi implements AirdropApi, InternalDropRequests {

	private final AirdropVersions versions;
	private final DropRequestCoordinator requests;
	private final CompletableFuture<AirdropApi> readiness = new CompletableFuture<>();
	private final CompletionStage<AirdropApi> readinessView = readiness.minimalCompletionStage();

	private volatile ReadinessState state = ReadinessState.STARTING;
	private volatile EconomyState economy = EconomyState.STARTING;
	private volatile String economyProviderName;
	private volatile List<String> degradedReasons = List.of();
	private volatile AirdropStatus status = snapshot(
			ReadinessState.STARTING, EconomyState.STARTING, null, List.of());

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
		return status;
	}

	@Override
	public List<AirdropPackage> listPackages() {
		requirePrimaryThread("listPackages");
		return PackageManager.getPackages().stream()
				.sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()))
				.map(this::requirePackageSnapshot)
				.toList();
	}

	@Override
	public Optional<AirdropPackage> findPackage(String name) {
		requirePrimaryThread("findPackage");
		String requiredName = Objects.requireNonNull(name, "name");
		try {
			return Optional.of(ApiModelMapper.packageSnapshot(PackageManager.get(requiredName)));
		} catch (PackageNotFoundException ignored) {
			return Optional.empty();
		}
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
		return List.of();
	}

	@Override
	public Optional<AirdropView> findByRequestId(UUID requestId) {
		Objects.requireNonNull(requestId, "requestId");
		return Optional.empty();
	}

	@Override
	public Optional<AirdropView> findByCrateId(UUID crateId) {
		Objects.requireNonNull(crateId, "crateId");
		return Optional.empty();
	}

	@Override
	public Optional<AirdropView> findByFallingEntity(FallingBlock entity) {
		requirePrimaryThread("findByFallingEntity");
		Objects.requireNonNull(entity, "entity");
		return Optional.empty();
	}

	@Override
	public Optional<AirdropView> findByLandedBlock(Block block) {
		requirePrimaryThread("findByLandedBlock");
		Objects.requireNonNull(block, "block");
		return Optional.empty();
	}

	synchronized void publishEconomy(
			EconomyState economy,
			String providerName,
			List<String> degradedReasons) {
		this.economy = Objects.requireNonNull(economy, "economy");
		this.economyProviderName = providerName;
		this.degradedReasons = List.copyOf(Objects.requireNonNull(
				degradedReasons, "degradedReasons"));
		status = snapshot(state, this.economy, this.economyProviderName, this.degradedReasons);
	}

	synchronized void refreshPackageCount() {
		status = snapshot(state, economy, economyProviderName, degradedReasons);
	}

	synchronized void publishReady() {
		if (state != ReadinessState.STARTING) {
			return;
		}
		requests.startAccepting();
		status = snapshot(ReadinessState.READY, economy, economyProviderName, degradedReasons);
		state = ReadinessState.READY;
		readiness.complete(this);
	}

	synchronized void publishFailure(Throwable failure) {
		Objects.requireNonNull(failure, "failure");
		if (state != ReadinessState.STARTING) {
			return;
		}
		status = snapshot(ReadinessState.FAILED, economy, economyProviderName, degradedReasons);
		state = ReadinessState.FAILED;
		readiness.completeExceptionally(failure);
	}

	synchronized void publishStopping() {
		if (state == ReadinessState.STOPPING) {
			return;
		}
		status = snapshot(ReadinessState.STOPPING, economy, economyProviderName, degradedReasons);
		state = ReadinessState.STOPPING;
		readiness.completeExceptionally(
				new IllegalStateException("Airdrop stopped before becoming ready"));
	}

	void stopRequests() {
		requests.stop();
	}

	private AirdropPackage requirePackageSnapshot(String name) {
		try {
			return ApiModelMapper.packageSnapshot(PackageManager.get(name));
		} catch (PackageNotFoundException failure) {
			throw new IllegalStateException("Published package disappeared during primary-thread lookup", failure);
		}
	}

	private static AirdropStatus snapshot(
			ReadinessState state,
			EconomyState economy,
			String providerName,
			List<String> degradedReasons) {
		return new AirdropStatus(
				state,
				economy,
				providerName,
				PackageManager.getPackages().size(),
				0,
				0,
				degradedReasons);
	}

	private static void requirePrimaryThread(String method) {
		if (!Bukkit.isPrimaryThread()) {
			throw new IllegalStateException(
					"AirdropApi." + method + " must be called on the primary server thread");
		}
	}
}
