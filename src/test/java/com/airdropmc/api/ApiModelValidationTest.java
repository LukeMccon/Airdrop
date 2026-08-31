package com.airdropmc.api;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkitExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockBukkitExtension.class)
class ApiModelValidationTest {

	@Test
	void packageRejectsMissingIdentityInvalidPriceAndInvalidItems() {
		assertAll(
				() -> assertThrows(NullPointerException.class,
						() -> new AirdropPackage(null, BigDecimal.ZERO, List.of())),
				() -> assertThrows(IllegalArgumentException.class,
						() -> new AirdropPackage("  ", BigDecimal.ZERO, List.of())),
				() -> assertThrows(NullPointerException.class,
						() -> new AirdropPackage("starter", null, List.of())),
				() -> assertThrows(IllegalArgumentException.class,
						() -> new AirdropPackage("starter", new BigDecimal("-0.01"), List.of())),
				() -> assertThrows(NullPointerException.class,
						() -> new AirdropPackage("starter", BigDecimal.ZERO, null)),
				() -> assertThrows(NullPointerException.class,
						() -> new AirdropPackage(
								"starter", BigDecimal.ZERO, java.util.Arrays.asList((ItemStack) null))));
	}

	@Test
	void requestOptionsRejectValuesThatCannotBecomeSafeDropSettings() {
		DropRequestOptions options = DropRequestOptions.defaults();

		assertAll(
				() -> assertThrows(IllegalArgumentException.class, () -> options.withChickenCount(0)),
				() -> assertThrows(IllegalArgumentException.class, () -> options.withChickenCount(65)),
				() -> assertThrows(IllegalArgumentException.class, () -> options.withFallingSpeed(0.0)),
				() -> assertThrows(IllegalArgumentException.class,
						() -> options.withFallingSpeed(Double.NaN)),
				() -> assertThrows(IllegalArgumentException.class, () -> options.withDropHeight(0)),
				() -> assertThrows(IllegalArgumentException.class, () -> options.withSmokeHeight(-1)));
	}

	@Test
	void resolvedSettingsRejectInvalidPrimitiveOrDurationValues() {
		assertAll(
				() -> assertThrows(IllegalArgumentException.class,
						() -> settings(0, 0.3, Duration.ofSeconds(30), 3, 10, Duration.ofMinutes(10))),
				() -> assertThrows(IllegalArgumentException.class,
						() -> settings(3, Double.POSITIVE_INFINITY,
								Duration.ofSeconds(30), 3, 10, Duration.ofMinutes(10))),
				() -> assertThrows(IllegalArgumentException.class,
						() -> settings(3, 0.3, Duration.ZERO, 3, 10, Duration.ofMinutes(10))),
				() -> assertThrows(IllegalArgumentException.class,
						() -> settings(3, 0.3, Duration.ofSeconds(30), 0, 10, Duration.ofMinutes(10))),
				() -> assertThrows(IllegalArgumentException.class,
						() -> settings(3, 0.3, Duration.ofSeconds(30), 3, 10, Duration.ofSeconds(-1))));
	}

	@Test
	void descriptorEnforcesSourceAndPlayerCorrelation() {
		World world = world(UUID.randomUUID());
		Location location = new Location(world, 0, 80, 0);

		assertAll(
				() -> assertThrows(NullPointerException.class,
						() -> new DropRequestDescriptor(null, DropSource.SYSTEM, null, "starter", location)),
				() -> assertThrows(IllegalArgumentException.class,
						() -> new DropRequestDescriptor(
								UUID.randomUUID(), DropSource.SYSTEM, null, " ", location)),
				() -> assertThrows(IllegalArgumentException.class,
						() -> new DropRequestDescriptor(
								UUID.randomUUID(), DropSource.PLAYER, null, "starter", location)),
				() -> assertThrows(IllegalArgumentException.class,
						() -> new DropRequestDescriptor(
								UUID.randomUUID(), DropSource.SYSTEM, UUID.randomUUID(), "starter", location)));
	}

	@Test
	void resolvedContextRejectsLocationsFromDifferentWorlds() {
		World requestedWorld = world(UUID.randomUUID());
		World otherWorld = world(UUID.randomUUID());
		DropRequestDescriptor descriptor = descriptor(requestedWorld);

		assertThrows(IllegalArgumentException.class, () -> new ResolvedDropContext(
				descriptor,
				airdropPackage(),
				new Location(requestedWorld, 0, 180, 0),
				new Location(otherWorld, 0, 81, 0),
				settings()));
	}

	@Test
	void worldPositionRejectsNonFiniteCoordinatesAndWrongWorldConversion() {
		UUID worldId = UUID.randomUUID();
		World otherWorld = world(UUID.randomUUID());

		assertThrows(IllegalArgumentException.class,
				() -> new WorldPosition(worldId, Double.NaN, 2, 3, 0, 0));
		assertThrows(IllegalArgumentException.class,
				() -> new WorldPosition(worldId, 1, 2, 3, 0, 0).toLocation(otherWorld));
	}

	@Test
	void viewTypesFixTheirOwnPhaseAndRejectContradictoryWorldOrExpiry() {
		World world = world(UUID.randomUUID());
		World otherWorld = world(UUID.randomUUID());
		ResolvedDropContext context = context(world);
		WorldPosition fallingPosition = WorldPosition.from(new Location(world, 0, 120, 0));
		WorldPosition landedPosition = WorldPosition.from(new Location(world, 0, 81, 0));
		FallingAirdropView falling = new FallingAirdropView(
				UUID.randomUUID(), UUID.randomUUID(), fallingPosition, context);
		LandedAirdropView landed = new LandedAirdropView(
				UUID.randomUUID(), landedPosition, context, System.currentTimeMillis() + 60_000L, false);

		assertEquals(DropState.FALLING, falling.state());
		assertEquals(DropState.LANDED, landed.state());
		assertTrue(falling.requestId().isPresent());
		assertEquals("starter", falling.packageName().orElseThrow());
		assertFalse(falling.recovered());
		assertThrows(IllegalArgumentException.class, () -> new FallingAirdropView(
				UUID.randomUUID(), UUID.randomUUID(),
				WorldPosition.from(new Location(otherWorld, 0, 120, 0)), context));
		assertThrows(IllegalArgumentException.class, () -> new LandedAirdropView(
				UUID.randomUUID(), landedPosition, context, 0L, false));
	}

	private static DropRequestDescriptor descriptor(World world) {
		return new DropRequestDescriptor(
				UUID.randomUUID(), DropSource.SYSTEM, null, "starter",
				new Location(world, 0, 80, 0));
	}

	private static ResolvedDropContext context(World world) {
		return new ResolvedDropContext(
				descriptor(world),
				airdropPackage(),
				new Location(world, 0, 180, 0),
				new Location(world, 0, 81, 0),
				settings());
	}

	private static AirdropPackage airdropPackage() {
		return new AirdropPackage(
				"starter", BigDecimal.ZERO, List.of(new ItemStack(Material.DIAMOND)));
	}

	private static ResolvedDropSettings settings() {
		return settings(3, 0.3, Duration.ofSeconds(30), 3, 10, Duration.ofMinutes(10));
	}

	private static ResolvedDropSettings settings(
			int chickenCount,
			double fallingSpeed,
			Duration requestCooldown,
			int maxFalling,
			int maxLanded,
			Duration landedLifetime) {
		return new ResolvedDropSettings(
				chickenCount, fallingSpeed, 100, true, true, true, true, 20,
				requestCooldown, maxFalling, maxLanded, landedLifetime);
	}

	private static World world(UUID id) {
		World world = mock(World.class);
		when(world.getUID()).thenReturn(id);
		return world;
	}
}
