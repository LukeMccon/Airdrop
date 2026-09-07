package com.airdropmc.controllers;

import com.airdropmc.Airdrop;
import com.airdropmc.api.AirdropApi;
import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.DropRejectionReason;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.config.DropOptions;
import com.airdropmc.exceptions.DropLimitException;
import com.airdropmc.exceptions.EconomyUnavailableException;
import com.airdropmc.exceptions.InsufficientPermissionsException;
import com.airdropmc.exceptions.SkyNotClearException;
import com.airdropmc.packages.Package;
import com.airdropmc.internal.drop.InternalDropRequests;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** Legacy implementation adapters for the supported correlated request API. */
@ApiStatus.Internal
public final class DropController {

	private DropController() {
	}

	/** Starts a typed player request for command and internal callers. */
	public static DropHandle requestPlayerDrop(
			Player player, String packageName, DropRequestOptions options) {
		return requireApi().requestPlayerDrop(player, packageName, options);
	}

	/** Starts a typed unpaid system request for internal callers. */
	public static DropHandle requestSystemDrop(
			Location location, String packageName, DropRequestOptions options) {
		return requireApi().requestSystemDrop(location, packageName, options);
	}

	/** @deprecated use {@link AirdropApi#requestSystemDrop(Location, String, DropRequestOptions)} */
	@Deprecated(forRemoval = false)
	public static void dropPackage(Package pkg, World world, Location loc)
			throws SkyNotClearException, DropLimitException {
		dropPackage(pkg, world, loc, DropOptions.createDefault());
	}

	/** @deprecated use {@link AirdropApi#requestSystemDrop(Location, String, DropRequestOptions)} */
	@Deprecated(forRemoval = false)
	public static void dropPackage(Package pkg, World world, Location loc, DropOptions options)
			throws SkyNotClearException, DropLimitException {
		Package requiredPackage = Objects.requireNonNull(pkg, "pkg");
		World requiredWorld = Objects.requireNonNull(world, "world");
		Location requiredLocation = Objects.requireNonNull(loc, "loc");
		if (requiredLocation.getWorld() == null
				|| !requiredWorld.getUID().equals(requiredLocation.getWorld().getUID())) {
			throw new IllegalArgumentException("world and loc must identify the same world");
		}
		DropHandle handle = requireInternalRequests().requestSystemDrop(
				requiredLocation, requiredPackage, toPublicOptions(options));
		throwLegacySystemRejection(handle);
	}

	/** @deprecated use {@link AirdropApi#requestSystemDrop(Location, String, DropRequestOptions)} */
	@Deprecated(forRemoval = false)
	public static void dropPackageOnPlayer(Package pkg, Player player)
			throws SkyNotClearException, DropLimitException {
		dropPackageOnPlayer(pkg, player, DropOptions.createDefault());
	}

	/** @deprecated use {@link AirdropApi#requestSystemDrop(Location, String, DropRequestOptions)} */
	@Deprecated(forRemoval = false)
	public static void dropPackageOnPlayer(Package pkg, Player player, DropOptions options)
			throws SkyNotClearException, DropLimitException {
		Player requiredPlayer = Objects.requireNonNull(player, "player");
		dropPackage(pkg, requiredPlayer.getWorld(), requiredPlayer.getLocation(), options);
	}

	/** @deprecated use {@link AirdropApi#requestPlayerDrop(Player, String, DropRequestOptions)} */
	@Deprecated(forRemoval = false)
	public static void playerInitiatedDropPackage(Package pkg, Player player)
			throws EconomyUnavailableException, InsufficientPermissionsException,
			SkyNotClearException, DropLimitException {
		playerInitiatedDropPackage(pkg, player, DropOptions.createDefault());
	}

	/** @deprecated use {@link AirdropApi#requestPlayerDrop(Player, String, DropRequestOptions)} */
	@Deprecated(forRemoval = false)
	public static void playerInitiatedDropPackage(
			Package pkg, Player player, DropOptions options)
			throws EconomyUnavailableException, InsufficientPermissionsException,
			SkyNotClearException, DropLimitException {
		Package requiredPackage = Objects.requireNonNull(pkg, "pkg");
		DropHandle handle = requireInternalRequests().requestPlayerDrop(
				Objects.requireNonNull(player, "player"),
				requiredPackage,
				toPublicOptions(options));
		throwLegacyPlayerRejection(handle);
	}

	private static DropRequestOptions toPublicOptions(DropOptions options) {
		DropOptions required = options != null ? options : DropOptions.createDefault();
		return DropRequestOptions.defaults()
				.withChickenCount(required.getChickenCount())
				.withFallingSpeed(required.getFallingSpeed())
				.withDropHeight(required.getDropHeight())
				.withLandingEffects(required.shouldShowLandingEffects())
				.withContinuousEffects(required.shouldShowContinuousEffects())
				.withFlareEffects(required.shouldShowFlareEffects())
				.withSmokeEnabled(required.isSmokeEnabled())
				.withSmokeHeight(required.getSmokeHeight());
	}

	private static void throwLegacySystemRejection(DropHandle handle)
			throws SkyNotClearException, DropLimitException {
		DropOutcome.Rejected rejection = immediateRejection(handle);
		if (rejection == null) {
			return;
		}
		switch (rejection.rejection().reason()) {
			case SKY_NOT_CLEAR -> throw new SkyNotClearException(handle.descriptor().requestedLocation());
			case REQUEST_PENDING, COOLDOWN, FALLING_CAPACITY, LANDED_CAPACITY,
					LOCATION_RESERVED, SHUTTING_DOWN -> throw toLimitException(rejection);
			default -> throw new IllegalStateException(rejection.rejection().diagnostic());
		}
	}

	private static void throwLegacyPlayerRejection(DropHandle handle)
			throws EconomyUnavailableException, InsufficientPermissionsException,
			SkyNotClearException, DropLimitException {
		DropOutcome.Rejected rejection = immediateRejection(handle);
		if (rejection == null) {
			return;
		}
		switch (rejection.rejection().reason()) {
			case INSUFFICIENT_PERMISSION -> throw new InsufficientPermissionsException(
					handle.descriptor().requestedPackageName());
			case SKY_NOT_CLEAR -> throw new SkyNotClearException(handle.descriptor().requestedLocation());
			case ECONOMY_DISABLED -> throw new EconomyUnavailableException(
					EconomyUnavailableException.Reason.DISABLED);
			case ECONOMY_PROVIDER_UNAVAILABLE -> throw new EconomyUnavailableException(
					EconomyUnavailableException.Reason.NO_PROVIDER);
			case REQUEST_PENDING, COOLDOWN, FALLING_CAPACITY, LANDED_CAPACITY,
					LOCATION_RESERVED, SHUTTING_DOWN -> throw toLimitException(rejection);
			default -> throw new IllegalStateException(rejection.rejection().diagnostic());
		}
	}

	private static DropOutcome.Rejected immediateRejection(DropHandle handle) {
		DropOutcome outcome = handle.outcome().toCompletableFuture().getNow(null);
		return outcome instanceof DropOutcome.Rejected rejected ? rejected : null;
	}

	private static DropLimitException toLimitException(DropOutcome.Rejected rejection) {
		DropLimitException.Reason reason = switch (rejection.rejection().reason()) {
			case REQUEST_PENDING -> DropLimitException.Reason.REQUEST_PENDING;
			case COOLDOWN -> DropLimitException.Reason.COOLDOWN;
			case FALLING_CAPACITY -> DropLimitException.Reason.FALLING_CAPACITY;
			case LANDED_CAPACITY -> DropLimitException.Reason.LANDED_CAPACITY;
			case LOCATION_RESERVED -> DropLimitException.Reason.LOCATION_RESERVED;
			case SHUTTING_DOWN -> DropLimitException.Reason.SHUTTING_DOWN;
			default -> throw new IllegalArgumentException("Not a legacy limit rejection");
		};
		long retrySeconds = rejection.rejection().retryAfter()
				.map(java.time.Duration::toSeconds)
				.orElse(0L);
		return new DropLimitException(reason, retrySeconds);
	}

	private static AirdropApi requireApi() {
		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin == null || !plugin.isEnabled()) {
			throw new IllegalStateException("Airdrop extension service is unavailable");
		}
		AirdropApi api = plugin.getServer().getServicesManager().load(AirdropApi.class);
		if (api == null) {
			throw new IllegalStateException("Airdrop extension service is unavailable");
		}
		return api;
	}

	private static InternalDropRequests requireInternalRequests() {
		AirdropApi api = requireApi();
		if (api instanceof InternalDropRequests requests) {
			return requests;
		}
		throw new IllegalStateException("Airdrop internal drop service is unavailable");
	}
}
