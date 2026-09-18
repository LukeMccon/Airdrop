package com.airdropmc.docs;

import com.airdropmc.api.AirdropApi;
import com.airdropmc.api.AirdropVersions;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OptionalIntegrationExampleTest {
	private static final Path FIXTURE_JAR = Path.of(
			"consumer-fixture", "build", "libs", "airdrop-consumer-fixture.jar");
	private static final String ENTRY_POINT = "dev.airdropmc.example.ExamplePlugin";
	private final Server server = mock(Server.class);
	private final PluginManager plugins = mock(PluginManager.class);
	private final ServicesManager services = mock(ServicesManager.class);
	private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
	private final Logger logger = mock(Logger.class);

	@Test
	void consumerEnablesWithoutAnyAirdropClassesAvailable() throws Exception {
		try (URLClassLoader loader = exampleClassLoader(false)) {
			assertThrows(ClassNotFoundException.class,
					() -> loader.loadClass("com.airdropmc.api.AirdropApi"));
			JavaPlugin consumer = consumer(loader, false);

			assertDoesNotThrow(consumer::onEnable);
			verifyNoInteractions(services, scheduler, logger);
		}
	}

	@Test
	void enabledAirdropWithoutAServiceSkipsTheIntegration() throws Exception {
		try (URLClassLoader loader = exampleClassLoader(true)) {
			JavaPlugin consumer = consumer(loader, true);

			assertDoesNotThrow(consumer::onEnable);

			verify(services).load(AirdropApi.class);
			verifyNoInteractions(scheduler, logger);
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void readyServiceSchedulesItsCallbackEvenWhenAlreadyComplete(boolean alreadyReady) throws Exception {
		try (URLClassLoader loader = exampleClassLoader(true)) {
			JavaPlugin consumer = consumer(loader, true);
			AirdropApi api = mock(AirdropApi.class);
			CompletableFuture<AirdropApi> readiness = new CompletableFuture<>();
			when(services.load(AirdropApi.class)).thenReturn(api);
			when(api.readiness()).thenReturn(readiness.minimalCompletionStage());
			when(api.versions()).thenReturn(new AirdropVersions("4.1.0", "1.0.0", "1.21.11", "21"));
			if (alreadyReady) {
				readiness.complete(api);
			}

			consumer.onEnable();
			if (!alreadyReady) {
				verifyNoInteractions(scheduler, logger);
				readiness.complete(api);
			}

			ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
			verify(scheduler).runTask(eq(consumer), callback.capture());
			verifyNoInteractions(logger);
			callback.getValue().run();
			verify(logger).info("Airdrop API 1.0.0 is ready");
		}
	}

	@Test
	void readinessFailureSchedulesTheWarningWithoutUsingTheProvider() throws Exception {
		try (URLClassLoader loader = exampleClassLoader(true)) {
			JavaPlugin consumer = consumer(loader, true);
			AirdropApi api = mock(AirdropApi.class);
			when(services.load(AirdropApi.class)).thenReturn(api);
			when(api.readiness()).thenReturn(CompletableFuture.failedStage(
					new IllegalStateException("Startup failed")));

			consumer.onEnable();

			ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
			verify(scheduler).runTask(eq(consumer), callback.capture());
			verifyNoInteractions(logger);
			assertDoesNotThrow(() -> callback.getValue().run());
			verify(logger).warning("Airdrop did not become ready");
		}
	}

	@Test
	void bothGuidesUseTheExactCompiledExample() throws IOException {
		for (String guide : List.of("modrinth.md", "migration-4.1.md")) {
			String documentation = Files.readString(Path.of("docs", guide));
			for (String sourceFile : List.of("ExamplePlugin.java", "AirdropIntegration.java")) {
				String source = Files.readString(Path.of("consumer-fixture", "src", "main", "java",
						"dev", "airdropmc", "example", sourceFile));
				assertTrue(documentation.contains("<!-- optional-example:" + sourceFile + " -->\n"
						+ "```java\n" + source + "```"),
						guide + " must include the compiled " + sourceFile + " example verbatim");
			}
		}
	}

	private URLClassLoader exampleClassLoader(boolean apiAvailable) throws IOException {
		return new URLClassLoader(new URL[]{FIXTURE_JAR.toUri().toURL()}, getClass().getClassLoader()) {
			@Override
			protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
				if (!apiAvailable && name.startsWith("com.airdropmc.")) {
					throw new ClassNotFoundException(name);
				}
				return super.loadClass(name, resolve);
			}
		};
	}

	private JavaPlugin consumer(ClassLoader loader, boolean airdropEnabled) throws ClassNotFoundException {
		JavaPlugin consumer = mock(loader.loadClass(ENTRY_POINT).asSubclass(JavaPlugin.class),
				CALLS_REAL_METHODS);
		when(consumer.getServer()).thenReturn(server);
		when(consumer.getLogger()).thenReturn(logger);
		when(server.getPluginManager()).thenReturn(plugins);
		when(server.getServicesManager()).thenReturn(services);
		when(server.getScheduler()).thenReturn(scheduler);
		when(plugins.isPluginEnabled("Airdrop")).thenReturn(airdropEnabled);
		return consumer;
	}
}
