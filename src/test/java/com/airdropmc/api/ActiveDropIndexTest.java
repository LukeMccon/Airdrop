package com.airdropmc.api;

import com.airdropmc.internal.drop.ActiveDropRegistry;
import com.airdropmc.limits.DropLocationKey;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.ServerMock;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockBukkitExtension.class)
class ActiveDropIndexTest {

	@Test
	void fallingRegistrationPublishesAllPureIndexes() {
		ServerMock server = (ServerMock) Bukkit.getServer();
		Fixture fixture = fixture(server, "starter", 10);
		ActiveDropRegistry registry = new ActiveDropRegistry();

		registry.registerFalling(fixture.falling());

		assertEquals(List.of(fixture.falling()), registry.activeDrops());
		assertSame(fixture.falling(), registry.findByRequestId(fixture.requestId()).orElseThrow());
		assertSame(fixture.falling(), registry.findByCrateId(fixture.crateId()).orElseThrow());
		assertSame(fixture.falling(), registry.findByFallingEntityId(fixture.entityId()).orElseThrow());
		assertEquals(1, registry.fallingCount());
		assertEquals(0, registry.landedCount());
	}

	@Test
	void duplicateRequestIsRejectedWithoutPublishingAnyPartialIndex() {
		ServerMock server = (ServerMock) Bukkit.getServer();
		Fixture first = fixture(server, "first", 10);
		Fixture duplicate = fixture(server, "second", 30, first.requestId());
		ActiveDropRegistry registry = new ActiveDropRegistry();
		registry.registerFalling(first.falling());

		assertThrows(IllegalStateException.class,
				() -> registry.registerFalling(duplicate.falling()));

		assertEquals(List.of(first.falling()), registry.activeDrops());
		assertTrue(registry.findByCrateId(duplicate.crateId()).isEmpty());
		assertTrue(registry.findByFallingEntityId(duplicate.entityId()).isEmpty());
	}

	@Test
	void fallingToLandedTransitionReplacesEveryIndexInOneSnapshot() {
		ServerMock server = (ServerMock) Bukkit.getServer();
		Fixture fixture = fixture(server, "starter", 10);
		ActiveDropRegistry registry = new ActiveDropRegistry();
		registry.registerFalling(fixture.falling());
		LandedAirdropView landed = fixture.landed(false);
		DropLocationKey locationKey = new DropLocationKey(
				landed.position().worldId(), 12, 64, 12);

		registry.transitionToLanded(fixture.crateId(), fixture.entityId(), locationKey, landed);

		assertEquals(List.of(landed), registry.activeDrops());
		assertSame(landed, registry.findByRequestId(fixture.requestId()).orElseThrow());
		assertSame(landed, registry.findByCrateId(fixture.crateId()).orElseThrow());
		assertTrue(registry.findByFallingEntityId(fixture.entityId()).isEmpty());
		assertSame(landed, registry.findByLandedLocation(locationKey).orElseThrow());
		assertEquals(0, registry.fallingCount());
		assertEquals(1, registry.landedCount());
	}

	@Test
	void openedReplacementKeepsIdentityAndRemovalIsIdempotent() {
		ServerMock server = (ServerMock) Bukkit.getServer();
		Fixture fixture = fixture(server, "starter", 10);
		ActiveDropRegistry registry = new ActiveDropRegistry();
		LandedAirdropView closed = fixture.landed(false);
		LandedAirdropView opened = fixture.landed(true);
		DropLocationKey key = new DropLocationKey(closed.position().worldId(), 12, 64, 12);
		registry.registerRecovered(key, closed);

		registry.replaceLanded(key, opened);

		assertSame(opened, registry.findByCrateId(fixture.crateId()).orElseThrow());
		assertSame(opened, registry.findByLandedLocation(key).orElseThrow());
		assertTrue(registry.remove(fixture.crateId()).isPresent());
		assertFalse(registry.remove(fixture.crateId()).isPresent());
		assertTrue(registry.activeDrops().isEmpty());
	}

	private static Fixture fixture(ServerMock server, String packageName, int x) {
		return fixture(server, packageName, x, UUID.randomUUID());
	}

	private static Fixture fixture(
			ServerMock server, String packageName, int x, UUID requestId) {
		org.bukkit.World world = server.getWorlds().isEmpty()
				? server.addSimpleWorld("active-index")
				: server.getWorlds().getFirst();
		UUID crateId = UUID.randomUUID();
		UUID entityId = UUID.randomUUID();
		Location requested = new Location(world, x, 80, x);
		Location spawn = new Location(world, x, 100, x);
		Location landing = new Location(world, 12, 64, 12);
		ResolvedDropContext context = new ResolvedDropContext(
				new DropRequestDescriptor(
						requestId, DropSource.SYSTEM, null, packageName, requested),
				new AirdropPackage(packageName, BigDecimal.ZERO,
						List.of(new ItemStack(Material.BREAD))),
				spawn,
				landing,
				settings());
		FallingAirdropView falling = new FallingAirdropView(
				crateId, entityId, WorldPosition.from(spawn), context);
		return new Fixture(crateId, requestId, entityId, context, falling);
	}

	private static ResolvedDropSettings settings() {
		return new ResolvedDropSettings(
				4, 0.2, 20, true, true, true, true, 6,
				Duration.ofSeconds(30), 3, 10, Duration.ofMinutes(10));
	}

	private record Fixture(
			UUID crateId,
			UUID requestId,
			UUID entityId,
			ResolvedDropContext context,
			FallingAirdropView falling) {

		LandedAirdropView landed(boolean opened) {
			return new LandedAirdropView(
					crateId,
					context.landingPosition(),
					context,
					System.currentTimeMillis() + 60_000L,
					opened);
		}
	}
}
