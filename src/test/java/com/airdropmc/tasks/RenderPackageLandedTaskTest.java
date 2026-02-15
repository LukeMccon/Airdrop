package com.airdropmc.tasks;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class RenderPackageLandedTaskTest {

	@Test
	void constructor_doesNotMutateInputLocation() {
		World world = mock(World.class);
		Location original = new Location(world, 10, 64, 10);

		new RenderPackageLandedTask(original, world);

		assertEquals(10.0, original.getX());
		assertEquals(64.0, original.getY());
		assertEquals(10.0, original.getZ());
	}
}
