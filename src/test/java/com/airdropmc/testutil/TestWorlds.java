package com.airdropmc.testutil;

import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.Set;

/** Existing lifecycle tests use active terrain; cold terrain has controlled-future tests. */
public final class TestWorlds {
	private record Ticket(int x, int z, Plugin plugin) {}
	private TestWorlds() {}

	public static WorldMock loadedWorld(ServerMock server, String name) {
		WorldMock world = new WorldMock() {
			private final Set<Ticket> tickets = new HashSet<>();

			@Override
			public boolean addPluginChunkTicket(int x, int z, Plugin plugin) {
				return tickets.add(new Ticket(x, z, plugin));
			}

			@Override
			public boolean removePluginChunkTicket(int x, int z, Plugin plugin) {
				return tickets.remove(new Ticket(x, z, plugin));
			}

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
