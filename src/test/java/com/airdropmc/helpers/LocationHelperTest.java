package com.airdropmc.helpers;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocationHelperTest {

	@Test
	void copyInWorldReturnsDetachedLocationForMatchingWorldUuid() {
		UUID worldId = UUID.randomUUID();
		World locationWorld = worldWithId(worldId);
		World suppliedWorld = worldWithId(worldId);
		Location source = new Location(locationWorld, 10.5, 64, 20.5);

		Location copy = LocationHelper.copyInWorld(source, suppliedWorld, "location");
		source.setY(1);

		assertNotSame(source, copy);
		assertEquals(64, copy.getY());
	}

	@Test
	void copyInWorldRejectsLocationFromDifferentWorld() {
		World suppliedWorld = worldWithId(UUID.randomUUID());
		Location location = new Location(worldWithId(UUID.randomUUID()), 10, 64, 20);

		assertThrows(IllegalArgumentException.class,
				() -> LocationHelper.copyInWorld(location, suppliedWorld, "landing location"));
	}

	@Test
	void copyInWorldRejectsNullValuesAndWorldlessLocation() {
		World world = worldWithId(UUID.randomUUID());

		assertThrows(NullPointerException.class,
				() -> LocationHelper.copyInWorld(null, world, "location"));
		assertThrows(NullPointerException.class,
				() -> LocationHelper.copyInWorld(new Location(world, 0, 0, 0), null, "location"));
		assertThrows(IllegalArgumentException.class,
				() -> LocationHelper.copyInWorld(new Location(null, 0, 0, 0), world, "location"));
	}

	private static World worldWithId(UUID worldId) {
		World world = mock(World.class);
		when(world.getUID()).thenReturn(worldId);
		return world;
	}
}
