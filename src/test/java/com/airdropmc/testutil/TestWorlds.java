package com.airdropmc.testutil;

import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** Existing lifecycle tests use active terrain; cold terrain has controlled-future tests. */
public final class TestWorlds {
	private TestWorlds() {}

	public static WorldMock loadedWorld(ServerMock server, String name) {
		WorldMock world = new WorldMock() {
			@Override
			public boolean isChunkLoaded(int x, int z) { return true; }

			@Override
			public org.mockbukkit.mockbukkit.world.ChunkMock getChunkAt(int x, int z) {
				var chunk = org.mockito.Mockito.spy(super.getChunkAt(x, z));
				org.mockito.Mockito.doReturn(org.bukkit.Chunk.LoadLevel.ENTITY_TICKING).when(chunk).getLoadLevel();
				return chunk;
			}
		};
		world.setName(name);
		server.addWorld(world);
		return world;
	}
}
