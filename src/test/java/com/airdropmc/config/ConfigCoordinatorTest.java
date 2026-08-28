package com.airdropmc.config;

import com.airdropmc.Airdrop;
import com.airdropmc.economy.EconomyProviderRefreshResult;
import com.airdropmc.lang.LanguageManager;
import com.airdropmc.packages.Package;
import com.airdropmc.packages.PackageManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigCoordinatorTest {

	@TempDir
	Path temporaryDirectory;

	private ConfigCoordinator coordinator;

	@AfterEach
	void tearDown() {
		if (coordinator != null) {
			coordinator.close();
		}
		PackageManager.clear();
	}

	@Test
	void nextOperationWaitsForPriorMainThreadCommit() throws Exception {
		AtomicInteger reads = new AtomicInteger();
		CountDownLatch firstRead = new CountDownLatch(1);
		CountDownLatch secondRead = new CountDownLatch(1);
		BlockingQueue<Runnable> mainTasks = new LinkedBlockingQueue<>();
		coordinator = coordinator(reads, firstRead, secondRead, mainTasks, ignored -> { });

		CompletionStage<Boolean> first = coordinator.createPackage(pkg("first"));
		CompletionStage<Boolean> second = coordinator.createPackage(pkg("second"));

		assertTrue(firstRead.await(5, TimeUnit.SECONDS));
		Runnable firstCommit = mainTasks.poll(5, TimeUnit.SECONDS);
		assertNotNull(firstCommit);
		assertEquals(1, reads.get());
		assertFalse(secondRead.await(100, TimeUnit.MILLISECONDS));

		firstCommit.run();
		assertTrue(first.toCompletableFuture().get(5, TimeUnit.SECONDS));
		assertTrue(secondRead.await(5, TimeUnit.SECONDS));
		Runnable secondCommit = mainTasks.poll(5, TimeUnit.SECONDS);
		assertNotNull(secondCommit);
		secondCommit.run();
		assertTrue(second.toCompletableFuture().get(5, TimeUnit.SECONDS));

		YamlConfiguration persisted = new ConfigFileStore().read(packagesPath());
		assertEquals(3, PackageManager.materializePackages(persisted).size());
	}

	@Test
	void failedOperationDoesNotPoisonQueue() throws Exception {
		AtomicInteger reads = new AtomicInteger();
		CountDownLatch firstRead = new CountDownLatch(1);
		CountDownLatch secondRead = new CountDownLatch(1);
		BlockingQueue<Runnable> mainTasks = new LinkedBlockingQueue<>();
		coordinator = coordinator(reads, firstRead, secondRead, mainTasks, ignored -> { });

		CompletionStage<Boolean> duplicate = coordinator.createPackage(pkg("starter"));
		CompletionStage<Boolean> valid = coordinator.createPackage(pkg("valid"));

		Runnable failureCompletion = mainTasks.poll(5, TimeUnit.SECONDS);
		assertNotNull(failureCompletion);
		assertEquals(1, reads.get());
		failureCompletion.run();
		assertThrows(CompletionException.class, () -> duplicate.toCompletableFuture().join());

		assertTrue(secondRead.await(5, TimeUnit.SECONDS));
		Runnable successCommit = mainTasks.poll(5, TimeUnit.SECONDS);
		assertNotNull(successCommit);
		successCommit.run();
		assertTrue(valid.toCompletableFuture().get(5, TimeUnit.SECONDS));
	}

	@Test
	void closeRejectsPreparedLateCommit() throws Exception {
		AtomicInteger reads = new AtomicInteger();
		CountDownLatch firstRead = new CountDownLatch(1);
		CountDownLatch secondRead = new CountDownLatch(1);
		BlockingQueue<Runnable> mainTasks = new LinkedBlockingQueue<>();
		AtomicInteger publications = new AtomicInteger();
		coordinator = coordinator(reads, firstRead, secondRead, mainTasks,
				ignored -> publications.incrementAndGet());

		CompletionStage<Boolean> pending = coordinator.createPackage(pkg("late"));
		Runnable lateCommit = mainTasks.poll(5, TimeUnit.SECONDS);
		assertNotNull(lateCommit);

		coordinator.close();
		lateCommit.run();

		assertThrows(java.util.concurrent.CancellationException.class,
				() -> pending.toCompletableFuture().join());
		assertEquals(0, publications.get());
	}

	@Test
	void dispatchFailureLeavesOperationPendingUntilClose() throws Exception {
		AtomicInteger reads = new AtomicInteger();
		CountDownLatch firstRead = new CountDownLatch(1);
		CountDownLatch secondRead = new CountDownLatch(1);
		CountDownLatch dispatchAttempt = new CountDownLatch(1);
		AtomicReference<String> completionThread = new AtomicReference<>();
		ConfigFileStore store = store(reads, firstRead, secondRead, new AtomicReference<>());
		Files.writeString(packagesPath(), """
				packages:
				  starter:
				    price: 10.0
				    items: []
				""", StandardCharsets.UTF_8);

		Airdrop plugin = mock(Airdrop.class);
		when(plugin.getDataFolder()).thenReturn(temporaryDirectory.toFile());
		when(plugin.getLogger()).thenReturn(Logger.getLogger("ConfigCoordinatorTest"));
		ExecutorService executor = Executors.newSingleThreadExecutor(
				task -> new Thread(task, "config-test-worker"));
		coordinator = new ConfigCoordinator(
				plugin,
				mock(LanguageManager.class),
				store,
				executor,
				task -> {
					dispatchAttempt.countDown();
					throw new IllegalStateException("scheduler unavailable");
				},
				ignored -> EconomyProviderRefreshResult.disabled(),
				ignored -> { });

		CompletionStage<Boolean> first = coordinator.createPackage(pkg("first"));
		CompletionStage<Boolean> second = coordinator.createPackage(pkg("second"));
		first.whenComplete((ignored, failure) -> completionThread.set(Thread.currentThread().getName()));

		assertTrue(firstRead.await(5, TimeUnit.SECONDS));
		assertTrue(dispatchAttempt.await(5, TimeUnit.SECONDS));
		assertFalse(first.toCompletableFuture().isDone());
		assertFalse(secondRead.await(100, TimeUnit.MILLISECONDS));

		String closingThread = Thread.currentThread().getName();
		coordinator.close();

		assertThrows(java.util.concurrent.CancellationException.class,
				() -> first.toCompletableFuture().join());
		assertThrows(java.util.concurrent.CancellationException.class,
				() -> second.toCompletableFuture().join());
		assertEquals(closingThread, completionThread.get());
	}

	@Test
	void diskWorkRunsOffCommitThreadAndFutureCompletesAfterPublication() throws Exception {
		AtomicInteger reads = new AtomicInteger();
		CountDownLatch firstRead = new CountDownLatch(1);
		CountDownLatch secondRead = new CountDownLatch(1);
		BlockingQueue<Runnable> mainTasks = new LinkedBlockingQueue<>();
		AtomicReference<String> readThread = new AtomicReference<>();
		AtomicReference<String> publishThread = new AtomicReference<>();
		AtomicReference<Boolean> publishedBeforeCompletion = new AtomicReference<>(false);

		ConfigFileStore store = store(reads, firstRead, secondRead, readThread);
		coordinator = coordinator(store, mainTasks, ignored -> publishThread.set(Thread.currentThread().getName()));
		CompletionStage<Boolean> stage = coordinator.createPackage(pkg("threaded"));
		stage.whenComplete((result, failure) -> publishedBeforeCompletion.set(publishThread.get() != null));

		Runnable commit = mainTasks.poll(5, TimeUnit.SECONDS);
		assertNotNull(commit);
		String commitThread = Thread.currentThread().getName();
		commit.run();

		assertTrue(stage.toCompletableFuture().get(5, TimeUnit.SECONDS));
		assertNotNull(readThread.get());
		assertFalse(commitThread.equals(readThread.get()));
		assertEquals(commitThread, publishThread.get());
		assertTrue(publishedBeforeCompletion.get());
	}

	private ConfigCoordinator coordinator(
			AtomicInteger reads,
			CountDownLatch firstRead,
			CountDownLatch secondRead,
			BlockingQueue<Runnable> mainTasks,
			java.util.function.Consumer<ConfigCoordinator.PackageCandidate> packageCommit)
			throws Exception {
		return coordinator(store(reads, firstRead, secondRead, new AtomicReference<>()),
				mainTasks, packageCommit);
	}

	private ConfigCoordinator coordinator(
			ConfigFileStore store,
			BlockingQueue<Runnable> mainTasks,
			java.util.function.Consumer<ConfigCoordinator.PackageCandidate> packageCommit)
			throws Exception {
		Files.createDirectories(temporaryDirectory);
		Files.writeString(packagesPath(), """
				packages:
				  starter:
				    price: 10.0
				    items: []
				""", StandardCharsets.UTF_8);

		Airdrop plugin = mock(Airdrop.class);
		when(plugin.getDataFolder()).thenReturn(temporaryDirectory.toFile());
		LanguageManager languageManager = mock(LanguageManager.class);
		ExecutorService executor = Executors.newSingleThreadExecutor(task -> new Thread(task, "config-test-worker"));
		return new ConfigCoordinator(
				plugin,
				languageManager,
				store,
				executor,
				mainTasks::add,
				ignored -> EconomyProviderRefreshResult.disabled(),
				packageCommit);
	}

	private ConfigFileStore store(
			AtomicInteger reads,
			CountDownLatch firstRead,
			CountDownLatch secondRead,
			AtomicReference<String> readThread) {
		return new ConfigFileStore(
				path -> {
					readThread.set(Thread.currentThread().getName());
					int count = reads.incrementAndGet();
					if (count == 1) {
						firstRead.countDown();
					} else if (count == 2) {
						secondRead.countDown();
					}
					return Files.readString(path, StandardCharsets.UTF_8);
				},
				(path, yaml) -> Files.writeString(path, yaml, StandardCharsets.UTF_8),
				(temporary, target, atomic) -> {
					if (atomic) {
						Files.move(temporary, target,
								StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
					} else {
						Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
					}
				});
	}

	private Path packagesPath() {
		return temporaryDirectory.resolve("packages.yml");
	}

	private static Package pkg(String name) {
		return new Package(name, 1.0, List.of());
	}
}
