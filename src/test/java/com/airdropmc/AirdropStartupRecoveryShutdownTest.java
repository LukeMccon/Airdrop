package com.airdropmc;

import com.airdropmc.api.AirdropApi;
import com.airdropmc.api.ReadinessState;
import com.airdropmc.api.event.AirdropRecoveredEvent;
import com.airdropmc.api.event.PackageRegistryChangedEvent;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.packages.PackageManager;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AirdropStartupRecoveryShutdownTest {

	private ServerMock server;
	private WorldMock world;
	private PluginMock observerPlugin;
	private final List<Block> recoveryBlocks = new ArrayList<>();

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock(new ServerMock() {
			@Override
			public boolean isStopping() {
				return false;
			}
		});
		Chunk chunk = mock(Chunk.class);
		world = new WorldMock() {
			@Override
			public Chunk[] getLoadedChunks() {
				return new Chunk[] { chunk };
			}

			@Override
			public void save() {
				// MockBukkit does not persist worlds; retain the in-memory block state.
			}

			@Override
			public void save(boolean flush) {
				// The production recovery path requests a flushed world save.
			}
		};
		server.addWorld(world);
		when(chunk.getWorld()).thenReturn(world);
		when(chunk.getTileEntities()).thenAnswer(ignored -> recoveryBlocks.stream()
				.filter(block -> block.getType() == Material.BARREL)
				.map(Block::getState).toArray(BlockState[]::new));
		observerPlugin = MockBukkit.createMockPlugin("StartupRecoveryObserver");
		net.milkbowl.vault.economy.Economy economy = mock(net.milkbowl.vault.economy.Economy.class);
		when(economy.isEnabled()).thenReturn(true);
		when(economy.getName()).thenReturn("RecoveryEconomy");
		server.getServicesManager().register(net.milkbowl.vault.economy.Economy.class,
				economy, observerPlugin, ServicePriority.Normal);
	}

	@AfterEach
	void tearDown() {
		CrateManager.clearAll();
		PackageManager.clear();
		MockBukkit.unmock();
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 2})
	void recoveryListenerDisableKeepsStartupStateCleared(int recoveredCrates) throws Exception {
		Airdrop plugin = preparedPlugin(recoveredCrates);
		RecoveryObserver observer = new RecoveryObserver(plugin, false);
		server.getPluginManager().registerEvents(observer, observerPlugin);
		server.getPluginManager().enablePlugin(plugin);
		AirdropApi api = server.getServicesManager().load(AirdropApi.class);

		awaitCondition(() -> observer.interrupted);

		assertAll(
				() -> assertFalse(plugin.isEnabled()),
				() -> assertFalse(Airdrop.isReady(), "Interrupted recovery cannot make startup ready"),
				() -> assertTrue(Airdrop.isShuttingDown()),
				() -> assertNull(Airdrop.getPluginInstance()),
				() -> assertNull(Airdrop.getEconomyProvider(), "Disable must keep the provider cleared"),
				() -> assertNull(Airdrop.getPackagesGui(), "Disable must keep the browser cleared"),
				() -> assertNull(Airdrop.getConfiguration()),
				() -> assertNull(Airdrop.getPackagesConfiguration()),
				() -> assertTrue(PackageManager.getPackages().isEmpty()),
				() -> assertEquals(0, observer.registryPublications),
				() -> assertEquals(1, observer.recoveryPublications),
				() -> assertTrue(CrateManager.activeDrops().isEmpty()),
				() -> assertEquals(ReadinessState.STOPPING, api.state()),
				() -> assertThrows(CompletionException.class, () -> api.readiness().toCompletableFuture().join()),
				() -> assertNull(server.getServicesManager().load(AirdropApi.class)));
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 2})
	void recoveryListenerRestartWaitsForItsOwnConfiguration(int recoveredCrates) throws Exception {
		Airdrop plugin = preparedPlugin(recoveredCrates);
		RecoveryObserver observer = new RecoveryObserver(plugin, true);
		server.getPluginManager().registerEvents(observer, observerPlugin);
		server.getPluginManager().enablePlugin(plugin);
		AirdropApi originalApi = server.getServicesManager().load(AirdropApi.class);

		awaitCondition(() -> observer.ownConfigurationAtReadiness != null);

		assertAll(
				() -> assertTrue(observer.interrupted),
				() -> assertTrue(observer.ownConfigurationAtReadiness,
						"Replacement readiness must wait for its own configuration"),
				() -> assertNotSame(originalApi, observer.replacementApi),
				() -> assertEquals(ReadinessState.STOPPING, originalApi.state()),
				() -> assertEquals(ReadinessState.READY, observer.replacementApi.state()),
				() -> assertTrue(plugin.isEnabled()),
				() -> assertTrue(Airdrop.isReady()),
				() -> assertFalse(ConfigKeys.isEconomyEnabled()),
				() -> assertNull(Airdrop.getEconomyProvider()),
				() -> assertEquals(1, observer.registryPublications),
				() -> assertEquals(1, observer.recoveryPublications),
				() -> assertTrue(CrateManager.activeDrops().isEmpty()));
	}

	private Airdrop preparedPlugin(int recoveredCrates) throws Exception {
		Airdrop plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(plugin.getDataFolder().toPath());
		writeConfiguration(plugin, true);
		Files.writeString(plugin.getDataFolder().toPath().resolve("packages.yml"),
				"packages:\n  starter:\n    price: 0\n    items: []\n", StandardCharsets.UTF_8);
		for (int index = 0; index < recoveredCrates; index++) {
			Block block = world.getBlockAt(8 + index, 64, 8);
			block.setType(Material.BARREL);
			Barrel barrel = (Barrel) block.getState();
			barrel.getSnapshotInventory().setItem(0, new ItemStack(Material.DIAMOND));
			barrel.getPersistentDataContainer().set(new NamespacedKey(plugin, "crate_id"),
					PersistentDataType.STRING, UUID.randomUUID().toString());
			barrel.getPersistentDataContainer().set(new NamespacedKey(plugin, "paid"),
					PersistentDataType.BYTE, (byte) 1);
			barrel.getPersistentDataContainer().set(new NamespacedKey(plugin, "expires_at"),
					PersistentDataType.LONG, System.currentTimeMillis() + 60_000L);
			barrel.getPersistentDataContainer().set(new NamespacedKey(plugin, "recovery_state"),
					PersistentDataType.STRING, Crate.RecoveryState.RECOVERABLE.name());
			assertTrue(barrel.update(true, false));
			barrel.getInventory().setStorageContents(barrel.getSnapshotInventory().getStorageContents());
			recoveryBlocks.add(block);
		}
		return plugin;
	}

	private void writeConfiguration(Airdrop plugin, boolean economyEnabled) throws IOException {
		Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"),
				"language: en\neconomy:\n  enabled: " + economyEnabled + "\n", StandardCharsets.UTF_8);
	}

	private void awaitCondition(java.util.function.BooleanSupplier condition) {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			server.getScheduler().performOneTick();
			LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
		}
		assertTrue(condition.getAsBoolean(), "Timed out waiting for the recovery callback");
	}

	private final class RecoveryObserver implements Listener {
		private final Airdrop plugin;
		private final boolean restart;
		private boolean interrupted;
		private int registryPublications;
		private int recoveryPublications;
		private AirdropApi replacementApi;
		private Boolean ownConfigurationAtReadiness;

		private RecoveryObserver(Airdrop plugin, boolean restart) {
			this.plugin = plugin;
			this.restart = restart;
		}

		@EventHandler
		public void onRecovered(AirdropRecoveredEvent event) throws IOException {
			recoveryPublications++;
			if (interrupted) {
				return;
			}
			interrupted = true;
			server.getPluginManager().disablePlugin(plugin);
			if (restart) {
				writeConfiguration(plugin, false);
				server.getPluginManager().enablePlugin(plugin);
				replacementApi = server.getServicesManager().load(AirdropApi.class);
				replacementApi.readiness().thenRun(() -> ownConfigurationAtReadiness =
						Airdrop.getConfiguration() != null && !ConfigKeys.isEconomyEnabled());
			}
		}

		@EventHandler
		public void onRegistryChanged(PackageRegistryChangedEvent event) {
			registryPublications++;
		}
	}
}
