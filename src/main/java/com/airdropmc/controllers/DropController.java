package com.airdropmc.controllers;

import com.airdropmc.Airdrop;
import com.airdropmc.Crate;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.config.DropOptions;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.economy.EconomyResult;
import com.airdropmc.events.PackageDropEvent;
import com.airdropmc.exceptions.CannotAffordException;
import com.airdropmc.exceptions.DropLimitException;
import com.airdropmc.exceptions.EconomyUnavailableException;
import com.airdropmc.exceptions.InsufficientPermissionsException;
import com.airdropmc.exceptions.SkyNotClearException;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.helpers.AirdropLogger;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLocationKey;
import com.airdropmc.packages.Package;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class DropController {

	private static final int ZERO_BLOCKS = 0;
	private static final double HALF_BLOCK = 0.5;

	private record DropTarget(Location spawnLocation, DropLocationKey landingKey) {
	}

	public static void dropPackage(Package pkg, World world, Location loc)
			throws SkyNotClearException, DropLimitException {
		dropPackage(pkg, world, loc, DropOptions.createDefault());
	}

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

	public static void dropPackageOnPlayer(Package pkg, Player player)
			throws SkyNotClearException, DropLimitException {
		dropPackageOnPlayer(pkg, player, DropOptions.createDefault());
	}

	public static void dropPackageOnPlayer(Package pkg, Player player, DropOptions options)
			throws SkyNotClearException, DropLimitException {
		DropOptions resolvedOptions = options != null ? options : DropOptions.createDefault();
		dropPackage(pkg, player.getWorld(), player.getLocation(), resolvedOptions);
	}

	public static void playerInitiatedDropPackage(Package pkg, Player player)
			throws CannotAffordException, EconomyUnavailableException,
			InsufficientPermissionsException, SkyNotClearException, DropLimitException {
		playerInitiatedDropPackage(pkg, player, DropOptions.createDefault());
	}

	public static void playerInitiatedDropPackage(Package pkg, Player player, DropOptions options)
			throws CannotAffordException, EconomyUnavailableException,
			InsufficientPermissionsException, SkyNotClearException, DropLimitException {
		DropOptions resolvedOptions = options != null ? options : DropOptions.createDefault();
		if (!PermissionsHelper.hasPermission(player, pkg.getName())) {
			throw new InsufficientPermissionsException(pkg.getName());
		}
		if (ConfigKeys.isEconomyEnabled() && Airdrop.getEconomyProvider() == null) {
			throw new EconomyUnavailableException();
		}
		if (!pkg.canAfford(player)) {
			throw new CannotAffordException(player.getName(), pkg.getPrice());
		}

		World world = player.getWorld();
		DropTarget target = getDropTarget(world, player.getLocation(), resolvedOptions);
		DropAdmissionController.Lease lease = requireAdmissionController().acquirePlayer(
				player.getUniqueId(), PermissionsHelper.hasCooldownBypass(player), target.landingKey(),
				ConfigKeys.getDropLimitSettings());
		boolean charged = false;
		try {
			List<ItemStack> items = pkg.getItems();
			charged = pkg.chargeUser(player);
			dropPackageAtLocation(items, world, target.spawnLocation(), resolvedOptions, lease);
		} catch (CannotAffordException | EconomyUnavailableException failure) {
			lease.close();
			throw failure;
		} catch (RuntimeException failure) {
			lease.close();
			if (charged) {
				attemptRefundOnDropFailure(pkg, player, failure);
			}
			throw failure;
		}
		if (charged) {
			sendChargeConfirmationBestEffort(pkg, player);
		}
	}

	private static void sendChargeConfirmationBestEffort(Package pkg, Player player) {
		try {
			ChatHandler.send(player, MessageKey.DROP_CHARGED,
					Map.of("amount", String.valueOf(pkg.getPrice())));
		} catch (RuntimeException failure) {
			try {
				AirdropLogger.log(Level.WARNING,
						"Drop succeeded but charge confirmation could not be sent to " + player.getName(), failure);
			} catch (RuntimeException loggingFailure) {
				failure.addSuppressed(loggingFailure);
			}
		}
	}

	private static void attemptRefundOnDropFailure(Package pkg, Player player, RuntimeException dropFailure) {
		if (!ConfigKeys.isEconomyEnabled()) {
			return;
		}
		EconomyProvider economy = Airdrop.getEconomyProvider();
		if (economy == null) {
			dropFailure.addSuppressed(new IllegalStateException(
					"Drop failed after charging " + player.getName()
							+ " but no economy provider was available for refund"));
			return;
		}
		try {
			EconomyResult result = economy.deposit(player, pkg.getPrice());
			if (result == null || !result.success()) {
				dropFailure.addSuppressed(new IllegalStateException(
						"Drop failed after charging " + player.getName() + " and refund transaction failed"));
			}
		} catch (RuntimeException refundFailure) {
			dropFailure.addSuppressed(refundFailure);
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
		Crate crate = null;
		try {
			crate = new Crate(spawn.clone(), world, items, options, lease);
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
			FallingBlock fallingBlock = crate.getFallingCrate();
			if (fallingBlock == null || !CrateManager.removeCrateAndDestroy(fallingBlock)) {
				crate.destroy();
			}
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
