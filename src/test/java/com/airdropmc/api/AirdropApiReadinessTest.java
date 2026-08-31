package com.airdropmc.api;

import com.airdropmc.Airdrop;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.packages.PackageManager;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

class AirdropApiReadinessTest {

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
	void readyCompletionOriginatesOnPrimaryThreadAndConsumerCannotCompleteBackingStage()
			throws Exception {
		Airdrop plugin = preparedPlugin("packages: {}\n", true);
		server.getPluginManager().enablePlugin(plugin);
		AirdropApi api = requireApi(plugin);
		AtomicBoolean completionOnPrimaryThread = new AtomicBoolean();
		api.readiness().whenComplete((ignored, failure) ->
				completionOnPrimaryThread.set(Bukkit.isPrimaryThread()));

		api.readiness().toCompletableFuture().complete(api);
		assertFalse(api.readiness().toCompletableFuture().isDone(),
				"consumer completion must affect only a detached stage view");

		awaitCondition(() -> api.state() == ReadinessState.READY || !plugin.isEnabled());

		assertTrue(plugin.isEnabled());
		assertSame(api, api.readiness().toCompletableFuture().join());
		assertTrue(completionOnPrimaryThread.get());
		assertEquals(EconomyState.UNAVAILABLE, api.status().economy());
		assertTrue(api.status().degradedReasons().contains("economy-provider-unavailable"));
		assertTrue(api.status().degradedReasons().contains("luckperms-not-installed"));
	}

	@Test
	void malformedStartupPublishesFailedBeforeSynchronousDisable() throws Exception {
		Airdrop plugin = preparedPlugin("packages: [\n", true);
		server.getPluginManager().enablePlugin(plugin);
		AirdropApi api = requireApi(plugin);
		AtomicReference<ReadinessState> stateAtFailure = new AtomicReference<>();
		AtomicBoolean completionOnPrimaryThread = new AtomicBoolean();
		api.readiness().whenComplete((ignored, failure) -> {
			stateAtFailure.set(api.state());
			completionOnPrimaryThread.set(Bukkit.isPrimaryThread());
		});

		awaitCondition(() -> !plugin.isEnabled());

		assertThrows(CompletionException.class, () -> api.readiness().toCompletableFuture().join());
		assertEquals(ReadinessState.FAILED, stateAtFailure.get());
		assertTrue(completionOnPrimaryThread.get());
		assertEquals(ReadinessState.STOPPING, api.state());
		assertTrue(server.getServicesManager().getRegistrations(AirdropApi.class).stream()
				.noneMatch(candidate -> candidate.getProvider() == api));
	}

	@Test
	void catastrophicRecoveryScanFailureIsFatal() throws Exception {
		Airdrop plugin = preparedPlugin("packages: {}\n", false);
		try (MockedStatic<CrateManager> crates = Mockito.mockStatic(
				CrateManager.class, Mockito.CALLS_REAL_METHODS)) {
			crates.when(() -> CrateManager.recoverLoadedCrates(any(), any()))
					.thenThrow(new IllegalStateException("world scan failed"));
			server.getPluginManager().enablePlugin(plugin);
			AirdropApi api = requireApi(plugin);

			awaitCondition(() -> !plugin.isEnabled());

			assertThrows(CompletionException.class,
					() -> api.readiness().toCompletableFuture().join());
			assertEquals(ReadinessState.STOPPING, api.state());
		}
	}

	@Test
	void disableDuringStartupStopsAndFailsIncompleteReadiness() throws Exception {
		Airdrop plugin = preparedPlugin("packages: {}\n", false);
		server.getPluginManager().enablePlugin(plugin);
		AirdropApi api = requireApi(plugin);

		server.getPluginManager().disablePlugin(plugin);

		assertEquals(ReadinessState.STOPPING, api.state());
		assertThrows(CompletionException.class, () -> api.readiness().toCompletableFuture().join());
		assertTrue(server.getServicesManager().getRegistrations(AirdropApi.class).stream()
				.noneMatch(candidate -> candidate.getProvider() == api));
	}

	private Airdrop preparedPlugin(String packages, boolean economyEnabled) throws Exception {
		Airdrop plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(plugin.getDataFolder().toPath());
		Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"),
				"language: en\neconomy:\n  enabled: " + economyEnabled + "\n",
				StandardCharsets.UTF_8);
		Files.writeString(plugin.getDataFolder().toPath().resolve("packages.yml"),
				packages, StandardCharsets.UTF_8);
		return plugin;
	}

	private AirdropApi requireApi(Airdrop plugin) {
		return server.getServicesManager().getRegistrations(plugin).stream()
				.filter(candidate -> candidate.getService() == AirdropApi.class)
				.map(candidate -> (AirdropApi) candidate.getProvider())
				.findFirst()
				.orElseThrow();
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
