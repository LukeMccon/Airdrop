package com.airdropmc.controllers;

import com.airdropmc.Airdrop;
import com.airdropmc.Crate;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.config.DropOptions;
import com.airdropmc.economy.EconomyPlayer;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.events.PackageDropEvent;
import com.airdropmc.exceptions.DropLimitException;
import com.airdropmc.exceptions.EconomyUnavailableException;
import com.airdropmc.exceptions.InsufficientPermissionsException;
import com.airdropmc.exceptions.SkyNotClearException;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLocationKey;
import com.airdropmc.packages.Package;
import com.airdropmc.paid.PaidDropSession;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.List;

/**
 * Validates drop requests, reserves admission capacity, and starts crate
 * creation. Priced player requests continue through the asynchronous economy
 * flow after this controller returns.
 */
public class DropController {

	private static final int ZERO_BLOCKS = 0;
	private static final double HALF_BLOCK = 0.5;

	private record DropTarget(Location spawnLocation, DropLocationKey landingKey) {
	}

	/**
	 * Starts an unpaid system drop with default options.
	 *
	 * @param pkg package to drop
	 * @param world target world
	 * @param loc requested location
	 * @throws SkyNotClearException if {@code loc} is below the highest block in its column
	 * @throws DropLimitException if admission rejects the request for
	 *         {@link DropLimitException.Reason#FALLING_CAPACITY},
	 *         {@link DropLimitException.Reason#LANDED_CAPACITY},
	 *         {@link DropLimitException.Reason#LOCATION_RESERVED}, or
	 *         {@link DropLimitException.Reason#SHUTTING_DOWN}
	 */
	public static void dropPackage(Package pkg, World world, Location loc)
			throws SkyNotClearException, DropLimitException {
		dropPackage(pkg, world, loc, DropOptions.createDefault());
	}

	/**
	 * Starts an unpaid system drop.
	 *
	 * @param pkg package to drop
	 * @param world target world
	 * @param loc requested location
	 * @param options per-drop options, or {@code null} for defaults
	 * @throws SkyNotClearException if {@code loc} is below the highest block in its column
	 * @throws DropLimitException if admission rejects the request for
	 *         {@link DropLimitException.Reason#FALLING_CAPACITY},
	 *         {@link DropLimitException.Reason#LANDED_CAPACITY},
	 *         {@link DropLimitException.Reason#LOCATION_RESERVED}, or
	 *         {@link DropLimitException.Reason#SHUTTING_DOWN}
	 */
	public static void dropPackage(Package pkg, World world, Location loc, DropOptions options)
			throws SkyNotClearException, DropLimitException {
		DropOptions resolvedOptions = options != null ? options : DropOptions.createDefault();
		DropTarget target = getDropTarget(world, loc, resolvedOptions);
		DropAdmissionController.Lease lease = requireAdmissionController().acquireSystem(
				target.landingKey(), ConfigKeys.getDropLimitSettings());
		try {
			List<ItemStack> items = pkg.getItems();
			dropPackageAtLocation(items, world, target.spawnLocation(), resolvedOptions, lease);
		} catch (RuntimeException failure) {
			lease.close();
			throw failure;
		}
	}

	/**
	 * Starts an unpaid system drop at a player's location with default options.
	 *
	 * @param pkg package to drop
	 * @param player target player
	 * @throws SkyNotClearException if the player is below the highest block in the column
	 * @throws DropLimitException if admission rejects the request for
	 *         {@link DropLimitException.Reason#FALLING_CAPACITY},
	 *         {@link DropLimitException.Reason#LANDED_CAPACITY},
	 *         {@link DropLimitException.Reason#LOCATION_RESERVED}, or
	 *         {@link DropLimitException.Reason#SHUTTING_DOWN}
	 */
	public static void dropPackageOnPlayer(Package pkg, Player player)
			throws SkyNotClearException, DropLimitException {
		dropPackageOnPlayer(pkg, player, DropOptions.createDefault());
	}

	/**
	 * Starts an unpaid system drop at a player's location.
	 *
	 * @param pkg package to drop
	 * @param player target player
	 * @param options per-drop options, or {@code null} for defaults
	 * @throws SkyNotClearException if the player is below the highest block in the column
	 * @throws DropLimitException if admission rejects the request for
	 *         {@link DropLimitException.Reason#FALLING_CAPACITY},
	 *         {@link DropLimitException.Reason#LANDED_CAPACITY},
	 *         {@link DropLimitException.Reason#LOCATION_RESERVED}, or
	 *         {@link DropLimitException.Reason#SHUTTING_DOWN}
	 */
	public static void dropPackageOnPlayer(Package pkg, Player player, DropOptions options)
			throws SkyNotClearException, DropLimitException {
		DropOptions resolvedOptions = options != null ? options : DropOptions.createDefault();
		dropPackage(pkg, player.getWorld(), player.getLocation(), resolvedOptions);
	}

	/**
	 * Validates and starts a player-requested drop with default options. For a
	 * priced package, returning means only that the asynchronous payment flow
	 * started; it does not confirm payment or delivery.
	 *
	 * @param pkg package to drop
	 * @param player requesting player
	 * @throws EconomyUnavailableException if a priced package is requested while
	 *         economy support is disabled or no provider is available
	 * @throws InsufficientPermissionsException if the player cannot request the package
	 * @throws SkyNotClearException if the player is below the highest block in the column
	 * @throws DropLimitException if player admission rejects the request for any
	 *         {@link DropLimitException.Reason}
	 */
	public static void playerInitiatedDropPackage(Package pkg, Player player)
			throws EconomyUnavailableException,
			InsufficientPermissionsException, SkyNotClearException, DropLimitException {
		playerInitiatedDropPackage(pkg, player, DropOptions.createDefault());
	}

	/**
	 * Validates and starts a player-requested drop. For a priced package,
	 * returning means only that the asynchronous payment flow started; it does
	 * not confirm payment or delivery.
	 *
	 * @param pkg package to drop
	 * @param player requesting player
	 * @param options per-drop options, or {@code null} for defaults
	 * @throws EconomyUnavailableException if a priced package is requested while
	 *         economy support is disabled or no provider is available
	 * @throws InsufficientPermissionsException if the player cannot request the package
	 * @throws SkyNotClearException if the player is below the highest block in the column
	 * @throws DropLimitException if player admission rejects the request for any
	 *         {@link DropLimitException.Reason}
	 */
	public static void playerInitiatedDropPackage(Package pkg, Player player, DropOptions options)
			throws EconomyUnavailableException,
			InsufficientPermissionsException, SkyNotClearException, DropLimitException {
		DropOptions resolvedOptions = options != null ? options : DropOptions.createDefault();
		if (!PermissionsHelper.hasPermission(player, pkg.getName())) {
			throw new InsufficientPermissionsException(pkg.getName());
		}
		double packagePrice = pkg.getPrice();
		boolean priced = packagePrice > 0.0;
		boolean economyEnabled = ConfigKeys.isEconomyEnabled();
		EconomyProvider economy = Airdrop.getEconomyProvider();
		if (priced && !economyEnabled) {
			throw new EconomyUnavailableException(EconomyUnavailableException.Reason.DISABLED);
		}
		if (priced && economy == null) {
			throw new EconomyUnavailableException(EconomyUnavailableException.Reason.NO_PROVIDER);
		}

		World world = player.getWorld();
		DropTarget target = getDropTarget(world, player.getLocation(), resolvedOptions);
		DropAdmissionController.Lease lease = requireAdmissionController().acquirePlayer(
				player.getUniqueId(), PermissionsHelper.hasCooldownBypass(player), target.landingKey(),
				ConfigKeys.getDropLimitSettings());
		List<ItemStack> items;
		try {
			items = pkg.getItems();
		} catch (RuntimeException failure) {
			lease.close();
			throw failure;
		}

		if (!priced) {
			try {
				dropPackageAtLocation(items, world, target.spawnLocation(), resolvedOptions, lease);
			} catch (RuntimeException failure) {
				lease.close();
				throw failure;
			}
			return;
		}

		Airdrop plugin = Airdrop.getPluginInstance();
		if (plugin == null || !plugin.isEnabled()) {
			lease.close();
			throw new IllegalStateException("Cannot start paid drop while plugin is unavailable");
		}

		PaidDropSession session = new PaidDropSession(
				plugin,
				economy,
				new EconomyPlayer(player.getUniqueId(), player.getName()),
				BigDecimal.valueOf(packagePrice),
				lease,
				paidSession -> dropPackageAtLocation(
						items, world, target.spawnLocation(), resolvedOptions, lease, true,
						outcome -> {
							if (outcome == Crate.Outcome.LANDED) {
								paidSession.landed();
							} else {
								paidSession.failed();
							}
						}));
		try {
			session.start();
		} catch (RuntimeException failure) {
			lease.close();
			throw failure;
		}
	}

	private static DropTarget getDropTarget(World world, Location requested, DropOptions options)
			throws SkyNotClearException {
		Location ground = world.getHighestBlockAt(requested.getBlockX(), requested.getBlockZ()).getLocation()
				.add(HALF_BLOCK, ZERO_BLOCKS, HALF_BLOCK);
		if (requested.getBlockY() < ground.getBlockY()) {
			throw new SkyNotClearException(requested);
		}
		Location spawn = ground.clone().add(ZERO_BLOCKS, options.getDropHeight(), ZERO_BLOCKS);
		Location intendedBarrel = ground.clone().add(ZERO_BLOCKS, 1, ZERO_BLOCKS);
		return new DropTarget(spawn, DropLocationKey.from(intendedBarrel));
	}

	private static void dropPackageAtLocation(List<ItemStack> items, World world, Location spawn,
			DropOptions options, DropAdmissionController.Lease lease) {
		dropPackageAtLocation(items, world, spawn, options, lease, false, ignored -> { });
	}

	private static void dropPackageAtLocation(List<ItemStack> items, World world, Location spawn,
			DropOptions options, DropAdmissionController.Lease lease, boolean paid,
			java.util.function.Consumer<Crate.Outcome> outcomeListener) {
		Crate crate = null;
		try {
			crate = new Crate(spawn.clone(), world, items, options, lease, paid, outcomeListener);
			crate.dropCrate();
			Bukkit.getPluginManager().callEvent(new PackageDropEvent(crate, world, crate.getDropLocation()));
			lease.commitSpawn();
		} catch (RuntimeException failure) {
			if (crate != null) {
				cleanupFailedCrate(crate, failure);
			}
			lease.close();
			throw failure;
		}
	}

	private static void cleanupFailedCrate(Crate crate, RuntimeException failure) {
		try {
			CrateManager.removeCrateAndDestroy(crate);
		} catch (RuntimeException cleanupFailure) {
			failure.addSuppressed(cleanupFailure);
		}
	}

	private static DropAdmissionController requireAdmissionController() {
		DropAdmissionController controller = Airdrop.getDropAdmissionController();
		if (controller == null) {
			throw new IllegalStateException("Drop admission is unavailable");
		}
		return controller;
	}
}
