package com.airdropmc;

import com.airdropmc.config.DropOptions;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Slime;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParachuteSystemTest {

	@Test
	void initialize_spawnsParachutesWithStaggeredHeights() {
		World world = mock(World.class);
		Slime slime = mock(Slime.class);
		Chicken chicken = mock(Chicken.class);
		FallingBlock fallingCrate = mock(FallingBlock.class);
		Server server = mock(Server.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		BukkitTask task = mock(BukkitTask.class);
		Airdrop plugin = mock(Airdrop.class);

		when(world.spawnEntity(any(Location.class), eq(EntityType.SLIME))).thenReturn(slime);
		when(world.spawnEntity(any(Location.class), eq(EntityType.CHICKEN))).thenReturn(chicken);
		when(server.getScheduler()).thenReturn(scheduler);
		when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);
		when(plugin.isEnabled()).thenReturn(true);

		DropOptions options = DropOptions.createDefault().withChickenCount(3);
		ParachuteSystem parachuteSystem = new ParachuteSystem(world, options);
		Location dropLocation = new Location(world, 100, 200, 300);

		try (MockedStatic<Bukkit> bukkitMock = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkitMock.when(Bukkit::getServer).thenReturn(server);
			parachuteSystem.initialize(dropLocation, fallingCrate, plugin);
		}

		// Verify slime spawns first at y+1
		ArgumentCaptor<Location> slimeLocationCaptor = ArgumentCaptor.forClass(Location.class);
		verify(world).spawnEntity(slimeLocationCaptor.capture(), eq(EntityType.SLIME));
		Location slimeLocation = slimeLocationCaptor.getValue();
		assertEquals(201.0, slimeLocation.getY());

		// Verify chickens spawn (location mutates each iteration creating staggered heights)
		ArgumentCaptor<Location> chickenLocationCaptor = ArgumentCaptor.forClass(Location.class);
		verify(world, times(3)).spawnEntity(chickenLocationCaptor.capture(), eq(EntityType.CHICKEN));
		List<Location> chickenLocations = chickenLocationCaptor.getAllValues();

		// Each chicken should be at increasing Y values due to location mutation
		double previousY = 201.0;
		for (Location chickenLocation : chickenLocations) {
			assertTrue(chickenLocation.getY() > previousY,
					"Chicken Y should be higher than previous: " + chickenLocation.getY() + " > " + previousY);
			previousY = chickenLocation.getY();
		}
	}

	@Test
	void fallingCrateDeath_releasesParachutesAndCancelsRepeatingTask() {
		World world = mock(World.class);
		Slime slime = mock(Slime.class);
		Chicken chicken = mock(Chicken.class);
		FallingBlock fallingCrate = mock(FallingBlock.class);
		Server server = mock(Server.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		BukkitTask task = mock(BukkitTask.class);
		Airdrop plugin = mock(Airdrop.class);
		Runnable[] tickRunnable = new Runnable[1];

		when(world.spawnEntity(any(Location.class), eq(EntityType.SLIME))).thenReturn(slime);
		when(world.spawnEntity(any(Location.class), eq(EntityType.CHICKEN))).thenReturn(chicken);
		when(server.getScheduler()).thenReturn(scheduler);
		when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenAnswer(invocation -> {
			tickRunnable[0] = invocation.getArgument(1);
			return task;
		});
		when(scheduler.runTaskLater(any(), any(Runnable.class), anyLong())).thenReturn(task);
		when(plugin.isEnabled()).thenReturn(true);
		when(fallingCrate.isDead()).thenReturn(true);
		when(task.isCancelled()).thenReturn(false);

		DropOptions options = DropOptions.createDefault().withChickenCount(1);
		ParachuteSystem parachuteSystem = new ParachuteSystem(world, options);
		Location dropLocation = new Location(world, 50, 100, 50);

		try (MockedStatic<Bukkit> bukkitMock = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkitMock.when(Bukkit::getServer).thenReturn(server);
			parachuteSystem.initialize(dropLocation, fallingCrate, plugin);
			tickRunnable[0].run();
		}

		verify(task).cancel();
		verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(60L));
	}

	@Test
	void cancel_cancelsRepeatingTask() {
		World world = mock(World.class);
		Slime slime = mock(Slime.class);
		Chicken chicken = mock(Chicken.class);
		FallingBlock fallingCrate = mock(FallingBlock.class);
		Server server = mock(Server.class);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		BukkitTask task = mock(BukkitTask.class);
		Airdrop plugin = mock(Airdrop.class);

		when(world.spawnEntity(any(Location.class), eq(EntityType.SLIME))).thenReturn(slime);
		when(world.spawnEntity(any(Location.class), eq(EntityType.CHICKEN))).thenReturn(chicken);
		when(server.getScheduler()).thenReturn(scheduler);
		when(scheduler.runTaskTimer(any(), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);
		when(plugin.isEnabled()).thenReturn(true);
		when(task.isCancelled()).thenReturn(false);

		DropOptions options = DropOptions.createDefault().withChickenCount(1);
		ParachuteSystem parachuteSystem = new ParachuteSystem(world, options);
		Location dropLocation = new Location(world, 10, 100, 10);

		try (MockedStatic<Bukkit> bukkitMock = org.mockito.Mockito.mockStatic(Bukkit.class)) {
			bukkitMock.when(Bukkit::getServer).thenReturn(server);
			parachuteSystem.initialize(dropLocation, fallingCrate, plugin);
			parachuteSystem.cancel();
		}

		verify(task).cancel();
	}
}
