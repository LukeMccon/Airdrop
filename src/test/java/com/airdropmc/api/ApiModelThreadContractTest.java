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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockBukkitExtension.class)
class ApiModelThreadContractTest {

	private static final String THREAD_FAILURE_SUFFIX =
			" must be called on the primary server thread";

	@Test
	void airdropPackageBukkitBoundariesFailFastOffThread() {
		List<ItemStack> items = List.of(new ItemStack(Material.DIAMOND));
		AirdropPackage snapshot = new AirdropPackage("starter", BigDecimal.TEN, items);

		assertAll(
				() -> assertOffThreadFailure("AirdropPackage.<init>", () ->
						new AirdropPackage("starter", BigDecimal.TEN, items)),
				() -> assertOffThreadFailure("AirdropPackage.items", () -> snapshot.items()));
	}

	@Test
	void dropRequestDescriptorBukkitBoundariesFailFastOffThread() {
		World world = world(UUID.randomUUID());
		Location requested = new Location(world, 10.5, 80, -3.5);
		DropRequestDescriptor descriptor = new DropRequestDescriptor(
				UUID.randomUUID(), DropSource.SYSTEM, null, "starter", requested);

		assertAll(
				() -> assertOffThreadFailure("DropRequestDescriptor.<init>", () ->
						new DropRequestDescriptor(
								UUID.randomUUID(), DropSource.SYSTEM, null, "starter", requested)),
				() -> assertOffThreadFailure(
						"DropRequestDescriptor.requestedLocation", () -> descriptor.requestedLocation()));
	}

	@Test
	void resolvedDropContextBukkitBoundariesFailFastOffThread() {
		World world = world(UUID.randomUUID());
		DropRequestDescriptor descriptor = descriptor(world);
		AirdropPackage airdropPackage = airdropPackage();
		Location spawn = new Location(world, 10.5, 180, -3.5);
		Location landing = new Location(world, 10.5, 81, -3.5);
		ResolvedDropSettings settings = settings();
		ResolvedDropContext context = new ResolvedDropContext(
				descriptor, airdropPackage, spawn, landing, settings);

		assertAll(
				() -> assertOffThreadFailure("ResolvedDropContext.<init>", () ->
						new ResolvedDropContext(
								descriptor, airdropPackage, spawn, landing, settings)),
				() -> assertOffThreadFailure(
						"ResolvedDropContext.spawnLocation", () -> context.spawnLocation()),
				() -> assertOffThreadFailure(
						"ResolvedDropContext.landingLocation", () -> context.landingLocation()));
	}

	@Test
	void worldPositionBukkitConversionsFailFastOffThread() {
		UUID worldId = UUID.randomUUID();
		World world = world(worldId);
		Location location = new Location(world, 10.5, 80, -3.5, 30.0f, 5.0f);
		WorldPosition position = new WorldPosition(worldId, 10.5, 80, -3.5, 30.0f, 5.0f);

		assertAll(
				() -> assertOffThreadFailure(
						"WorldPosition.from", () -> WorldPosition.from(location)),
				() -> assertOffThreadFailure(
						"WorldPosition.toLocation", () -> position.toLocation(world)));
	}

	@Test
	void pureModelValuesRemainSafeOffThread() {
		UUID worldId = UUID.randomUUID();
		UUID requestId = UUID.randomUUID();
		World world = world(worldId);
		DropRequestDescriptor descriptor = new DropRequestDescriptor(
				requestId, DropSource.SYSTEM, null, "starter",
				new Location(world, 10.5, 80, -3.5, 30.0f, 5.0f));
		AirdropPackage airdropPackage = airdropPackage();
		ResolvedDropSettings settings = settings();
		ResolvedDropContext context = new ResolvedDropContext(
				descriptor,
				airdropPackage,
				new Location(world, 10.5, 180, -3.5),
				new Location(world, 10.5, 81, -3.5),
				settings);

		CompletableFuture.runAsync(() -> {
			assertEquals("starter", airdropPackage.name());
			assertEquals(BigDecimal.TEN, airdropPackage.price());
			assertEquals(requestId, descriptor.requestId());
			assertEquals(DropSource.SYSTEM, descriptor.source());
			assertTrue(descriptor.playerId().isEmpty());
			assertEquals("starter", descriptor.requestedPackageName());
			assertSame(descriptor, context.descriptor());
			assertSame(airdropPackage, context.airdropPackage());
			assertSame(settings, context.settings());
			assertEquals(descriptor.requestedPosition(), context.descriptor().requestedPosition());
			assertTrue(context.spawnPosition().isSameWorld(context.landingPosition()));

			WorldPosition pure = new WorldPosition(
					worldId, 10.5, 80, -3.5, 30.0f, 5.0f);
			assertEquals(worldId, pure.worldId());
			assertEquals(10.5, pure.x());
			assertEquals(80, pure.y());
			assertEquals(-3.5, pure.z());
			assertEquals(30.0f, pure.yaw());
			assertEquals(5.0f, pure.pitch());
			assertTrue(pure.isSameWorld(descriptor.requestedPosition()));
		}).join();
	}

	private static void assertOffThreadFailure(String operation, Runnable call) {
		CompletionException completion = assertThrows(CompletionException.class,
				() -> CompletableFuture.runAsync(call).join());
		IllegalStateException failure = assertInstanceOf(
				IllegalStateException.class, completion.getCause());
		assertEquals(operation + THREAD_FAILURE_SUFFIX, failure.getMessage());
	}

	private static DropRequestDescriptor descriptor(World world) {
		return new DropRequestDescriptor(
				UUID.randomUUID(), DropSource.SYSTEM, null, "starter",
				new Location(world, 10.5, 80, -3.5));
	}

	private static AirdropPackage airdropPackage() {
		return new AirdropPackage(
				"starter", BigDecimal.TEN, List.of(new ItemStack(Material.DIAMOND)));
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
