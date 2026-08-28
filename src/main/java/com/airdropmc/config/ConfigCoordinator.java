package com.airdropmc.config;

import com.airdropmc.Airdrop;
import com.airdropmc.economy.EconomyProviderRefreshResult;
import com.airdropmc.lang.LanguageManager;
import com.airdropmc.packages.Package;
import com.airdropmc.packages.PackageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Serializes configuration I/O through one worker and publishes prepared state on the server thread.
 */
public final class ConfigCoordinator implements AutoCloseable {

	private static final String CONFIG_FILE = "config.yml";
	private static final String PACKAGES_FILE = "packages.yml";
	private static final String CONFIG_RESOURCE = "config.yml";

	private final Airdrop plugin;
	private final LanguageManager languageManager;
	private final ConfigFileStore store;
	private final ExecutorService executor;
	private final MainThreadDispatcher mainThread;
	private final Function<ConfigurationCandidate, EconomyProviderRefreshResult> configurationCommit;
	private final Consumer<PackageCandidate> packageCommit;
	private final Object queueLock = new Object();
	private final ArrayDeque<QueuedOperation<?>> queue = new ArrayDeque<>();

	private QueuedOperation<?> active;
	private boolean closed;
	private long generation;

	public ConfigCoordinator(
			Airdrop plugin,
			LanguageManager languageManager,
			Function<ConfigurationCandidate, EconomyProviderRefreshResult> configurationCommit,
			Consumer<PackageCandidate> packageCommit) {
		this(
				plugin,
				languageManager,
				new ConfigFileStore(),
				Executors.newSingleThreadExecutor(task -> {
					Thread thread = new Thread(task, "Airdrop-Configuration");
					thread.setDaemon(true);
					return thread;
				}),
				task -> Bukkit.getScheduler().runTask(plugin, task),
				configurationCommit,
				packageCommit);
	}

	ConfigCoordinator(
			Airdrop plugin,
			LanguageManager languageManager,
			ConfigFileStore store,
			ExecutorService executor,
			MainThreadDispatcher mainThread,
			Function<ConfigurationCandidate, EconomyProviderRefreshResult> configurationCommit,
			Consumer<PackageCandidate> packageCommit) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.languageManager = Objects.requireNonNull(languageManager, "languageManager");
		this.store = Objects.requireNonNull(store, "store");
		this.executor = Objects.requireNonNull(executor, "executor");
		this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
		this.configurationCommit = Objects.requireNonNull(configurationCommit, "configurationCommit");
		this.packageCommit = Objects.requireNonNull(packageCommit, "packageCommit");
	}

	public CompletionStage<EconomyProviderRefreshResult> startup() {
		return enqueue(() -> prepareConfiguration(true));
	}

	public CompletionStage<EconomyProviderRefreshResult> reload() {
		return enqueue(() -> prepareConfiguration(false));
	}

	public CompletionStage<Boolean> createPackage(Package pkg) {
		Objects.requireNonNull(pkg, "pkg");
		Package detachedPackage = new Package(pkg.getName(), pkg.getPrice(), pkg.getItems());
		Set<String> controlItemNames = Set.copyOf(languageManager.getControlItemNames());
		return enqueue(() -> {
			YamlConfiguration candidate = PackageManager.createPackageCandidate(
					readPackages(), detachedPackage, controlItemNames);
			Map<String, Package> materialized = PackageManager.materializePackages(
					candidate, controlItemNames);
			store.write(packagesPath(), candidate);
			PackageCandidate prepared = new PackageCandidate(candidate, materialized, true);
			return () -> {
				packageCommit.accept(prepared);
				return true;
			};
		});
	}

	public CompletionStage<Boolean> updatePackageInventory(String packageName, List<ItemStack> items) {
		String detachedName = Objects.requireNonNull(packageName, "packageName");
		List<ItemStack> detachedItems = cloneItems(items);
		Set<String> controlItemNames = Set.copyOf(languageManager.getControlItemNames());
		return enqueue(() -> {
			YamlConfiguration candidate = PackageManager.updatePackageInventoryCandidate(
					readPackages(), detachedName, detachedItems, controlItemNames);
			Map<String, Package> materialized = PackageManager.materializePackages(
					candidate, controlItemNames);
			store.write(packagesPath(), candidate);
			PackageCandidate prepared = new PackageCandidate(candidate, materialized, false);
			return () -> {
				packageCommit.accept(prepared);
				return true;
			};
		});
	}

	public CompletionStage<Boolean> deletePackage(String packageName) {
		String detachedName = Objects.requireNonNull(packageName, "packageName");
		Set<String> controlItemNames = Set.copyOf(languageManager.getControlItemNames());
		return enqueue(() -> {
			YamlConfiguration candidate = PackageManager.deletePackageCandidate(
					readPackages(), detachedName, controlItemNames);
			Map<String, Package> materialized = PackageManager.materializePackages(
					candidate, controlItemNames);
			store.write(packagesPath(), candidate);
			PackageCandidate prepared = new PackageCandidate(candidate, materialized, true);
			return () -> {
				packageCommit.accept(prepared);
				return true;
			};
		});
	}

	private Commit<EconomyProviderRefreshResult> prepareConfiguration(boolean startup) throws Exception {
		YamlConfiguration defaultConfig = readResource(CONFIG_RESOURCE);
		YamlConfiguration mainConfig = readOrProvision(configPath(), defaultConfig, startup);
		mainConfig.setDefaults(defaultConfig);

		YamlConfiguration packagesConfig;
		try {
			packagesConfig = readPackages();
		} catch (NoSuchFileException missing) {
			if (!startup) {
				throw missing;
			}
			packagesConfig = defaultPackages();
			store.write(packagesPath(), packagesConfig);
		}

		String languageCode = ConfigKeys.getLanguage(mainConfig);
		LanguageManager.LanguageCandidate language = languageManager.prepareLanguage(languageCode);
		Map<String, Package> packages = PackageManager.materializePackages(
				packagesConfig,
				language.controlItemNames());
		boolean economyEnabled = ConfigKeys.isEconomyEnabled(mainConfig);
		ConfigurationCandidate candidate = new ConfigurationCandidate(
				mainConfig, packagesConfig, packages, language, economyEnabled, startup);
		return () -> configurationCommit.apply(candidate);
	}

	private YamlConfiguration readOrProvision(
			Path path, YamlConfiguration defaultConfig, boolean startup)
			throws IOException, InvalidConfigurationException {
		try {
			return store.read(path);
		} catch (NoSuchFileException missing) {
			if (!startup) {
				throw missing;
			}
			YamlConfiguration candidate = copy(defaultConfig);
			store.write(path, candidate);
			return candidate;
		}
	}

	private YamlConfiguration readPackages() throws IOException, InvalidConfigurationException {
		return store.read(packagesPath());
	}

	private YamlConfiguration readResource(String resource)
			throws IOException, InvalidConfigurationException {
		try (InputStream input = plugin.getResource(resource)) {
			if (input == null) {
				throw new IOException("Missing bundled resource: " + resource);
			}
			YamlConfiguration configuration = new YamlConfiguration();
			configuration.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));
			return configuration;
		}
	}

	private static YamlConfiguration copy(FileConfiguration source) throws InvalidConfigurationException {
		YamlConfiguration copy = new YamlConfiguration();
		copy.loadFromString(source.saveToString());
		return copy;
	}

	private static YamlConfiguration defaultPackages() {
		YamlConfiguration configuration = new YamlConfiguration();
		configuration.createSection(PackageManager.PACKAGES_SECTION + ".starter");
		configuration.set(PackageManager.PACKAGES_SECTION + ".starter.items", List.of(
				new ItemStack(Material.IRON_HELMET, 1),
				new ItemStack(Material.IRON_CHESTPLATE, 1),
				new ItemStack(Material.IRON_LEGGINGS, 1),
				new ItemStack(Material.IRON_BOOTS, 1),
				new ItemStack(Material.BREAD, 2)));
		configuration.set(PackageManager.PACKAGES_SECTION + ".starter.price", 10.0);
		return configuration;
	}

	private Path configPath() {
		return plugin.getDataFolder().toPath().resolve(CONFIG_FILE);
	}

	private Path packagesPath() {
		return plugin.getDataFolder().toPath().resolve(PACKAGES_FILE);
	}

	private static List<ItemStack> cloneItems(List<ItemStack> items) {
		if (items == null || items.isEmpty()) {
			return List.of();
		}
		List<ItemStack> copy = new ArrayList<>(items.size());
		for (ItemStack item : items) {
			copy.add(item == null ? null : item.clone());
		}
		return copy;
	}

	private <T> CompletionStage<T> enqueue(Preparation<T> preparation) {
		QueuedOperation<T> operation = new QueuedOperation<>(preparation);
		QueuedOperation<?> dispatch = null;
		synchronized (queueLock) {
			if (closed) {
				return CompletableFuture.failedFuture(
						new CancellationException("Configuration coordinator is closed"));
			}
			queue.addLast(operation);
			if (active == null) {
				active = queue.removeFirst();
				dispatch = active;
			}
		}
		if (dispatch != null) {
			dispatch(dispatch);
		}
		return operation.future;
	}

	private void dispatch(QueuedOperation<?> operation) {
		long operationGeneration;
		synchronized (queueLock) {
			if (closed || active != operation) {
				return;
			}
			operationGeneration = generation;
		}
		try {
			executor.execute(() -> prepare(operation, operationGeneration));
		} catch (RuntimeException failure) {
			postResult(operation, operationGeneration, null, failure);
		}
	}

	private <T> void prepare(QueuedOperation<T> operation, long operationGeneration) {
		Commit<T> commit = null;
		Throwable failure = null;
		try {
			commit = operation.preparation.prepare();
		} catch (Throwable thrown) {
			failure = thrown;
		}
		postResult(operation, operationGeneration, commit, failure);
	}

	private <T> void postResult(
			QueuedOperation<T> operation,
			long operationGeneration,
			Commit<T> commit,
			Throwable failure) {
		try {
			mainThread.dispatch(() -> finish(operation, operationGeneration, commit, failure));
		} catch (RuntimeException schedulingFailure) {
			plugin.getLogger().log(Level.SEVERE,
					"Could not schedule configuration completion on the server thread; "
							+ "the operation will remain pending until shutdown",
					schedulingFailure);
		}
	}

	private <T> void finish(
			QueuedOperation<T> operation,
			long operationGeneration,
			Commit<T> commit,
			Throwable failure) {
		synchronized (queueLock) {
			if (closed || generation != operationGeneration || active != operation) {
				return;
			}
		}

		if (failure != null) {
			operation.future.completeExceptionally(failure);
		} else {
			try {
				operation.future.complete(commit.apply());
			} catch (Throwable thrown) {
				operation.future.completeExceptionally(thrown);
			}
		}
		advance(operation);
	}

	private void advance(QueuedOperation<?> completed) {
		QueuedOperation<?> dispatch = null;
		synchronized (queueLock) {
			if (closed || active != completed) {
				return;
			}
			active = queue.pollFirst();
			dispatch = active;
		}
		if (dispatch != null) {
			dispatch(dispatch);
		}
	}

	@Override
	public void close() {
		List<CompletableFuture<?>> cancelled = new ArrayList<>();
		synchronized (queueLock) {
			if (closed) {
				return;
			}
			closed = true;
			generation++;
			if (active != null) {
				cancelled.add(active.future);
				active = null;
			}
			while (!queue.isEmpty()) {
				cancelled.add(queue.removeFirst().future);
			}
		}
		executor.shutdownNow();
		for (CompletableFuture<?> future : cancelled) {
			future.completeExceptionally(new CancellationException("Configuration coordinator is closed"));
		}
	}

	public record ConfigurationCandidate(
			FileConfiguration configuration,
			FileConfiguration packagesConfiguration,
			Map<String, Package> packages,
			LanguageManager.LanguageCandidate language,
			boolean economyEnabled,
			boolean startup) {
		public ConfigurationCandidate {
			Objects.requireNonNull(configuration, "configuration");
			Objects.requireNonNull(packagesConfiguration, "packagesConfiguration");
			packages = Map.copyOf(Objects.requireNonNull(packages, "packages"));
			Objects.requireNonNull(language, "language");
		}
	}

	public record PackageCandidate(
			FileConfiguration configuration,
			Map<String, Package> packages,
			boolean refreshBrowser) {
		public PackageCandidate {
			Objects.requireNonNull(configuration, "configuration");
			packages = Map.copyOf(Objects.requireNonNull(packages, "packages"));
		}
	}

	@FunctionalInterface
	interface MainThreadDispatcher {
		void dispatch(Runnable task);
	}

	@FunctionalInterface
	private interface Preparation<T> {
		Commit<T> prepare() throws Exception;
	}

	@FunctionalInterface
	private interface Commit<T> {
		T apply() throws Exception;
	}

	private static final class QueuedOperation<T> {
		private final Preparation<T> preparation;
		private final CompletableFuture<T> future = new CompletableFuture<>();

		private QueuedOperation(Preparation<T> preparation) {
			this.preparation = Objects.requireNonNull(preparation, "preparation");
		}
	}
}
