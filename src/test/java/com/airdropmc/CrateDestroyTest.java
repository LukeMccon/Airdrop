package com.airdropmc;

import com.airdropmc.config.DropOptions;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.FallingBlock;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrateDestroyTest {

	@Test
	void destroy_removesFallingCrateAndRestoresGravity_whenEntityStillAlive() throws Exception {
		World world = mock(World.class);
		FallingBlock fallingBlock = mock(FallingBlock.class);
		when(fallingBlock.isDead()).thenReturn(false);

		Crate crate = new Crate(new Location(world, 100, 100, 100), world, List.of(), DropOptions.createDefault());
		setFallingCrate(crate, fallingBlock);

		crate.destroy();

		verify(fallingBlock).setGravity(true);
		verify(fallingBlock).remove();
	}

	@Test
	void destroy_skipsEntityRemoval_whenFallingCrateAlreadyDead() throws Exception {
		World world = mock(World.class);
		FallingBlock fallingBlock = mock(FallingBlock.class);
		when(fallingBlock.isDead()).thenReturn(true);

		Crate crate = new Crate(new Location(world, 100, 100, 100), world, List.of(), DropOptions.createDefault());
		setFallingCrate(crate, fallingBlock);

		crate.destroy();

		verify(fallingBlock, never()).setGravity(true);
		verify(fallingBlock, never()).remove();
	}

	private void setFallingCrate(Crate crate, FallingBlock fallingBlock) throws Exception {
		Field fallingCrateField = Crate.class.getDeclaredField("fallingCrate");
		fallingCrateField.setAccessible(true);
		fallingCrateField.set(crate, fallingBlock);
	}
}
