package com.airdropmc.api;

import com.airdropmc.Airdrop;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.packages.PackageManager;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AirdropApiThreadContractTest {

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
	void pureSnapshotsAndUuidQueriesAreSafeOffThreadWhileBukkitMethodsFailFast() throws Exception {
		Airdrop plugin = loadReadyPlugin();
		AirdropApi api = requireApi(plugin);
		FallingBlock falling = mock(FallingBlock.class);
		Block landed = mock(Block.class);

		CompletableFuture<Void> pureAccess = CompletableFuture.runAsync(() -> {
			assertEquals(ReadinessState.READY, api.state());
			assertFalse(api.versions().pluginVersion().isBlank());
			assertEquals(ReadinessState.READY, api.status().readiness());
			assertTrue(api.activeDrops().isEmpty());
			assertTrue(api.findByRequestId(UUID.randomUUID()).isEmpty());
			assertTrue(api.findByCrateId(UUID.randomUUID()).isEmpty());
			assertTrue(api.readiness().toCompletableFuture().isDone());
		});
		pureAccess.join();

		assertOffThreadFailure(() -> api.listPackages());
		assertOffThreadFailure(() -> api.findPackage("starter"));
		assertOffThreadFailure(() -> api.findByFallingEntity(falling));
		assertOffThreadFailure(() -> api.findByLandedBlock(landed));

		assertEquals(1, api.listPackages().size());
		assertEquals("starter", api.findPackage("STARTER").orElseThrow().name());
	}

	@Test
	void continuationAttachedAfterReadinessRunsOnAttachingThread() throws Exception {
		Airdrop plugin = loadReadyPlugin();
		AirdropApi api = requireApi(plugin);
		AtomicReference<String> attachingThread = new AtomicReference<>();
		AtomicReference<String> continuationThread = new AtomicReference<>();

		CompletableFuture.runAsync(() -> {
			attachingThread.set(Thread.currentThread().getName());
			api.readiness().thenRun(() ->
					continuationThread.set(Thread.currentThread().getName()));
		}).join();

		assertEquals(attachingThread.get(), continuationThread.get());
	}

	@Test
	void statusIsFinalImmutableClassThatCanGainFieldsWithoutRecordShape() {
		assertTrue(Modifier.isFinal(AirdropStatus.class.getModifiers()));
		assertFalse(AirdropStatus.class.isRecord());
		List<String> reasons = new ArrayList<>(List.of("economy-provider-unavailable"));
		AirdropStatus status = new AirdropStatus(
				ReadinessState.READY,
				EconomyState.UNAVAILABLE,
				null,
				1,
				0,
				0,
				reasons);
		reasons.clear();
		assertEquals(List.of("economy-provider-unavailable"), status.degradedReasons());
		assertThrows(UnsupportedOperationException.class,
				() -> status.degradedReasons().add("mutated"));
		assertThrows(IllegalArgumentException.class, () -> new AirdropStatus(
				ReadinessState.READY,
				EconomyState.UNAVAILABLE,
				"impossible-provider",
				0,
				0,
				0,
				List.of("economy-provider-unavailable")));
	}

	private Airdrop loadReadyPlugin() throws Exception {
		Airdrop plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(plugin.getDataFolder().toPath());
		Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"),
				"language: en\neconomy:\n  enabled: false\n", StandardCharsets.UTF_8);
		Files.writeString(plugin.getDataFolder().toPath().resolve("packages.yml"), """
				packages:
				  starter:
				    price: 0.0
				    items: []
				""", StandardCharsets.UTF_8);
		server.getPluginManager().enablePlugin(plugin);
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline && plugin.isEnabled() && !Airdrop.isReady()) {
			server.getScheduler().performOneTick();
			LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
		}
		assertTrue(plugin.isEnabled());
		assertTrue(Airdrop.isReady());
		return plugin;
	}

	private AirdropApi requireApi(Airdrop plugin) {
		return server.getServicesManager().getRegistrations(plugin).stream()
				.filter(candidate -> candidate.getService() == AirdropApi.class)
				.map(candidate -> (AirdropApi) candidate.getProvider())
				.findFirst()
				.orElseThrow();
	}

	private static void assertOffThreadFailure(Runnable call) {
		CompletionException completion = assertThrows(CompletionException.class,
				() -> CompletableFuture.runAsync(call).join());
		assertInstanceOf(IllegalStateException.class, completion.getCause());
	}
}
