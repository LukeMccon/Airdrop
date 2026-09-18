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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockBukkitExtension.class)
class ApiModelImmutabilityTest {

	@Test
	void airdropPackageClonesConstructorItemsAndEveryReturnedItem() {
		ItemStack sourceItem = new ItemStack(Material.DIAMOND, 2);
		List<ItemStack> sourceItems = new ArrayList<>(List.of(sourceItem));
		AirdropPackage snapshot = new AirdropPackage(
				"starter", new BigDecimal("12.50"), sourceItems);

		sourceItem.setType(Material.DIRT);
		sourceItems.clear();
		List<ItemStack> firstRead = snapshot.items();
		firstRead.getFirst().setAmount(9);

		assertEquals(1, snapshot.items().size());
		assertEquals(Material.DIAMOND, snapshot.items().getFirst().getType());
		assertEquals(2, snapshot.items().getFirst().getAmount());
		assertThrows(UnsupportedOperationException.class,
				() -> firstRead.add(new ItemStack(Material.STONE)));
	}

	@Test
	void descriptorClonesInputAndReturnedBukkitLocations() {
		World world = world(UUID.randomUUID());
		Location requested = new Location(world, 10.5, 80, -3.5, 30.0f, 5.0f);
		DropRequestDescriptor descriptor = new DropRequestDescriptor(
				UUID.randomUUID(), DropSource.SYSTEM, null, "starter", requested);

		requested.setX(1000);
		Location returned = descriptor.requestedLocation();
		returned.setZ(1000);

		assertEquals(10.5, descriptor.requestedLocation().getX());
		assertEquals(-3.5, descriptor.requestedLocation().getZ());
		assertEquals(10.5, descriptor.requestedPosition().x());
		assertEquals(-3.5, descriptor.requestedPosition().z());
	}

	@Test
	void resolvedContextClonesSpawnAndLandingLocations() {
		World world = world(UUID.randomUUID());
		DropRequestDescriptor descriptor = descriptor(world);
		Location spawn = new Location(world, 10.5, 180, -3.5);
		Location landing = new Location(world, 10.5, 81, -3.5);
		ResolvedDropContext context = new ResolvedDropContext(
				descriptor, airdropPackage(), spawn, landing, settings());

		spawn.setY(0);
		landing.setX(0);
		context.spawnLocation().setZ(0);
		context.landingLocation().setY(0);

		assertEquals(180, context.spawnLocation().getY());
		assertEquals(-3.5, context.spawnLocation().getZ());
		assertEquals(10.5, context.landingLocation().getX());
		assertEquals(81, context.landingLocation().getY());
		assertEquals(context.spawnPosition(), WorldPosition.from(context.spawnLocation()));
		assertEquals(context.landingPosition(), WorldPosition.from(context.landingLocation()));
	}

	@Test
	void requestOptionFluentMethodsReturnIndependentValues() {
		DropRequestOptions defaults = DropRequestOptions.defaults();
		DropRequestOptions customized = defaults
				.withChickenCount(3)
				.withFallingSpeed(0.4)
				.withLandingEffects(false);

		assertNotSame(defaults, customized);
		assertTrue(defaults.chickenCount().isEmpty());
		assertTrue(defaults.fallingSpeed().isEmpty());
		assertTrue(defaults.landingEffects().isEmpty());
		assertEquals(3, customized.chickenCount().orElseThrow());
		assertEquals(0.4, customized.fallingSpeed().orElseThrow());
		assertFalse(customized.landingEffects().orElseThrow());
	}

	private static DropRequestDescriptor descriptor(World world) {
		return new DropRequestDescriptor(
				UUID.randomUUID(), DropSource.SYSTEM, null, "starter",
				new Location(world, 10.5, 80, -3.5));
	}

	private static AirdropPackage airdropPackage() {
		return new AirdropPackage(
				"starter", new BigDecimal("12.50"), List.of(new ItemStack(Material.DIAMOND)));
	}

	private static ResolvedDropSettings settings() {
		return new ResolvedDropSettings(
				3, 0.3, 100, true, true, true, true, 20,
				Duration.ofSeconds(30), 3, 10, Duration.ofMinutes(10));
	}

	private static World world(UUID id) {
		World world = mock(World.class);
		when(world.getUID()).thenReturn(id);
		return world;
	}
}
