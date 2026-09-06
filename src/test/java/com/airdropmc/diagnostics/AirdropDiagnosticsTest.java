package com.airdropmc.diagnostics;

import com.airdropmc.Airdrop;
import com.airdropmc.api.AirdropApi;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.internal.diagnostics.AirdropDiagnostics;
import com.airdropmc.packages.Package;
import com.airdropmc.packages.PackageManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirdropDiagnosticsTest {
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
	void diagnosticMessagesAreSingleLineBoundedAndFailClosed() {
		AirdropDiagnostics diagnostics = new AirdropDiagnostics();
		String secret = "super-secret-token-value";
		String unsafe = "\u001B[31m§c{reload failed}\n"
				+ "/home/alex/server/plugins/Airdrop/config.yml "
				+ "C:\\server\\plugins\\Airdrop\\packages.yml "
				+ "https://alice:hunter2@example.invalid/config "
				+ "token=" + secret;

		diagnostics.record(
				AirdropDiagnostics.Category.CONFIGURATION,
				new IllegalStateException(unsafe));

		AirdropDiagnostics.Snapshot snapshot = diagnostics.snapshot().orElseThrow();
		String message = snapshot.message();
		assertEquals(AirdropDiagnostics.Category.CONFIGURATION, snapshot.category());
		assertTrue(message.codePointCount(0, message.length()) <= 160, message);
		for (String forbidden : new String[]{
				"\n", "\r", "\u001B", "§", "{", "}", "/home/", "C:\\server",
				"alice:hunter2", secret, "IllegalStateException"}) {
			assertFalse(message.contains(forbidden), forbidden + " leaked in: " + message);
		}
	}

	@Test
	void diagnosticsUseAStableFallbackInsteadOfThrowableIdentity() {
		AirdropDiagnostics diagnostics = new AirdropDiagnostics();

		diagnostics.record(
				AirdropDiagnostics.Category.STARTUP,
				new SecretStartupFailure());

		String message = diagnostics.snapshot().orElseThrow().message();
		assertFalse(message.isBlank());
		assertFalse(message.contains("SecretStartupFailure"), message);
	}

	@Test
	void clearOnlyRemovesTheMatchingCategory() {
		AirdropDiagnostics diagnostics = new AirdropDiagnostics();
		diagnostics.record(
				AirdropDiagnostics.Category.PACKAGE_REGISTRY,
				new IllegalArgumentException("candidate rejected"));
		AirdropDiagnostics.Snapshot retained = diagnostics.snapshot().orElseThrow();

		diagnostics.clear(AirdropDiagnostics.Category.CONFIGURATION);

		assertEquals(Optional.of(retained), diagnostics.snapshot());
		diagnostics.clear(AirdropDiagnostics.Category.PACKAGE_REGISTRY);
		assertTrue(diagnostics.snapshot().isEmpty());
	}

	@Test
	void returnedSnapshotsStayDetachedFromLaterRecords() {
		AirdropDiagnostics diagnostics = new AirdropDiagnostics();
		diagnostics.record(
				AirdropDiagnostics.Category.CONFIGURATION,
				new IllegalStateException("first failure"));
		AirdropDiagnostics.Snapshot first = diagnostics.snapshot().orElseThrow();

		diagnostics.record(
				AirdropDiagnostics.Category.PACKAGE_REGISTRY,
				new IllegalStateException("second failure"));

		assertEquals(AirdropDiagnostics.Category.CONFIGURATION, first.category());
		assertEquals("first failure", first.message());
		assertNotEquals(first, diagnostics.snapshot().orElseThrow());
	}

	@Test
	void providerLabelsUseTheSameSafeTextBoundary() {
		String safe = AirdropDiagnostics.sanitizeLabel(
				"§aVault\npassword=hunter2 /plugins/Vault/config.yml");

		assertFalse(safe.contains("§"), safe);
		assertFalse(safe.contains("\n"), safe);
		assertFalse(safe.contains("hunter2"), safe);
		assertFalse(safe.contains("/plugins/"), safe);
		assertTrue(safe.codePointCount(0, safe.length()) <= 80, safe);
	}

	@Test
	void deeplySegmentedUnixPathsDoNotOverflowTheRegexStack() throws InterruptedException {
		String unsafePath = "/" + "a/".repeat(1_023) + "z";
		AtomicReference<String> sanitized = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread sanitizer = new Thread(
				null,
				() -> sanitized.set(AirdropDiagnostics.sanitizeLabel(unsafePath)),
				"diagnostic-sanitizer-test",
				32 * 1_024);
		sanitizer.setDaemon(true);
		sanitizer.setUncaughtExceptionHandler((thread, problem) -> failure.set(problem));

		sanitizer.start();
		sanitizer.join(Duration.ofSeconds(5).toMillis());

		assertFalse(sanitizer.isAlive(), "Sanitizing a bounded path did not finish");
		assertNull(failure.get(), () -> "Sanitizing a bounded path failed: " + failure.get());
		assertEquals("[path]", sanitized.get());
	}

	@Test
	void configurationFailureIsPublishedAndOnlyAConfigurationSuccessClearsIt() throws Exception {
		Airdrop plugin = readyPlugin();
		AirdropApi api = server.getServicesManager().load(AirdropApi.class);
		Files.writeString(
				plugin.getDataFolder().toPath().resolve("packages.yml"),
				"packages: [\npassword: hunter2\n",
				StandardCharsets.UTF_8);

		CompletionStage<?> failed = plugin.reloadConfiguration();
		awaitDone(failed);
		assertThrows(CompletionException.class, () -> failed.toCompletableFuture().join());
		assertEquals("CONFIGURATION", api.status().lastDiagnosticCategory().orElseThrow());
		String diagnostic = api.status().lastDiagnostic().orElseThrow();
		assertFalse(diagnostic.contains("hunter2"), diagnostic);
		assertFalse(diagnostic.contains(plugin.getDataFolder().getAbsolutePath()), diagnostic);

		Files.writeString(
				plugin.getDataFolder().toPath().resolve("packages.yml"),
				"packages:\n  starter:\n    price: 0\n    items: []\n",
				StandardCharsets.UTF_8);
		CompletionStage<?> succeeded = plugin.reloadConfiguration();
		awaitDone(succeeded);
		succeeded.toCompletableFuture().join();
		assertTrue(api.status().lastDiagnostic().isEmpty());
	}

	@Test
	void packageMutationFailureIsPublishedAndOnlyAPackageSuccessClearsIt() throws Exception {
		Airdrop plugin = readyPlugin();
		AirdropApi api = server.getServicesManager().load(AirdropApi.class);

		CompletionStage<?> firstFailure = plugin.createPackageAsync(
				new Package("starter", 0.0, List.of()));
		awaitDone(firstFailure);
		assertThrows(CompletionException.class, () -> firstFailure.toCompletableFuture().join());
		assertEquals("PACKAGE_REGISTRY", api.status().lastDiagnosticCategory().orElseThrow());

		CompletionStage<?> reload = plugin.reloadConfiguration();
		awaitDone(reload);
		reload.toCompletableFuture().join();
		assertTrue(api.status().lastDiagnostic().isEmpty(),
				"full reload republishes the package registry and must clear its diagnostic");

		CompletionStage<?> repeatedFailure = plugin.createPackageAsync(
				new Package("starter", 0.0, List.of()));
		awaitDone(repeatedFailure);
		assertThrows(CompletionException.class,
				() -> repeatedFailure.toCompletableFuture().join());
		assertEquals("PACKAGE_REGISTRY", api.status().lastDiagnosticCategory().orElseThrow());

		CompletionStage<Boolean> succeeded = plugin.createPackageAsync(
				new Package("second", 0.0, List.of()));
		awaitDone(succeeded);
		assertTrue(succeeded.toCompletableFuture().join());
		assertTrue(api.status().lastDiagnostic().isEmpty());
		assertEquals(2, api.status().packageCount());
	}

	private Airdrop readyPlugin() throws Exception {
		Airdrop plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(plugin.getDataFolder().toPath());
		Files.writeString(
				plugin.getDataFolder().toPath().resolve("config.yml"),
				"language: en\neconomy:\n  enabled: false\nlogging:\n  debug: false\n",
				StandardCharsets.UTF_8);
		Files.writeString(
				plugin.getDataFolder().toPath().resolve("packages.yml"),
				"packages:\n  starter:\n    price: 0\n    items: []\n",
				StandardCharsets.UTF_8);
		server.getPluginManager().enablePlugin(plugin);
		awaitCondition(() -> Airdrop.isReady() || !plugin.isEnabled());
		assertTrue(plugin.isEnabled());
		assertTrue(Airdrop.isReady());
		return plugin;
	}

	private void awaitDone(CompletionStage<?> stage) {
		awaitCondition(() -> stage.toCompletableFuture().isDone());
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
		assertTrue(condition.getAsBoolean(), "Timed out waiting for asynchronous operation");
	}

	private static final class SecretStartupFailure extends RuntimeException {
	}
}
