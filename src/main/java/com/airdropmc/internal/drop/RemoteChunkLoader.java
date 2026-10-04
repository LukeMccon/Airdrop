package com.airdropmc.internal.drop;

import com.airdropmc.api.DropRejection;
import com.airdropmc.api.DropRejectionReason;
import com.airdropmc.config.ConfigKeys;
import org.bukkit.Chunk;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

/** Main-thread loading budget and shared plugin-ticket ownership; no request queue. */
final class RemoteChunkLoader {

	private record ChunkKey(UUID world, int x, int z) {}
	private record Attempt(long started, long duration) {}
	private final Plugin plugin;
	private final LongSupplier clock;
	private final Map<UUID, Attempt> attempts = new HashMap<>();
	private final Map<ChunkKey, Integer> tickets = new HashMap<>();
	private int outstanding;

	RemoteChunkLoader(Plugin plugin, LongSupplier clock) {
		this.plugin = plugin;
		this.clock = clock;
	}

	Load load(Location target, UUID caller, BiConsumer<Load, Chunk> ready, Consumer<DropRejection> rejected) {
		boolean generate = ConfigKeys.canGenerateRemoteChunks();
		if (generate && !generationFitsBorder(target.getWorld(), target.getBlockX() >> 4, target.getBlockZ() >> 4)) {
			rejected.accept(DropRejection.of(DropRejectionReason.OUTSIDE_WORLD_BORDER,
					"Required nearby terrain crosses the world border"));
			return null;
		}

		long now = clock.getAsLong();
		attempts.entrySet().removeIf(entry -> now - entry.getValue().started >= entry.getValue().duration);
		Attempt previous = attempts.get(caller);
		if (previous != null) {
			long seconds = Math.max(1, (previous.duration - (now - previous.started) + 999_999_999) / 1_000_000_000);
			rejected.accept(new DropRejection(DropRejectionReason.REMOTE_LOAD_THROTTLED,
					"Wait before another remote delivery attempt", Optional.of(Duration.ofSeconds(seconds))));
			return null;
		}
		if (outstanding >= ConfigKeys.getMaxRemoteLoads()) {
			rejected.accept(DropRejection.of(DropRejectionReason.REMOTE_LOAD_CAPACITY,
					"Remote deliveries are busy"));
			return null;
		}
		Load load = new Load(rejected, ConfigKeys.getRemoteLoadTimeoutSeconds(), generate);
		outstanding++;
		attempts.put(caller, new Attempt(now, Duration.ofSeconds(ConfigKeys.getRemoteAttemptCooldownSeconds()).toNanos()));
		prepareChunk(load, target.getWorld(), target.getBlockX() >> 4, target.getBlockZ() >> 4,
				0, ready);

		return load;
	}

	/** One sequential backend operation per preparation keeps even neighborhood reads within the cap. */
	private void prepareChunk(Load load, World world, int centerX, int centerZ, int index,
			BiConsumer<Load, Chunk> ready) {
		// Load the 5x5 entity-ticking neighborhood, then refresh the center before retaining it.
		int x = index == 25 ? centerX : centerX - 2 + index / 5;
		int z = index == 25 ? centerZ : centerZ - 2 + index % 5;
		try {
			world.getChunkAtAsync(x, z, load.generate, false).whenComplete((chunk, failure) -> {
				if (load.done || !plugin.isEnabled()) {
					outstanding--;
					load.close();
					return;
				}
				if (Bukkit.getWorld(world.getUID()) != world) {
					outstanding--;
					load.reject(DropRejectionReason.INVALID_TARGET, "Destination world unloaded");
				} else if (load.generate && !generationFitsBorder(world, centerX, centerZ)) {
					outstanding--;
					load.reject(DropRejectionReason.OUTSIDE_WORLD_BORDER, "Required nearby terrain crosses the world border");
				} else if (load.expired()) {
					outstanding--;
					load.timeout();
				} else if (failure != null || chunk == null) {
					outstanding--;
					load.reject(!load.generate && chunk == null && failure == null
							? DropRejectionReason.TARGET_NOT_GENERATED : DropRejectionReason.INVALID_TARGET,
							"Destination and nearby terrain are unavailable; generation may be disabled");
				} else if (index < 25) {
					prepareChunk(load, world, centerX, centerZ, index + 1, ready);
				} else {
					outstanding--;
					ready.accept(load, chunk);
				}
			});
		} catch (RuntimeException failure) {
			outstanding--;
			load.reject(DropRejectionReason.INVALID_TARGET, "Could not load destination terrain");
		}
	}

	private static boolean generationFitsBorder(World world, int centerX, int centerZ) {
		var border = world.getWorldBorder();
		Location center = border.getCenter();
		double halfSize = border.getSize() / 2;
		return (centerX - 2) * 16.0 >= center.getX() - halfSize
				&& (centerX + 3) * 16.0 <= center.getX() + halfSize
				&& (centerZ - 2) * 16.0 >= center.getZ() - halfSize
				&& (centerZ + 3) * 16.0 <= center.getZ() + halfSize;
	}

	Runnable retainIfOwned(Chunk chunk) {
		return tickets.containsKey(new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()))
				? retain(chunk) : null;
	}

	Runnable retain(Chunk chunk) {
		World world = chunk.getWorld();
		ChunkKey key = new ChunkKey(world.getUID(), chunk.getX(), chunk.getZ());
		if (!world.isChunkLoaded(key.x, key.z)) {
			throw new IllegalStateException("Destination unloaded before retention");
		}
		if (!tickets.containsKey(key)) {
			world.addPluginChunkTicket(key.x, key.z, plugin);
		}
		tickets.merge(key, 1, Integer::sum);
		return new Runnable() {
			private boolean released;
			@Override
			public void run() {
				if (released) return;
				released = true;
				int remaining = tickets.get(key) - 1;
				if (remaining == 0) {
					world.removePluginChunkTicket(key.x, key.z, plugin);
					tickets.remove(key);
				} else {
					tickets.put(key, remaining);
				}
			}
		};
	}

	final class Load implements AutoCloseable {
		private final Consumer<DropRejection> rejected;
		private final long started = clock.getAsLong();
		private final long timeoutNanos;
		final boolean generate;
		private final BukkitTask deadline;
		private boolean done;

		Load(Consumer<DropRejection> rejected, int timeoutSeconds, boolean generate) {
			this.generate = generate;
			this.rejected = rejected;
			this.timeoutNanos = Duration.ofSeconds(timeoutSeconds).toNanos();
			this.deadline = plugin.getServer().getScheduler().runTaskLater(plugin, this::timeout, timeoutSeconds * 20L);
		}

		boolean expired() {
			return clock.getAsLong() - started >= timeoutNanos;
		}

		void timeout() {
			reject(DropRejectionReason.TARGET_LOAD_TIMEOUT, "Destination took too long to load");
		}

		void reject(DropRejectionReason reason, String diagnostic) {
			if (done) return;
			close();
			rejected.accept(DropRejection.of(reason, diagnostic));
		}

		@Override
		public void close() {
			done = true;
			deadline.cancel();
		}
	}
}
