package com.airdropmc;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import com.airdropmc.config.DropOptions;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLimitSettings;
import com.airdropmc.limits.DropLocationKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class CrateLandingLifecycleTest {

	private ServerMock server;
	private WorldMock world;
	private Airdrop plugin;
	private DropAdmissionController admission;
	private Block reservedBlock;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("landing_world");
		plugin = mock(Airdrop.class);
		when(plugin.isEnabled()).thenReturn(true);
		Airdrop.setPluginInstance(plugin);
		admission = new DropAdmissionController();
		reservedBlock = world.getBlockAt(10, 64, 10);
		CrateManager.clearAll();
	}

	@AfterEach
	void tearDown() {
		CrateManager.clearAll();
		admission.clear();
		Airdrop.setPluginInstance(null);
		MockBukkit.unmock();
	}

	@Test
	void land_convertsLeaseAndSchedulesExpiry() throws Exception {
		Crate crate = newCrate(reservedBlock);

		crate.land(reservedBlock);

		assertEquals(0, admission.snapshot().falling());
		assertEquals(1, admission.snapshot().landedClaims());
		assertSame(crate, CrateManager.getCrate(reservedBlock.getLocation()));
		assertEquals(Material.BARREL, reservedBlock.getType());
	}

	@Test
	void successfulLandingReportsLandedAndLaterDestroyDoesNotReportFailure() throws Exception {
		List<Crate.Outcome> outcomes = new ArrayList<>();
		Crate crate = newCrate(reservedBlock, outcomes);

		crate.land(reservedBlock);
		CrateManager.removeCrateAndDestroy(crate);

		assertEquals(List.of(Crate.Outcome.LANDED), outcomes);
	}

	@Test
	void expiry_removesBarrelAndReleasesLandedClaim() throws Exception {
		Crate crate = newCrate(reservedBlock);
		crate.land(reservedBlock);

		server.getScheduler().performTicks(12_000L);

		assertNull(CrateManager.getCrate(reservedBlock.getLocation()));
		assertEquals(Material.AIR, reservedBlock.getType());
		assertEquals(0, admission.snapshot().landedClaims());
	}

	@Test
	void land_rejectsActualBlockThatDoesNotMatchReservationBeforeMutation() throws Exception {
		Crate crate = newCrate(reservedBlock);
		Block unexpected = world.getBlockAt(11, 64, 10);

		assertThrows(IllegalStateException.class, () -> crate.land(unexpected));

		assertEquals(Material.AIR, unexpected.getType());
		assertNull(CrateManager.getCrate(unexpected.getLocation()));
		assertEquals(0, admission.snapshot().landedClaims());
	}

	@Test
	void land_rejectsUnavailablePluginInsteadOfCreatingImmortalCrate() throws Exception {
		Crate crate = newCrate(reservedBlock);
		Airdrop.setPluginInstance(null);

		assertThrows(IllegalStateException.class, () -> crate.land(reservedBlock));

		assertEquals(Material.AIR, reservedBlock.getType());
		assertNull(CrateManager.getCrate(reservedBlock.getLocation()));
		assertEquals(0, admission.snapshot().landedClaims());
	}

	@Test
	void failedRegistrationDoesNotReplaceExistingOwner() throws Exception {
		Crate existing = mock(Crate.class);
		CrateManager.addCrate(reservedBlock.getLocation(), existing);
		Crate crate = newCrate(reservedBlock);

		assertThrows(IllegalStateException.class, () -> crate.land(reservedBlock));

		assertSame(existing, CrateManager.getCrate(reservedBlock.getLocation()));
		assertEquals(Material.AIR, reservedBlock.getType());
		assertEquals(0, admission.snapshot().landedClaims());
		verify(existing, never()).destroy();
	}

	@Test
	void schedulerRejectionRollsBackBarrelAndLease() throws Exception {
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(scheduler.runTaskLater(org.mockito.ArgumentMatchers.eq(plugin),
				org.mockito.ArgumentMatchers.any(Runnable.class), org.mockito.ArgumentMatchers.anyLong()))
				.thenThrow(new IllegalStateException("scheduler rejected task"));
		Crate crate = newCrate(reservedBlock);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
			bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
			assertThrows(IllegalStateException.class, () -> crate.land(reservedBlock));
		}

		assertEquals(Material.AIR, reservedBlock.getType());
		assertNull(CrateManager.getCrate(reservedBlock.getLocation()));
		assertEquals(0, admission.snapshot().landedClaims());
	}

	@Test
	void destroy_cancelsPendingExpiryAndLandingEffectTasks() throws Exception {
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		BukkitTask expiryTask = mock(BukkitTask.class);
		BukkitTask landingTask = mock(BukkitTask.class);
		when(scheduler.runTaskLater(org.mockito.ArgumentMatchers.eq(plugin),
				org.mockito.ArgumentMatchers.any(Runnable.class), org.mockito.ArgumentMatchers.anyLong()))
				.thenReturn(expiryTask);
		when(scheduler.runTask(org.mockito.ArgumentMatchers.eq(plugin),
				org.mockito.ArgumentMatchers.any(Runnable.class))).thenReturn(landingTask);
		DropOptions options = DropOptions.createDefault()
				.withLandingEffects(true)
				.withContinuousEffects(false)
				.withSmokeEnabled(false)
				.withFlareEffects(false);
		Crate crate = newCrate(reservedBlock, options);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
			bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
			crate.land(reservedBlock);
			CrateManager.removeCrateAndDestroy(crate);
		}

		verify(expiryTask).cancel();
		verify(landingTask).cancel();
	}

	@Test
	void destroy_cancelsContinuousGlowAndSmokeTasks() throws Exception {
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		BukkitTask expiryTask = mock(BukkitTask.class);
		BukkitTask glowTask = mock(BukkitTask.class);
		BukkitTask smokeTask = mock(BukkitTask.class);
		when(scheduler.runTaskLater(org.mockito.ArgumentMatchers.eq(plugin),
				org.mockito.ArgumentMatchers.any(Runnable.class), org.mockito.ArgumentMatchers.anyLong()))
				.thenReturn(expiryTask);
		when(scheduler.runTaskTimer(org.mockito.ArgumentMatchers.eq(plugin),
				org.mockito.ArgumentMatchers.any(Runnable.class), org.mockito.ArgumentMatchers.anyLong(),
				org.mockito.ArgumentMatchers.anyLong()))
				.thenReturn(glowTask, smokeTask);
		DropOptions options = DropOptions.createDefault()
				.withLandingEffects(false)
				.withContinuousEffects(true)
				.withSmokeEnabled(true)
				.withFlareEffects(false);
		Crate crate = newCrate(reservedBlock, options);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class, CALLS_REAL_METHODS)) {
			bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
			crate.land(reservedBlock);
			CrateManager.removeCrateAndDestroy(crate);
		}

		verify(expiryTask).cancel();
		verify(glowTask).cancel();
		verify(smokeTask).cancel();
	}

	private Crate newCrate(Block landingBlock) throws Exception {
		DropOptions options = DropOptions.createDefault()
				.withLandingEffects(false)
				.withContinuousEffects(false)
				.withSmokeEnabled(false)
				.withFlareEffects(false);
		return newCrate(landingBlock, options);
	}

	private Crate newCrate(Block landingBlock, DropOptions options) throws Exception {
		DropAdmissionController.Lease lease = admission.acquireSystem(
				DropLocationKey.from(landingBlock.getLocation()),
				new DropLimitSettings(Duration.ofSeconds(30), 3, 10, Duration.ofSeconds(600)));
		lease.commitSpawn();
		return new Crate(new Location(world, 10.5, 100, 10.5), world, List.of(), options, lease);
	}

	private Crate newCrate(Block landingBlock, List<Crate.Outcome> outcomes) throws Exception {
		DropOptions options = DropOptions.createDefault()
				.withLandingEffects(false)
				.withContinuousEffects(false)
				.withSmokeEnabled(false)
				.withFlareEffects(false);
		DropAdmissionController.Lease lease = admission.acquireSystem(
				DropLocationKey.from(landingBlock.getLocation()),
				new DropLimitSettings(Duration.ofSeconds(30), 3, 10, Duration.ofSeconds(600)));
		lease.commitSpawn();
		return new Crate(new Location(world, 10.5, 100, 10.5), world,
				List.of(), options, lease, outcomes::add);
	}
}
