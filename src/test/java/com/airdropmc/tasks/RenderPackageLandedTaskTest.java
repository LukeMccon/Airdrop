package com.airdropmc.tasks;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RenderPackageLandedTaskTest {

	private ServerMock server;
	private Plugin plugin;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		plugin = MockBukkit.createMockPlugin("LandingAnimationHarness");
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void constructor_doesNotMutateInputLocation() {
		World world = mock(World.class);
		Location original = new Location(world, 10, 64, 10);

		new RenderPackageLandedTask(original, world);

		assertEquals(10.0, original.getX());
		assertEquals(64.0, original.getY());
		assertEquals(10.0, original.getZ());
	}

	@Test
	void scheduledTask_rendersAllTwentyFramesAndStops() {
		World world = mock(World.class);
		RenderPackageLandedTask animation = new RenderPackageLandedTask(
				new Location(world, 10, 64, 10), world);

		BukkitTask scheduled = animation.runTaskTimer(plugin, 0L, 1L);
		server.getScheduler().performOneTick();

		verifyFrames(world, 1);

		server.getScheduler().performTicks(19L);

		ArgumentCaptor<Location> glowLocations = ArgumentCaptor.forClass(Location.class);
		verify(world, times(20)).spawnParticle(
				eq(Particle.GLOW), glowLocations.capture(), eq(15),
				eq(0.3), eq(0.1), eq(0.3), eq(0.05));
		verify(world, times(400)).spawnParticle(
				eq(Particle.END_ROD), any(Location.class), eq(1),
				eq(0.0), eq(0.0), eq(0.0), eq(0.0));
		List<Location> frames = glowLocations.getAllValues();
		assertEquals(64.5, frames.getFirst().getY(), 0.0001);
		assertEquals(66.4, frames.getLast().getY(), 0.0001);
		assertTrue(scheduled.isCancelled());

		server.getScheduler().performTicks(5L);
		verifyFrames(world, 20);
	}

	private static void verifyFrames(World world, int frameCount) {
		verify(world, times(frameCount)).spawnParticle(
				eq(Particle.GLOW), any(Location.class), eq(15),
				eq(0.3), eq(0.1), eq(0.3), eq(0.05));
		verify(world, times(frameCount * 20)).spawnParticle(
				eq(Particle.END_ROD), any(Location.class), eq(1),
				eq(0.0), eq(0.0), eq(0.0), eq(0.0));
	}
}
