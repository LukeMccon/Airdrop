package com.airdropmc.api;

import com.airdropmc.Airdrop;
import com.airdropmc.api.event.PackageRegistryChangedEvent;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.listeners.CrateHopperListener;
import com.airdropmc.packages.PackageManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AirdropApiServiceTest {

	private ServerMock server;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
	}

	@AfterEach
	void tearDown() {
		CrateManager.clearAll();
		PackageManager.clear();
		MockBukkit.unmock();
	}

	@Test
	void registersOneNormalProviderBeforeStartupCommitAndPublishesThatProvider() throws Exception {
		AtomicBoolean serviceEventOnPrimaryThread = new AtomicBoolean();
		PluginMock observer = MockBukkit.createMockPlugin("ServiceObserver");
		server.getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onServiceRegister(ServiceRegisterEvent event) {
				if (event.getProvider().getService() == AirdropApi.class) {
					serviceEventOnPrimaryThread.set(org.bukkit.Bukkit.isPrimaryThread());
				}
			}
		}, observer);

		Airdrop plugin = preparedPlugin(false);
		server.getPluginManager().enablePlugin(plugin);

		List<RegisteredServiceProvider<?>> registrations = server.getServicesManager()
				.getRegistrations(plugin);
		RegisteredServiceProvider<?> registration = registrations.stream()
				.filter(candidate -> candidate.getService() == AirdropApi.class)
				.findFirst()
				.orElseThrow();
		AirdropApi api = (AirdropApi) registration.getProvider();
		assertEquals(ServicePriority.Normal, registration.getPriority());
		assertEquals(1L, registrations.stream()
				.filter(candidate -> candidate.getService() == AirdropApi.class)
				.count());
		assertSame(api, server.getServicesManager().load(AirdropApi.class));
		assertEquals(ReadinessState.STARTING, api.state());
		assertEquals(EconomyState.STARTING, api.status().economy());
		assertEquals(0, api.status().pendingCount());
		assertTrue(api.status().maxFalling().isEmpty());
		assertTrue(api.status().maxLanded().isEmpty());
		assertTrue(api.status().lastDiagnostic().isEmpty());
		assertFalse(api.readiness().toCompletableFuture().isDone());
		assertTrue(serviceEventOnPrimaryThread.get());

		awaitReady(plugin);

		assertSame(api, api.readiness().toCompletableFuture().join());
		assertEquals(ReadinessState.READY, api.state());
		assertEquals(ReadinessState.READY, api.status().readiness());
		assertEquals(EconomyState.DISABLED, api.status().economy());
		assertEquals(3, api.status().maxFalling().orElseThrow());
		assertEquals(10, api.status().maxLanded().orElseThrow());
		assertEquals(0, api.status().pendingCount());
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void serviceRegistrationListenerStopsTheInterruptedEnable(boolean restart) throws Exception {
		Airdrop plugin = preparedPlugin(false);
		List<AirdropApi> registeredApis = new ArrayList<>();
		AtomicInteger startupPublications = new AtomicInteger();
		PluginMock observer = MockBukkit.createMockPlugin("ServiceLifecycleObserver");
		server.getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onServiceRegister(ServiceRegisterEvent event) {
				if (event.getProvider().getService() != AirdropApi.class
						|| event.getProvider().getPlugin() != plugin) {
					return;
				}
				registeredApis.add((AirdropApi) event.getProvider().getProvider());
				if (registeredApis.size() == 1) {
					server.getPluginManager().disablePlugin(plugin);
					if (restart) {
						server.getPluginManager().enablePlugin(plugin);
					}
				}
			}

			@EventHandler
			public void onRegistryChanged(PackageRegistryChangedEvent event) {
				if (event.cause() == PackageRegistryCause.STARTUP) {
					startupPublications.incrementAndGet();
				}
			}
		}, observer);

		server.getPluginManager().enablePlugin(plugin);

		assertEquals(restart ? 2 : 1, registeredApis.size());
		AirdropApi interrupted = registeredApis.getFirst();
		assertEquals(ReadinessState.STOPPING, interrupted.state());
		CompletableFuture<AirdropApi> interruptedReadiness = interrupted.readiness().toCompletableFuture();
		assertTrue(interruptedReadiness.isDone());
		assertThrows(CompletionException.class, interruptedReadiness::join);
		assertEquals(0L, interrupted.packageRevision());
		if (!restart) {
			assertAll(
					() -> assertFalse(plugin.isEnabled()),
					() -> assertNull(Airdrop.getPluginInstance()),
					() -> assertNull(plugin.getLanguageManager(), "Stopped enable must not initialize language"),
					() -> assertTrue(HandlerList.getRegisteredListeners(plugin).isEmpty()),
					() -> assertTrue(server.getServicesManager().getRegistrations(plugin).isEmpty()),
					() -> assertEquals(0, startupPublications.get()));
			return;
		}

		AirdropApi replacement = registeredApis.getLast();
		assertNotSame(interrupted, replacement);
		assertSame(replacement, requireApi(plugin));
		awaitReady(plugin);
		CompletableFuture<AirdropApi> replacementReadiness = replacement.readiness().toCompletableFuture();
		assertTrue(replacementReadiness.isDone());
		assertSame(replacement, replacementReadiness.join());
		assertAll(
				() -> assertEquals(ReadinessState.READY, replacement.state()),
				() -> assertEquals(1L, replacement.packageRevision()),
				() -> assertEquals(1, startupPublications.get()),
				() -> assertEquals(1L, HandlerList.getRegisteredListeners(plugin).stream()
						.filter(listener -> listener.getListener() instanceof CrateHopperListener).count()),
				() -> assertEquals(1L, server.getServicesManager().getRegistrations(plugin).stream()
						.filter(registration -> registration.getService() == AirdropApi.class).count()));
	}

	@Test
	void replacesOnlyStaleSamePluginProviderAndRepeatedDisableIsSafe() throws Exception {
		Airdrop plugin = preparedPlugin(false);
		PluginMock foreignPlugin = MockBukkit.createMockPlugin("ForeignConsumer");
		AirdropApi stale = mock(AirdropApi.class);
		AirdropApi foreign = mock(AirdropApi.class);
		server.getServicesManager().register(
				AirdropApi.class, stale, plugin, ServicePriority.Low);
		server.getServicesManager().register(
				AirdropApi.class, foreign, foreignPlugin, ServicePriority.High);

		server.getPluginManager().enablePlugin(plugin);
		AirdropApi current = server.getServicesManager().getRegistrations(plugin).stream()
				.filter(candidate -> candidate.getService() == AirdropApi.class)
				.map(candidate -> (AirdropApi) candidate.getProvider())
				.findFirst()
				.orElseThrow();

		assertFalse(current == stale);
		assertTrue(server.getServicesManager().getRegistrations(AirdropApi.class).stream()
				.anyMatch(candidate -> candidate.getProvider() == foreign));
		assertFalse(server.getServicesManager().getRegistrations(AirdropApi.class).stream()
				.anyMatch(candidate -> candidate.getProvider() == stale));

		server.getPluginManager().disablePlugin(plugin);

		assertEquals(ReadinessState.STOPPING, current.state());
		assertFalse(server.getServicesManager().getRegistrations(AirdropApi.class).stream()
				.anyMatch(candidate -> candidate.getProvider() == current));
		assertTrue(server.getServicesManager().getRegistrations(AirdropApi.class).stream()
				.anyMatch(candidate -> candidate.getProvider() == foreign));
		assertDoesNotThrow(plugin::onDisable);
		assertTrue(server.getServicesManager().getRegistrations(AirdropApi.class).stream()
				.anyMatch(candidate -> candidate.getProvider() == foreign));
	}

	@Test
	void versionSnapshotSurvivesPluginStaticCleanup() throws Exception {
		Airdrop plugin = preparedPlugin(false);
		server.getPluginManager().enablePlugin(plugin);
		AirdropApi api = requireApi(plugin);
		AirdropVersions versions = api.versions();

		server.getPluginManager().disablePlugin(plugin);

		assertNotNull(versions.pluginVersion());
		assertFalse(versions.pluginVersion().isBlank());
		assertFalse(versions.extensionApiVersion().isBlank());
		assertEquals("1.21.11", versions.paperApiVersion());
		assertFalse(versions.javaVersion().isBlank());
		assertEquals(versions, api.versions());
	}

	private Airdrop preparedPlugin(boolean economyEnabled) throws Exception {
		Airdrop plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(plugin.getDataFolder().toPath());
		Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"),
				"language: en\neconomy:\n  enabled: " + economyEnabled + "\n",
				StandardCharsets.UTF_8);
		Files.writeString(plugin.getDataFolder().toPath().resolve("packages.yml"),
				"packages: {}\n", StandardCharsets.UTF_8);
		return plugin;
	}

	private AirdropApi requireApi(Airdrop plugin) {
		return server.getServicesManager().getRegistrations(plugin).stream()
				.filter(candidate -> candidate.getService() == AirdropApi.class)
				.map(candidate -> (AirdropApi) candidate.getProvider())
				.findFirst()
				.orElseThrow();
	}

	private void awaitReady(Airdrop plugin) {
		awaitCondition(() -> Airdrop.isReady() || !plugin.isEnabled());
		assertTrue(plugin.isEnabled(), "Airdrop disabled during startup");
		assertTrue(Airdrop.isReady(), "Timed out waiting for Airdrop startup");
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
		assertTrue(condition.getAsBoolean(), "Timed out waiting for asynchronous lifecycle work");
	}
}
