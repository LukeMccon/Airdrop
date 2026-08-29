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
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrateLocationIsolationTest {

	private ServerMock server;
	private WorldMock world;
	private DropAdmissionController admission;
	private Airdrop plugin;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("location_isolation_world");
		admission = new DropAdmissionController();
		plugin = mock(Airdrop.class);
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.getLogger()).thenReturn(Logger.getLogger("CrateLocationIsolationTest"));
		Airdrop.setPluginInstance(plugin);
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
	void fallingLocationInputsAndOutputsAreDetached() throws Exception {
		Location source = new Location(world, 10.5, 100, 10.5);
		Crate crate = newCrate(source, world);

		source.setY(1);
		Location returned = crate.getLocation();
		returned.setY(2);
		crate.getDropLocation().setY(3);

		assertEquals(100, crate.getLocation().getY());
		assertEquals(100, crate.getDropLocation().getY());
	}

	@Test
	void landedLocationOutputsAreDetached() throws Exception {
		Block landingBlock = world.getBlockAt(10, 64, 10);
		Crate crate = newCrate(new Location(world, 10.5, 100, 10.5), world);
		crate.land(landingBlock);

		Location returned = crate.getLandedLocation();
		returned.setX(99);
		Location current = crate.getLocation();
		current.setZ(99);

		assertEquals(10, crate.getLandedLocation().getBlockX());
		assertEquals(10, crate.getLocation().getBlockX());
		assertEquals(10, crate.getLandedLocation().getBlockZ());
		assertEquals(10, crate.getLocation().getBlockZ());
	}

	@Test
	void constructorRejectsLocationFromDifferentWorld() {
		WorldMock otherWorld = server.addSimpleWorld("other_location_world");
		Location otherWorldLocation = new Location(otherWorld, 10.5, 100, 10.5);

		assertThrows(IllegalArgumentException.class, () -> newCrate(otherWorldLocation, world));
	}

	@Test
	void constructorRejectsLocationWithoutWorld() {
		Location worldless = new Location(null, 10.5, 100, 10.5);

		assertThrows(IllegalArgumentException.class, () -> newCrate(worldless, world));
	}

	@Test
	void constructorRejectsNullWorldAndLocation() throws Exception {
		DropAdmissionController.Lease lease = newLease(world);

		assertThrows(NullPointerException.class, () -> new Crate(
				new Location(world, 10.5, 100, 10.5), null, List.of(), disabledEffects(), lease));
		assertThrows(NullPointerException.class, () -> new Crate(
				null, world, List.of(), disabledEffects(), lease));
	}

	@Test
	void constructorRejectsWorldWithoutUuid() throws Exception {
		World uuidlessWorld = mock(World.class);
		DropAdmissionController.Lease lease = newLease(world);

		assertThrows(IllegalArgumentException.class, () -> new Crate(
				new Location(uuidlessWorld, 10.5, 100, 10.5), uuidlessWorld,
				List.of(), disabledEffects(), lease));
	}

	@Test
	void constructorAcceptsDistinctWorldWrapperWithSameUuid() throws Exception {
		World sameWorldWrapper = mock(World.class);
		when(sameWorldWrapper.getUID()).thenReturn(world.getUID());

		newCrate(new Location(world, 10.5, 100, 10.5), sameWorldWrapper);
	}

	@Test
	void landRejectsBlockFromDifferentWorldBeforeAdoptingLocation() throws Exception {
		WorldMock otherWorld = server.addSimpleWorld("other_landing_world");
		Crate crate = newCrate(new Location(world, 10.5, 100, 10.5), world);

		assertThrows(IllegalArgumentException.class,
				() -> crate.land(otherWorld.getBlockAt(10, 64, 10)));
		assertNull(crate.getLandedLocation());
	}

	@Test
	void recoveryAcceptsDistinctWorldWrapperWithSameUuid() {
		World sameWorldWrapper = mock(World.class);
		when(sameWorldWrapper.getUID()).thenReturn(world.getUID());

		Crate recovered = recoverCrate(sameWorldWrapper, world.getBlockAt(10, 64, 10));

		assertNotNull(recovered);
		assertEquals(world.getUID(), recovered.getLandedLocation().getWorld().getUID());
	}

	@Test
	void recoveryRejectsBarrelLocationFromDifferentWorld() {
		WorldMock otherWorld = server.addSimpleWorld("other_recovery_world");

		assertThrows(IllegalArgumentException.class,
				() -> recoverCrate(otherWorld, world.getBlockAt(10, 64, 10)));
	}

	private Crate newCrate(Location dropLocation, World crateWorld) throws Exception {
		DropAdmissionController.Lease lease = newLease(crateWorld);
		lease.commitSpawn();
		return new Crate(dropLocation, crateWorld, List.of(), disabledEffects(), lease);
	}

	private DropAdmissionController.Lease newLease(World leaseWorld) throws Exception {
		return admission.acquireSystem(
				DropLocationKey.from(new Location(leaseWorld, 10, 64, 10)),
				new DropLimitSettings(Duration.ofSeconds(30), 3, 10, Duration.ofSeconds(600)));
	}

	private Crate recoverCrate(World recoveryWorld, Block barrelBlock) {
		barrelBlock.setType(Material.BARREL);
		Barrel barrel = (Barrel) barrelBlock.getState();
		DropAdmissionController.Lease lease = admission.restoreLanded(
				DropLocationKey.from(barrel.getLocation()));
		Crate.PersistedBarrelData persisted = new Crate.PersistedBarrelData(
				UUID.randomUUID().toString(),
				System.currentTimeMillis() + Duration.ofMinutes(10).toMillis(),
				Crate.RecoveryState.LIVE);
		return Crate.recoverPaidLanded(recoveryWorld, barrel, persisted, lease, plugin);
	}

	private static DropOptions disabledEffects() {
		return DropOptions.createDefault()
				.withLandingEffects(false)
				.withContinuousEffects(false)
				.withSmokeEnabled(false)
				.withFlareEffects(false);
	}
}
