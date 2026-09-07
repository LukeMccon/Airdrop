package com.airdropmc.helpers;

import com.airdropmc.Airdrop;
import com.airdropmc.Config;
import com.airdropmc.api.DeliveryStatus;
import com.airdropmc.api.DropRejectionReason;
import com.airdropmc.api.EconomyState;
import com.airdropmc.api.PackageRegistryCause;
import com.airdropmc.api.ReadinessState;
import com.airdropmc.api.AirdropApi;
import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.packages.PackageManager;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirdropLoggerTest {

	@AfterEach
	void resetConfiguration() throws Exception {
		setConfiguration(null);
	}

	@Test
	void typedDebugEventsIncludeOperationalSignalsAndSanitizeExternalLabels() throws Exception {
		setDebugEnabled(true);
		UUID requestId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
		List<String> messages = capture(() -> {
			AirdropLogger.debugReadiness(ReadinessState.STARTING, ReadinessState.READY);
			AirdropLogger.debugEconomy(
					EconomyState.ACTIVE,
					"§aVault\npassword=hunter2 /plugins/Vault/config.yml");
			AirdropLogger.debugPublication(
					AirdropLogger.Publication.CONFIGURATION,
					PackageRegistryCause.RELOAD,
					4L,
					3);
			AirdropLogger.debugPublication(
					AirdropLogger.Publication.PACKAGE_REGISTRY,
					PackageRegistryCause.UPDATE,
					5L,
					3);
			AirdropLogger.debugAdmission(
					requestId,
					AirdropLogger.AdmissionDecision.REJECTED,
					DropRejectionReason.FALLING_CAPACITY);
			AirdropLogger.debugRequest(
					requestId, AirdropLogger.RequestPhase.TERMINAL, DeliveryStatus.FAILED);
		});

		String output = String.join("\n", messages);
		for (String required : List.of(
				"readiness from=STARTING to=READY",
				"economy state=ACTIVE provider=Vault",
				"publication kind=CONFIGURATION cause=RELOAD revision=4 count=3",
				"publication kind=PACKAGE_REGISTRY cause=UPDATE revision=5 count=3",
				"admission request=" + requestId + " decision=REJECTED reason=FALLING_CAPACITY",
				"request request=" + requestId + " phase=TERMINAL reason=FAILED")) {
			assertTrue(output.contains(required), required + " missing from: " + output);
		}
		for (String forbidden : List.of(
				"hunter2", "/plugins/", "config.yml", "\npassword", "§")) {
			assertFalse(output.contains(forbidden), forbidden + " leaked in: " + output);
		}
	}

	@Test
	void debugEventsAreSilentWhenDebugLoggingIsDisabled() throws Exception {
		setDebugEnabled(false);

		List<String> messages = capture(() -> AirdropLogger.debugRequest(
				UUID.randomUUID(), AirdropLogger.RequestPhase.CREATED));

		assertEquals(List.of(), messages);
	}

	@Test
	void runtimeBoundariesEmitEachSupportedDebugCategory() throws Exception {
		ServerMock server = MockBukkit.mock();
		try {
			Airdrop plugin = (Airdrop) server.getPluginManager()
					.loadPlugin(Airdrop.class, new Object[0]);
			Files.createDirectories(plugin.getDataFolder().toPath());
			Files.writeString(
					plugin.getDataFolder().toPath().resolve("config.yml"),
					"language: en\neconomy:\n  enabled: false\nlogging:\n  debug: true\n",
					StandardCharsets.UTF_8);
			Files.writeString(
					plugin.getDataFolder().toPath().resolve("packages.yml"),
					"packages:\n  starter:\n    price: 0\n    items: []\n",
					StandardCharsets.UTF_8);

			List<String> messages = capture(plugin.getLogger(), () -> {
				server.getPluginManager().enablePlugin(plugin);
				await(server, () -> Airdrop.isReady() || !plugin.isEnabled());
				AirdropApi api = server.getServicesManager().load(AirdropApi.class);
				var world = server.addSimpleWorld("debug_world");
				DropHandle handle = api.requestSystemDrop(
						new Location(world, 4, 100, 6),
						"starter",
						DropRequestOptions.defaults()
								.withChickenCount(1)
								.withFlareEffects(false));
				handle.spawn().toCompletableFuture().join();
			});

			String output = String.join("\n", messages);
			for (String required : List.of(
					"publication kind=PACKAGE_REGISTRY cause=STARTUP revision=1 count=1",
					"publication kind=CONFIGURATION cause=STARTUP revision=1 count=1",
					"economy state=DISABLED provider=none",
					"readiness from=STARTING to=READY",
					"phase=CREATED",
					"phase=RESOLVED",
					"decision=ACCEPTED",
					"phase=SPAWNED")) {
				assertTrue(output.contains(required), required + " missing from: " + output);
			}
		} finally {
			CrateManager.clearAll();
			PackageManager.clear();
			MockBukkit.unmock();
		}
	}

	private List<String> capture(Runnable action) {
		return capture(Logger.getLogger(Airdrop.PLUGIN_NAME), action);
	}

	private List<String> capture(Logger logger, Runnable action) {
		boolean useParentHandlers = logger.getUseParentHandlers();
		Level level = logger.getLevel();
		List<String> messages = new ArrayList<>();
		Handler handler = new Handler() {
			@Override
			public void publish(LogRecord record) {
				messages.add(record.getMessage());
			}

			@Override
			public void flush() {
			}

			@Override
			public void close() {
			}
		};
		logger.setUseParentHandlers(false);
		logger.setLevel(Level.ALL);
		logger.addHandler(handler);
		try {
			action.run();
		} finally {
			logger.removeHandler(handler);
			logger.setUseParentHandlers(useParentHandlers);
			logger.setLevel(level);
		}
		return messages;
	}

	private void await(ServerMock server, java.util.function.BooleanSupplier condition) {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) {
				return;
			}
			server.getScheduler().performOneTick();
			LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
		}
		assertTrue(condition.getAsBoolean(), "Timed out waiting for runtime debug boundary");
	}

	private void setDebugEnabled(boolean enabled) throws Exception {
		YamlConfiguration configuration = new YamlConfiguration();
		configuration.set("logging.debug", enabled);
		setConfiguration(new Config(configuration));
	}

	private void setConfiguration(Config configuration) throws Exception {
		Field field = Airdrop.class.getDeclaredField("configuration");
		field.setAccessible(true);
		field.set(null, configuration);
	}
}
