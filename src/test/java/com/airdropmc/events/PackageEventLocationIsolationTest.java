package com.airdropmc.events;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import com.airdropmc.Crate;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackageEventLocationIsolationTest {

	private ServerMock server;
	private WorldMock world;
	private Plugin plugin;
	private Crate crate;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("event_location_world");
		plugin = MockBukkit.createMockPlugin("EventLocationHarness");
		crate = mock(Crate.class);
	}

	@AfterEach
	void tearDown() {
		HandlerList.unregisterAll(plugin);
		MockBukkit.unmock();
	}

	@Test
	void dropEventCopiesConstructorInputAndGetterOutput() {
		Location source = new Location(world, 10.5, 100, 20.5);
		PackageDropEvent event = new PackageDropEvent(crate, world, source);

		source.setY(1);
		event.getDropLocation().setY(2);

		assertEquals(100, event.getDropLocation().getY());
	}

	@Test
	void landEventCopiesConstructorInputAndGetterOutput() {
		Block landedBlock = world.getBlockAt(10, 64, 20);
		Location source = landedBlock.getLocation();
		PackageLandEvent event = new PackageLandEvent(crate, world, source, landedBlock);

		source.setY(1);
		event.getLandingLocation().setY(2);

		assertEquals(64, event.getLandingLocation().getY());
	}

	@Test
	void dropListenerCannotChangeLocationSeenByLaterListener() {
		DropLocationMutator mutator = new DropLocationMutator();
		DropLocationObserver observer = new DropLocationObserver();
		server.getPluginManager().registerEvents(mutator, plugin);
		server.getPluginManager().registerEvents(observer, plugin);
		PackageDropEvent event = new PackageDropEvent(
				crate, world, new Location(world, 10.5, 100, 20.5));

		server.getPluginManager().callEvent(event);

		assertEquals(100, observer.observed.getY());
	}

	@Test
	void landListenerCannotChangeLocationSeenByLaterListener() {
		LandLocationMutator mutator = new LandLocationMutator();
		LandLocationObserver observer = new LandLocationObserver();
		server.getPluginManager().registerEvents(mutator, plugin);
		server.getPluginManager().registerEvents(observer, plugin);
		Block landedBlock = world.getBlockAt(10, 64, 20);
		PackageLandEvent event = new PackageLandEvent(
				crate, world, landedBlock.getLocation(), landedBlock);

		server.getPluginManager().callEvent(event);

		assertEquals(64, observer.observed.getY());
	}

	@Test
	void eventsRejectLocationsFromDifferentWorld() {
		WorldMock otherWorld = server.addSimpleWorld("other_event_world");
		Location otherLocation = new Location(otherWorld, 10, 64, 20);
		Block landedBlock = otherWorld.getBlockAt(10, 64, 20);

		assertThrows(IllegalArgumentException.class,
				() -> new PackageDropEvent(crate, world, otherLocation));
		assertThrows(IllegalArgumentException.class,
				() -> new PackageLandEvent(crate, world, otherLocation, landedBlock));
	}

	@Test
	void eventsRejectLocationsWithoutWorld() {
		Location worldless = new Location(null, 10, 64, 20);

		assertThrows(IllegalArgumentException.class,
				() -> new PackageDropEvent(crate, world, worldless));
		assertThrows(IllegalArgumentException.class,
				() -> new PackageLandEvent(crate, world, worldless, mock(Block.class)));
	}

	@Test
	void eventsRejectNullWorldAndLocation() {
		Location location = new Location(world, 10, 64, 20);
		Block landedBlock = world.getBlockAt(10, 64, 20);

		assertThrows(NullPointerException.class,
				() -> new PackageDropEvent(crate, null, location));
		assertThrows(NullPointerException.class,
				() -> new PackageDropEvent(crate, world, null));
		assertThrows(NullPointerException.class,
				() -> new PackageLandEvent(crate, null, location, landedBlock));
		assertThrows(NullPointerException.class,
				() -> new PackageLandEvent(crate, world, null, landedBlock));
	}

	@Test
	void eventsRejectWorldWithoutUuid() {
		World uuidlessWorld = mock(World.class);
		Location location = new Location(uuidlessWorld, 10, 64, 20);

		assertThrows(IllegalArgumentException.class,
				() -> new PackageDropEvent(crate, uuidlessWorld, location));
		assertThrows(IllegalArgumentException.class,
				() -> new PackageLandEvent(crate, uuidlessWorld, location, mock(Block.class)));
	}

	@Test
	void eventsAcceptDistinctWorldWrapperWithSameUuid() {
		World sameWorldWrapper = mock(World.class);
		when(sameWorldWrapper.getUID()).thenReturn(world.getUID());
		Location location = new Location(world, 10, 64, 20);

		new PackageDropEvent(crate, sameWorldWrapper, location);
		new PackageLandEvent(crate, sameWorldWrapper, location, world.getBlockAt(10, 64, 20));
	}

	private static class DropLocationMutator implements Listener {
		@EventHandler(priority = EventPriority.LOWEST)
		public void mutate(PackageDropEvent event) {
			event.getDropLocation().setY(1);
		}
	}

	private static class DropLocationObserver implements Listener {
		private Location observed;

		@EventHandler(priority = EventPriority.HIGHEST)
		public void observe(PackageDropEvent event) {
			observed = event.getDropLocation();
		}
	}

	private static class LandLocationMutator implements Listener {
		@EventHandler(priority = EventPriority.LOWEST)
		public void mutate(PackageLandEvent event) {
			event.getLandingLocation().setY(1);
		}
	}

	private static class LandLocationObserver implements Listener {
		private Location observed;

		@EventHandler(priority = EventPriority.HIGHEST)
		public void observe(PackageLandEvent event) {
			observed = event.getLandingLocation();
		}
	}
}
