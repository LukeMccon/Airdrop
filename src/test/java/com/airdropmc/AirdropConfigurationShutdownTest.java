package com.airdropmc;

import com.airdropmc.api.AirdropApi;
import com.airdropmc.api.PackageRegistryCause;
import com.airdropmc.api.ReadinessState;
import com.airdropmc.api.event.PackageRegistryChangedEvent;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.economy.EconomyProviderRefreshResult;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.packages.PackageManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AirdropConfigurationShutdownTest {

	private ServerMock server;
	private PluginMock observerPlugin;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock(new ServerMock() {
			@Override
			public boolean isStopping() {
				return false;
			}
		});
		observerPlugin = MockBukkit.createMockPlugin("ConfigurationObserver");
		net.milkbowl.vault.economy.Economy economy = mock(net.milkbowl.vault.economy.Economy.class);
		when(economy.isEnabled()).thenReturn(true);
		when(economy.getName()).thenReturn("ConfigurationEconomy");
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
	@EnumSource(value = PackageRegistryCause.class, names = {"STARTUP", "RELOAD"})
	void registryListenerDisableStopsTheRemainingConfigurationCommit(PackageRegistryCause cause)
			throws Exception {
		Airdrop plugin = preparedPlugin();
		ShutdownObserver observer = new ShutdownObserver(plugin, cause);
		server.getPluginManager().registerEvents(observer, observerPlugin);
		server.getPluginManager().enablePlugin(plugin);
		AirdropApi api = server.getServicesManager().load(AirdropApi.class);
		if (cause == PackageRegistryCause.RELOAD) {
			awaitCondition(() -> api.state() == ReadinessState.READY || !plugin.isEnabled());
			assertTrue(Airdrop.isReady());
			CompletableFuture<EconomyProviderRefreshResult> reload =
					plugin.reloadConfiguration().toCompletableFuture();
			awaitCondition(reload::isDone);
			CompletionException failure = assertThrows(CompletionException.class, reload::join);
			assertInstanceOf(CancellationException.class, failure.getCause());
			CompletableFuture<AirdropApi> readiness = api.readiness().toCompletableFuture();
			assertTrue(readiness.isDone());
			assertSame(api, readiness.join(), "Reload must preserve the earlier readiness result");
		} else {
			CompletableFuture<AirdropApi> readiness = api.readiness().toCompletableFuture();
			awaitCondition(readiness::isDone);
			assertThrows(CompletionException.class, readiness::join);
		}

		assertTrue(observer.disabled, "The independent listener must exercise real plugin disable");
		assertAll(
				() -> assertFalse(plugin.isEnabled()),
				() -> assertFalse(Airdrop.isReady(), "An interrupted startup cannot become ready"),
				() -> assertTrue(Airdrop.isShuttingDown()),
				() -> assertNull(Airdrop.getPluginInstance()),
				() -> assertNull(Airdrop.getEconomyProvider(), "Disable must keep the provider cleared"),
				() -> assertNull(Airdrop.getPackagesGui(), "Disable must keep the browser cleared"),
				() -> assertNull(Airdrop.getConfiguration()),
				() -> assertNull(Airdrop.getPackagesConfiguration()),
				() -> assertTrue(PackageManager.getPackages().isEmpty()),
				() -> assertEquals(ReadinessState.STOPPING, api.state()),
				() -> assertNull(server.getServicesManager().load(AirdropApi.class)));
	}

	@Test
	void registryListenerRestartWaitsForTheReplacementConfigurationBeforeReadiness() throws Exception {
		Airdrop plugin = preparedPlugin();
		RestartObserver observer = new RestartObserver(plugin);
		server.getPluginManager().registerEvents(observer, observerPlugin);
		server.getPluginManager().enablePlugin(plugin);
		AirdropApi originalApi = server.getServicesManager().load(AirdropApi.class);

		awaitCondition(() -> observer.ownConfigurationAtReadiness != null);

		assertTrue(observer.ownConfigurationAtReadiness,
				"Replacement readiness must wait for its configuration, not the interrupted commit");
		assertNotSame(originalApi, observer.replacementApi);
		assertEquals(ReadinessState.STOPPING, originalApi.state());
		assertEquals(ReadinessState.READY, observer.replacementApi.state());
		assertTrue(plugin.isEnabled());
		assertTrue(Airdrop.isReady());
		assertFalse(ConfigKeys.isEconomyEnabled());
		assertNull(Airdrop.getEconomyProvider());
	}

	private Airdrop preparedPlugin() throws Exception {
		Airdrop plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(plugin.getDataFolder().toPath());
		Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"),
				"language: en\neconomy:\n  enabled: true\n", StandardCharsets.UTF_8);
		Files.writeString(plugin.getDataFolder().toPath().resolve("packages.yml"),
				"packages:\n  starter:\n    price: 0\n    items: []\n", StandardCharsets.UTF_8);
		return plugin;
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
		assertTrue(condition.getAsBoolean(), "Timed out waiting for the configuration callback");
	}

	private final class ShutdownObserver implements Listener {
		private final Airdrop plugin;
		private final PackageRegistryCause cause;
		private boolean disabled;

		private ShutdownObserver(Airdrop plugin, PackageRegistryCause cause) {
			this.plugin = plugin;
			this.cause = cause;
		}

		@EventHandler
		public void onRegistryChanged(PackageRegistryChangedEvent event) {
			if (event.cause() == cause) {
				server.getPluginManager().disablePlugin(plugin);
				disabled = true;
			}
		}
	}

	private final class RestartObserver implements Listener {
		private final Airdrop plugin;
		private boolean restarted;
		private AirdropApi replacementApi;
		private Boolean ownConfigurationAtReadiness;

		private RestartObserver(Airdrop plugin) {
			this.plugin = plugin;
		}

		@EventHandler
		public void onRegistryChanged(PackageRegistryChangedEvent event) throws IOException {
			if (restarted || event.cause() != PackageRegistryCause.STARTUP) {
				return;
			}
			restarted = true;
			server.getPluginManager().disablePlugin(plugin);
			Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"),
					"language: en\neconomy:\n  enabled: false\n", StandardCharsets.UTF_8);
			server.getPluginManager().enablePlugin(plugin);
			replacementApi = server.getServicesManager().load(AirdropApi.class);
			replacementApi.readiness().thenRun(() -> ownConfigurationAtReadiness =
					Airdrop.getConfiguration() != null && !ConfigKeys.isEconomyEnabled());
		}
	}
}
