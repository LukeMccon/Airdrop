package com.airdropmc.internal.drop;

import com.airdropmc.Airdrop;
import com.airdropmc.Crate;
import com.airdropmc.api.AirdropPackage;
import com.airdropmc.api.DeliveryStatus;
import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.DropRejection;
import com.airdropmc.api.DropRejectionReason;
import com.airdropmc.api.DropRequestDescriptor;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.api.DropSource;
import com.airdropmc.api.DropSpawnResult;
import com.airdropmc.api.FallingAirdropView;
import com.airdropmc.api.LandedAirdropView;
import com.airdropmc.api.PaymentStatus;
import com.airdropmc.api.ResolvedDropContext;
import com.airdropmc.api.ResolvedDropSettings;
import com.airdropmc.api.WorldPosition;
import com.airdropmc.api.event.AirdropLandedEvent;
import com.airdropmc.api.event.AirdropLandingAttemptEvent;
import com.airdropmc.api.event.AirdropOutcomeEvent;
import com.airdropmc.api.event.AirdropRequestEvent;
import com.airdropmc.api.event.AirdropSpawnedEvent;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.economy.EconomyPlayer;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.economy.EconomyResult;
import com.airdropmc.events.PackageDropEvent;
import com.airdropmc.events.PackageLandEvent;
import com.airdropmc.exceptions.DropLimitException;
import com.airdropmc.exceptions.PackageNotFoundException;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.helpers.AirdropLogger;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.internal.api.ApiModelMapper;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLocationKey;
import com.airdropmc.packages.Package;
import com.airdropmc.packages.PackageManager;
import com.airdropmc.paid.PaidDropSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Coordinates all free, paid, player, and system request phases. */
@ApiStatus.Internal
public final class DropRequestCoordinator {

	private static final double HALF_BLOCK = 0.5;

	private record DropTarget(Location spawn, Location landing, DropLocationKey landingKey) {
	}

	private final Plugin plugin;
	private final DropSettingsResolver settingsResolver;
	private final Map<UUID, DropRequestProcess> processes = new LinkedHashMap<>();
	private volatile int pendingCount;
	private boolean accepting;
	private boolean stopping;

	public DropRequestCoordinator(Plugin plugin) {
		this(plugin, new DropSettingsResolver());
	}

	DropRequestCoordinator(Plugin plugin, DropSettingsResolver settingsResolver) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.settingsResolver = Objects.requireNonNull(settingsResolver, "settingsResolver");
	}

	public void startAccepting() {
		requirePrimaryThread("startAccepting");
		if (!stopping) {
			accepting = true;
		}
	}

	public DropHandle requestPlayerDrop(
			Player player, String packageName, DropRequestOptions options) {
		requirePrimaryThread("requestPlayerDrop");
		Player requiredPlayer = Objects.requireNonNull(player, "player");
		String requiredPackage = requirePackageName(packageName);
		DropRequestOptions requiredOptions = Objects.requireNonNull(options, "options");
		Location requested = requiredPlayer.getLocation();
		DropRequestDescriptor descriptor = new DropRequestDescriptor(
				UUID.randomUUID(),
				DropSource.PLAYER,
				requiredPlayer.getUniqueId(),
				requiredPackage,
				requested);
		return begin(descriptor, requiredPlayer, requiredOptions, null);
	}

	public DropHandle requestSystemDrop(
			Location location, String packageName, DropRequestOptions options) {
		requirePrimaryThread("requestSystemDrop");
		Location requiredLocation = Objects.requireNonNull(location, "location");
		String requiredPackage = requirePackageName(packageName);
		DropRequestOptions requiredOptions = Objects.requireNonNull(options, "options");
		DropRequestDescriptor descriptor = new DropRequestDescriptor(
				UUID.randomUUID(),
				DropSource.SYSTEM,
				null,
				requiredPackage,
				requiredLocation);
		return begin(descriptor, null, requiredOptions, null);
	}

	/** Internal adapter for a package object already resolved by legacy code. */
	public DropHandle requestPlayerDrop(
			Player player, Package pkg, DropRequestOptions options) {
		requirePrimaryThread("requestPlayerDrop");
		Player requiredPlayer = Objects.requireNonNull(player, "player");
		Package requiredPackage = Objects.requireNonNull(pkg, "pkg");
		DropRequestOptions requiredOptions = Objects.requireNonNull(options, "options");
		DropRequestDescriptor descriptor = new DropRequestDescriptor(
				UUID.randomUUID(), DropSource.PLAYER, requiredPlayer.getUniqueId(),
				requiredPackage.getName(), requiredPlayer.getLocation());
		return begin(descriptor, requiredPlayer, requiredOptions, requiredPackage);
	}

	/** Internal adapter for a package object already resolved by legacy code. */
	public DropHandle requestSystemDrop(
			Location location, Package pkg, DropRequestOptions options) {
		requirePrimaryThread("requestSystemDrop");
		Location requiredLocation = Objects.requireNonNull(location, "location");
		Package requiredPackage = Objects.requireNonNull(pkg, "pkg");
		DropRequestOptions requiredOptions = Objects.requireNonNull(options, "options");
		DropRequestDescriptor descriptor = new DropRequestDescriptor(
				UUID.randomUUID(), DropSource.SYSTEM, null,
				requiredPackage.getName(), requiredLocation);
		return begin(descriptor, null, requiredOptions, requiredPackage);
	}

	public void stop() {
		requirePrimaryThread("stop");
		if (stopping) {
			return;
		}
		accepting = false;
		stopping = true;
		for (DropRequestProcess process : List.copyOf(processes.values())) {
			stop(process);
		}
	}

	int incompleteCount() {
		return processes.size();
	}

	/** @return incomplete request count safe for immutable status snapshots */
	public int pendingCount() {
		return pendingCount;
	}

	private DropHandle begin(
			DropRequestDescriptor descriptor,
			Player player,
			DropRequestOptions options,
			Package suppliedPackage) {
		DefaultDropHandle handle = new DefaultDropHandle(descriptor);
		DropRequestProcess process = new DropRequestProcess(handle);
		processes.put(handle.requestId(), process);
		publishPendingCount();
		AirdropLogger.debugRequest(handle.requestId(), AirdropLogger.RequestPhase.CREATED);

		if (!accepting) {
			DropRejectionReason reason = stopping
					? DropRejectionReason.SHUTTING_DOWN
					: DropRejectionReason.SERVICE_UNAVAILABLE;
			return reject(process, reason,
					stopping ? "Airdrop is shutting down" : "Airdrop is not ready",
					preResolutionPayment(descriptor.source()));
		}

		Package pkg;
		try {
			pkg = suppliedPackage != null
					? suppliedPackage
					: PackageManager.get(descriptor.requestedPackageName());
		} catch (PackageNotFoundException failure) {
			return reject(process, DropRejectionReason.UNKNOWN_PACKAGE,
					"Unknown package: " + descriptor.requestedPackageName(),
					preResolutionPayment(descriptor.source()));
		}

		ResolvedDropSettings settings;
		AirdropPackage packageSnapshot;
		DropTarget target;
		try {
			settings = settingsResolver.resolve(options);
			packageSnapshot = ApiModelMapper.packageSnapshot(pkg);
			target = resolveTarget(descriptor.requestedLocation(), settings);
		} catch (SkyBlocked failure) {
			return reject(process, DropRejectionReason.SKY_NOT_CLEAR,
					"Target is not open to the sky", paymentFor(descriptor.source(), pkg));
		} catch (RuntimeException failure) {
			return reject(process, DropRejectionReason.INVALID_TARGET,
					"Could not resolve the drop target", paymentFor(descriptor.source(), pkg));
		}

		ResolvedDropContext context = new ResolvedDropContext(
				descriptor, packageSnapshot, target.spawn(), target.landing(), settings);
		process.context = context;
		handle.publishContext(context);
		AirdropLogger.debugRequest(handle.requestId(), AirdropLogger.RequestPhase.RESOLVED);
		process.payment = paymentFor(descriptor.source(), packageSnapshot.price());
		AirdropRequestEvent requestEvent = new AirdropRequestEvent(context);
		process.requestEventPublished = true;
		Bukkit.getPluginManager().callEvent(requestEvent);
		if (process.phase == DropRequestProcess.Phase.TERMINAL) {
			return handle;
		}
		if (requestEvent.isCancelled()) {
			return reject(process, DropRejectionReason.CANCELLED,
					"Request cancelled by an AirdropRequestEvent listener", process.payment);
		}

		if (player != null && !PermissionsHelper.hasPermission(player, pkg.getName())) {
			return reject(process, DropRejectionReason.INSUFFICIENT_PERMISSION,
					"Player lacks package permission", process.payment);
		}

		DropAdmissionController admission = Airdrop.getDropAdmissionController();
		if (admission == null) {
			return reject(process, DropRejectionReason.SERVICE_UNAVAILABLE,
					"Drop admission is unavailable", process.payment);
		}
		try {
			process.lease = player == null
					? admission.acquireSystem(target.landingKey(), toLimitSettings(settings))
					: admission.acquirePlayer(
							player.getUniqueId(),
							PermissionsHelper.hasCooldownBypass(player),
							target.landingKey(),
							toLimitSettings(settings));
			process.phase = DropRequestProcess.Phase.ADMITTED;
			AirdropLogger.debugAdmission(
					handle.requestId(), AirdropLogger.AdmissionDecision.ACCEPTED, null);
		} catch (DropLimitException failure) {
			return reject(process, mapLimit(failure), limitDiagnostic(failure), process.payment,
					failure.getReason() == DropLimitException.Reason.COOLDOWN
							? Optional.of(Duration.ofSeconds(failure.getRetryAfterSeconds()))
							: Optional.empty());
		}

		if (player == null || packageSnapshot.price().signum() == 0) {
			process.payment = PaymentStatus.NOT_APPLICABLE;
			spawn(process);
			return handle;
		}
		if (!ConfigKeys.isEconomyEnabled()) {
			return reject(process, DropRejectionReason.ECONOMY_DISABLED,
					"Economy support is disabled", PaymentStatus.REJECTED);
		}
		EconomyProvider economy = Airdrop.getEconomyProvider();
		if (economy == null) {
			return reject(process, DropRejectionReason.ECONOMY_PROVIDER_UNAVAILABLE,
					"No economy provider is available", PaymentStatus.REJECTED);
		}

		startPayment(process, player, economy, packageSnapshot.price());
		return handle;
	}

	private void startPayment(
			DropRequestProcess process,
			Player player,
			EconomyProvider economy,
			BigDecimal amount) {
		process.phase = DropRequestProcess.Phase.CHECKING_AFFORDABILITY;
		AirdropLogger.debugRequest(
				process.handle.requestId(), AirdropLogger.RequestPhase.PAYMENT_PENDING);
		process.paymentSession = new PaidDropSession(
				plugin,
				economy,
				new EconomyPlayer(player.getUniqueId(), player.getName()),
				amount,
				process.handle.requestId(),
				(operation, result) -> acceptPayment(process, operation, result));
		try {
			process.paymentSession.start();
		} catch (RuntimeException failure) {
			reject(process, DropRejectionReason.PAYMENT_REJECTED,
					"Could not start payment", PaymentStatus.REJECTED);
		}
	}

	private void acceptPayment(
			DropRequestProcess process,
			PaidDropSession.Operation operation,
			EconomyResult result) {
		if (process.phase == DropRequestProcess.Phase.TERMINAL) {
			return;
		}
		switch (operation) {
			case AFFORDABILITY -> {
				if (result.outcome() == EconomyResult.Outcome.REJECTED) {
					reject(process, DropRejectionReason.INSUFFICIENT_FUNDS,
							"Player cannot afford the package", PaymentStatus.REJECTED);
				} else {
					reject(process, DropRejectionReason.AFFORDABILITY_UNKNOWN,
							"Affordability check was inconclusive", PaymentStatus.REJECTED);
				}
			}
			case WITHDRAWAL -> {
				if (result.outcome() == EconomyResult.Outcome.SUCCESS) {
					process.payment = PaymentStatus.CHARGED;
					spawn(process);
				} else if (result.outcome() == EconomyResult.Outcome.REJECTED) {
					reject(process, DropRejectionReason.PAYMENT_REJECTED,
							"Withdrawal was rejected", PaymentStatus.REJECTED);
				} else {
					finishFailure(process, DeliveryStatus.FAILED, PaymentStatus.UNKNOWN);
				}
			}
			case REFUND -> completeRefund(process, result);
		}
	}

	private void spawn(DropRequestProcess process) {
		if (process.phase == DropRequestProcess.Phase.TERMINAL || stopping) {
			if (stopping) {
				stop(process);
			}
			return;
		}
		process.phase = DropRequestProcess.Phase.SPAWNING;
		ResolvedDropContext context = Objects.requireNonNull(process.context, "context");
		World world = context.spawnLocation().getWorld();
		if (world == null) {
			failBeforeSpawn(process, DeliveryStatus.FAILED);
			return;
		}
		try {
			process.crate = new Crate(
					context.spawnLocation(),
					world,
					context.airdropPackage().items(),
					context.settings(),
					Objects.requireNonNull(process.lease, "lease"),
					process.payment == PaymentStatus.CHARGED,
					process.handle.requestId(),
					context,
					candidate -> acceptLandingAttempt(process, candidate),
					crate -> acceptLanded(process, crate),
					outcome -> acceptCrateOutcome(process, outcome));
			process.crate.dropCrate();
			process.lease.commitSpawn();
			FallingBlock falling = process.crate.getFallingCrate();
			FallingAirdropView view = new FallingAirdropView(
					UUID.fromString(process.crate.getCrateId()),
					falling.getUniqueId(),
					WorldPosition.from(falling.getLocation()),
					context);
			process.phase = DropRequestProcess.Phase.FALLING;
			if (!process.handle.completeSpawned(
					new DropSpawnResult.Spawned(context, view, process.payment))) {
				throw new IllegalStateException("Could not complete the request spawn stage");
			}
			if (process.phase != DropRequestProcess.Phase.FALLING || stopping || !plugin.isEnabled()) {
				return;
			}
			AirdropLogger.debugRequest(
					process.handle.requestId(), AirdropLogger.RequestPhase.SPAWNED);
			Bukkit.getPluginManager().callEvent(new AirdropSpawnedEvent(context, view));
			if (process.phase != DropRequestProcess.Phase.FALLING || stopping || !plugin.isEnabled()) {
				return;
			}
			Bukkit.getPluginManager().callEvent(new PackageDropEvent(
					process.crate, world, process.crate.getDropLocation()));
		} catch (RuntimeException failure) {
			Crate crate = process.crate;
			if (crate != null) {
				CrateManager.removeCrateAndDestroy(crate);
			}
			if (process.phase != DropRequestProcess.Phase.REFUNDING
					&& process.phase != DropRequestProcess.Phase.TERMINAL) {
				failBeforeSpawn(process, DeliveryStatus.FAILED);
			}
		}
	}

	private boolean acceptLandingAttempt(
			DropRequestProcess process, WorldPosition candidatePosition) {
		if (process.phase != DropRequestProcess.Phase.FALLING) {
			return false;
		}
		AirdropLogger.debugRequest(
				process.handle.requestId(), AirdropLogger.RequestPhase.LANDING_ATTEMPT);
		AirdropLandingAttemptEvent event = new AirdropLandingAttemptEvent(
				Objects.requireNonNull(process.context, "context"),
				Objects.requireNonNull(process.crate.snapshotFallingView(
						process.crate.getFallingCrate()), "fallingView"),
				candidatePosition);
		Bukkit.getPluginManager().callEvent(event);
		return process.phase == DropRequestProcess.Phase.FALLING && !event.isCancelled();
	}

	private void acceptLanded(DropRequestProcess process, Crate crate) {
		if (process.phase == DropRequestProcess.Phase.TERMINAL
				|| process.phase == DropRequestProcess.Phase.LANDED
				|| process.phase == DropRequestProcess.Phase.REFUNDING) {
			return;
		}
		ResolvedDropContext context = Objects.requireNonNull(process.context, "context");
		LandedAirdropView view = new LandedAirdropView(
				UUID.fromString(crate.getCrateId()),
				WorldPosition.from(crate.getLocation()),
				context,
				crate.getExpiresAtMillis(),
				crate.getOpened());
		// Landing is committed before listeners run, including listeners that disable the plugin.
		process.phase = DropRequestProcess.Phase.LANDED;
		AirdropLogger.debugRequest(
				process.handle.requestId(), AirdropLogger.RequestPhase.LANDED);
		Bukkit.getPluginManager().callEvent(new AirdropLandedEvent(context, view));
		Location landingLocation = crate.getLocation();
		Bukkit.getPluginManager().callEvent(new PackageLandEvent(
				crate,
				Objects.requireNonNull(landingLocation.getWorld(), "landing world"),
				landingLocation,
				landingLocation.getBlock()));
		finishSpawned(process, new DropOutcome.Landed(context, view, process.payment));
	}

	private void acceptCrateOutcome(DropRequestProcess process, Crate.Outcome outcome) {
		if (process.phase == DropRequestProcess.Phase.TERMINAL
				|| process.phase == DropRequestProcess.Phase.LANDED) {
			return;
		}
		if (outcome == Crate.Outcome.LANDED) {
			acceptLanded(process, Objects.requireNonNull(process.crate, "crate"));
			return;
		}
		DeliveryStatus delivery = outcome == Crate.Outcome.CANCELLED
				? DeliveryStatus.CANCELLED
				: DeliveryStatus.FAILED;
		failAfterResolution(process, delivery);
	}

	private void failBeforeSpawn(DropRequestProcess process, DeliveryStatus delivery) {
		failAfterResolution(process, delivery);
	}

	private void failAfterResolution(DropRequestProcess process, DeliveryStatus delivery) {
		if (process.payment == PaymentStatus.CHARGED && !stopping) {
			process.pendingFailure = delivery;
			process.phase = DropRequestProcess.Phase.REFUNDING;
			if (process.paymentSession != null && process.paymentSession.refund()) {
				return;
			}
			finishFailure(process, delivery, PaymentStatus.UNKNOWN);
			return;
		}
		PaymentStatus payment = process.payment == PaymentStatus.CHARGED
				? PaymentStatus.CHARGED
				: PaymentStatus.NOT_APPLICABLE;
		if (delivery == DeliveryStatus.SHUTDOWN) {
			finishFailure(process, delivery, payment);
		} else {
			finishFailure(process, delivery, PaymentStatus.NOT_APPLICABLE);
		}
	}

	private void completeRefund(DropRequestProcess process, EconomyResult result) {
		PaymentStatus payment = switch (result.outcome()) {
			case SUCCESS -> PaymentStatus.REFUNDED;
			case REJECTED -> PaymentStatus.REFUND_FAILED;
			case UNKNOWN -> PaymentStatus.UNKNOWN;
		};
		finishFailure(process, process.pendingFailure, payment);
	}

	private DropHandle reject(
			DropRequestProcess process,
			DropRejectionReason reason,
			String diagnostic,
			PaymentStatus payment) {
		return reject(process, reason, diagnostic, payment, Optional.empty());
	}

	private DropHandle reject(
			DropRequestProcess process,
			DropRejectionReason reason,
			String diagnostic,
			PaymentStatus payment,
			Optional<Duration> retryAfter) {
		if (process.phase == DropRequestProcess.Phase.TERMINAL) {
			return process.handle;
		}
		boolean admitted = process.lease != null;
		if (process.lease != null) {
			process.lease.close();
		}
		if (!admitted) {
			AirdropLogger.debugAdmission(
					process.handle.requestId(), AirdropLogger.AdmissionDecision.REJECTED, reason);
		}
		DropRejection rejection = new DropRejection(reason, diagnostic, retryAfter);
		DropOutcome.Rejected outcome = new DropOutcome.Rejected(
				process.handle.descriptor(), Optional.ofNullable(process.context), rejection, payment);
		process.phase = DropRequestProcess.Phase.TERMINAL;
		processes.remove(process.handle.requestId(), process);
		publishPendingCount();
		boolean completed = process.handle.completeNotSpawned(outcome);
		if (completed) {
			AirdropLogger.debugRequest(
					process.handle.requestId(), AirdropLogger.RequestPhase.TERMINAL, reason);
			publishOutcome(process, outcome);
		}
		return process.handle;
	}

	private void finishFailure(
			DropRequestProcess process, DeliveryStatus delivery, PaymentStatus payment) {
		DropOutcome.Failed outcome = new DropOutcome.Failed(
				Objects.requireNonNull(process.context, "context"), delivery, payment);
		boolean spawned = process.handle.spawn().toCompletableFuture().isDone()
				&& process.handle.spawn().toCompletableFuture().getNow(null)
						instanceof DropSpawnResult.Spawned;
		process.phase = DropRequestProcess.Phase.TERMINAL;
		if (!spawned && process.lease != null) {
			process.lease.close();
		}
		processes.remove(process.handle.requestId(), process);
		publishPendingCount();
		// Completion callbacks may immediately submit another request or inspect status.
		boolean completed = spawned
				? process.handle.completeOutcome(outcome)
				: process.handle.completeNotSpawned(outcome);
		if (completed) {
			AirdropLogger.debugRequest(
					process.handle.requestId(), AirdropLogger.RequestPhase.TERMINAL, delivery);
			publishOutcome(process, outcome);
		}
	}

	private void finishSpawned(DropRequestProcess process, DropOutcome outcome) {
		process.phase = DropRequestProcess.Phase.TERMINAL;
		processes.remove(process.handle.requestId(), process);
		publishPendingCount();
		boolean completed = process.handle.completeOutcome(outcome);
		if (completed) {
			AirdropLogger.debugRequest(
					process.handle.requestId(), AirdropLogger.RequestPhase.TERMINAL,
					outcome.delivery());
			publishOutcome(process, outcome);
		}
	}

	private void publishOutcome(DropRequestProcess process, DropOutcome outcome) {
		if (!process.requestEventPublished || process.context == null) {
			return;
		}
		Bukkit.getPluginManager().callEvent(new AirdropOutcomeEvent(process.context, outcome));
	}

	private void stop(DropRequestProcess process) {
		if (process.phase == DropRequestProcess.Phase.TERMINAL
				|| process.phase == DropRequestProcess.Phase.LANDED) {
			return;
		}
		DropRequestProcess.Phase phase = process.phase;
		PaidDropSession.State paymentPhase = process.paymentSession == null
				? null
				: process.paymentSession.stop();
		if (process.crate != null) {
			process.crate.clearOutcomeListener();
		}

		if (paymentPhase == PaidDropSession.State.CHECKING
				|| (paymentPhase == null
						&& phase == DropRequestProcess.Phase.CHECKING_AFFORDABILITY)) {
			reject(process, DropRejectionReason.SHUTTING_DOWN,
					"Airdrop stopped during affordability check", PaymentStatus.REJECTED);
			return;
		}
		if (paymentPhase == PaidDropSession.State.WITHDRAWING
				|| (paymentPhase == null
						&& phase == DropRequestProcess.Phase.WITHDRAWING)) {
			finishFailure(process, DeliveryStatus.SHUTDOWN, PaymentStatus.UNKNOWN);
			return;
		}
		if (paymentPhase == PaidDropSession.State.REFUNDING
				|| phase == DropRequestProcess.Phase.REFUNDING) {
			finishFailure(process, process.pendingFailure, PaymentStatus.UNKNOWN);
			return;
		}
		if (process.context == null) {
			reject(process, DropRejectionReason.SHUTTING_DOWN,
					"Airdrop is shutting down", preResolutionPayment(process.handle.descriptor().source()));
			return;
		}
		finishFailure(process, DeliveryStatus.SHUTDOWN,
				process.payment == PaymentStatus.CHARGED
						? PaymentStatus.CHARGED
						: PaymentStatus.NOT_APPLICABLE);
	}

	private static DropTarget resolveTarget(Location requested, ResolvedDropSettings settings) {
		World world = requested.getWorld();
		if (world == null) {
			throw new IllegalArgumentException("Requested location has no world");
		}
		Location ground = world.getHighestBlockAt(
				requested.getBlockX(), requested.getBlockZ()).getLocation().add(HALF_BLOCK, 0, HALF_BLOCK);
		if (requested.getBlockY() < ground.getBlockY()) {
			throw new SkyBlocked();
		}
		Location spawn = ground.clone().add(0, settings.dropHeight(), 0);
		Location landing = ground.clone().add(0, 1, 0);
		return new DropTarget(spawn, landing, DropLocationKey.from(landing));
	}

	private static com.airdropmc.limits.DropLimitSettings toLimitSettings(
			ResolvedDropSettings settings) {
		return new com.airdropmc.limits.DropLimitSettings(
				settings.requestCooldown(), settings.maxFalling(), settings.maxLanded(),
				settings.landedLifetime());
	}

	private static PaymentStatus preResolutionPayment(DropSource source) {
		return source == DropSource.SYSTEM
				? PaymentStatus.NOT_APPLICABLE
				: PaymentStatus.REJECTED;
	}

	private static PaymentStatus paymentFor(DropSource source, Package pkg) {
		return paymentFor(source, BigDecimal.valueOf(pkg.getPrice()));
	}

	private static PaymentStatus paymentFor(DropSource source, BigDecimal price) {
		return source == DropSource.SYSTEM || price.signum() == 0
				? PaymentStatus.NOT_APPLICABLE
				: PaymentStatus.REJECTED;
	}

	private static DropRejectionReason mapLimit(DropLimitException failure) {
		return switch (failure.getReason()) {
			case REQUEST_PENDING -> DropRejectionReason.REQUEST_PENDING;
			case COOLDOWN -> DropRejectionReason.COOLDOWN;
			case FALLING_CAPACITY -> DropRejectionReason.FALLING_CAPACITY;
			case LANDED_CAPACITY -> DropRejectionReason.LANDED_CAPACITY;
			case LOCATION_RESERVED -> DropRejectionReason.LOCATION_RESERVED;
			case SHUTTING_DOWN -> DropRejectionReason.SHUTTING_DOWN;
		};
	}

	private static String limitDiagnostic(DropLimitException failure) {
		return failure.getReason() == DropLimitException.Reason.COOLDOWN
				? "Request cooldown has not expired"
				: "Drop admission rejected: " + failure.getReason();
	}

	private static String requirePackageName(String packageName) {
		String required = Objects.requireNonNull(packageName, "packageName");
		if (required.isBlank()) {
			throw new IllegalArgumentException("packageName must not be blank");
		}
		return required;
	}

	private void publishPendingCount() {
		pendingCount = processes.size();
	}

	private static void requirePrimaryThread(String method) {
		if (!Bukkit.isPrimaryThread()) {
			throw new IllegalStateException(
					"DropRequestCoordinator." + method + " must run on the primary server thread");
		}
	}

	private static final class SkyBlocked extends RuntimeException {
	}
}
