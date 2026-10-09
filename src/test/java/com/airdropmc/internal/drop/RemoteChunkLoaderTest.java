package com.airdropmc.internal.drop;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoteChunkLoaderTest {
	private ServerMock server;
	private Plugin plugin;
	private World world;
	private Chunk chunk;
	private RemoteChunkLoader loader;
	private AtomicBoolean ticketHeld;
	private final IllegalStateException failure = new IllegalStateException("ticket removal failed");

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		plugin = MockBukkit.createMockPlugin();
		world = mock(World.class);
		chunk = mock(Chunk.class);
		when(chunk.getWorld()).thenReturn(world);
		when(chunk.getX()).thenReturn(12);
		when(chunk.getZ()).thenReturn(34);
		when(world.getUID()).thenReturn(UUID.randomUUID());
		when(world.isChunkLoaded(12, 34)).thenReturn(true);

		ticketHeld = new AtomicBoolean();
		when(world.addPluginChunkTicket(12, 34, plugin))
				.thenAnswer(ignored -> !ticketHeld.getAndSet(true));
		when(world.removePluginChunkTicket(12, 34, plugin))
				.thenThrow(failure)
				.thenAnswer(ignored -> ticketHeld.getAndSet(false));
		loader = new RemoteChunkLoader(plugin, System::nanoTime);
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void failedTicketRemovalCanBeRetriedWithoutLeakingTheTicket() {
		Runnable release = loader.retain(chunk);
		assertTrue(ticketHeld.get());
		assertDoesNotThrow(release::run, "Deferred cleanup must not interrupt request completion");
		assertTrue(ticketHeld.get(), "The failed removal must leave the ticket held");

		release.run();
		assertFalse(ticketHeld.get(), "Retrying a failed release must remove the orphaned ticket");
		verify(world, times(2)).removePluginChunkTicket(12, 34, plugin);
		release.run();
		verify(world, times(2)).removePluginChunkTicket(12, 34, plugin);

		Runnable nextRelease = loader.retain(chunk);
		assertTrue(ticketHeld.get(), "A later crate must acquire a fresh ticket");
		verify(world, times(2)).addPluginChunkTicket(12, 34, plugin);
		nextRelease.run();
		assertFalse(ticketHeld.get(), "The later crate must not inherit a stale reference count");
		server.getScheduler().performTicks(20);
		verify(world, times(3)).removePluginChunkTicket(12, 34, plugin);
		assertTrue(server.getScheduler().getPendingTasks().isEmpty(), "Successful manual retry must cancel the timer");
	}

	@Test
	void delayedReleaseKeepsTheTicketForANewCrateSharingTheChunk() {
		Runnable release = loader.retain(chunk);
		assertDoesNotThrow(release::run);
		Runnable nextRelease = loader.retain(chunk);

		server.getScheduler().performTicks(20);
		assertTrue(ticketHeld.get(), "Retry must release only its own claim");
		verify(world).removePluginChunkTicket(12, 34, plugin);
		nextRelease.run();
		assertFalse(ticketHeld.get());
		verify(world, times(2)).removePluginChunkTicket(12, 34, plugin);
	}

	@Test
	void repeatedFailuresKeepOnlyOneScheduledRetry() {
		org.mockito.Mockito.doThrow(failure, failure, failure)
				.doAnswer(ignored -> ticketHeld.getAndSet(false))
				.when(world).removePluginChunkTicket(12, 34, plugin);
		Runnable release = loader.retain(chunk);
		assertDoesNotThrow(release::run);
		assertDoesNotThrow(release::run);
		verify(world, times(2)).removePluginChunkTicket(12, 34, plugin);
		assertEquals(1, server.getScheduler().getPendingTasks().size());

		server.getScheduler().performTicks(20);
		assertTrue(ticketHeld.get());
		verify(world, times(3)).removePluginChunkTicket(12, 34, plugin);
		server.getScheduler().performTicks(20);
		assertFalse(ticketHeld.get());
		verify(world, times(4)).removePluginChunkTicket(12, 34, plugin);
		server.getScheduler().performTicks(40);
		verify(world, times(4)).removePluginChunkTicket(12, 34, plugin);
		assertTrue(server.getScheduler().getPendingTasks().isEmpty(), "Successful cleanup must not leak a retry task");
	}
}
