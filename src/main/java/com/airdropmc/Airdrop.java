package com.airdropmc;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

import com.airdropmc.config.ConfigCoordinator;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.economy.EconomyProviderDiscovery;
import com.airdropmc.economy.EconomyProviderRefreshResult;
import com.airdropmc.helpers.AirdropLogger;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.integrations.OptionalIntegrations;
import com.airdropmc.internal.api.AirdropServiceLifecycle;
import com.airdropmc.internal.api.AirdropVersionMetadata;
import com.airdropmc.internal.diagnostics.AirdropDiagnostics;
import com.airdropmc.lang.LanguageManager;
import com.airdropmc.listeners.CrateDestroyListener;
import com.airdropmc.listeners.CrateCloseListener;
import com.airdropmc.listeners.CrateCleanupListener;
import com.airdropmc.listeners.CrateHopperListener;
import com.airdropmc.listeners.CrateOpenListener;
import com.airdropmc.listeners.EconomyProviderListener;
import com.airdropmc.listeners.FallingCrateListener;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.packages.Package;
import com.airdropmc.packages.PackageGui;
import com.airdropmc.packages.PackageManager;
import com.airdropmc.packages.PackagesGui;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;

import com.airdropmc.commands.CmdAirdrop;

/**
 * Main plugin class
 */
@ApiStatus.Internal
public class Airdrop extends JavaPlugin {

	public static final String PLUGIN_NAME = "Airdrop";
	public static final String AIRDROP_COMMAND = "airdrop";
	private static Airdrop pluginInstance;
	private static String pluginVersion;
	private static String paperCompatibilityVersion;
	private static PackagesGui packagesGui;
	private static volatile EconomyProvider economyProvider;
	private static volatile Config configuration;
	private static volatile PackagesConfig packagesConfiguration;
	private static DropAdmissionController dropAdmissionController;
	private static volatile boolean shuttingDown;
	private static volatile boolean ready;
	private LanguageManager languageManager;
	private ConfigCoordinator configurationCoordinator;
	private AirdropServiceLifecycle airdropServiceLifecycle;

	@Override
	public void onEnable() {
		shuttingDown = false;
		ready = false;
		PluginDescriptionFile pdf = this.getDescription();

		pluginInstance = this;
		pluginVersion = pdf.getVersion();
		paperCompatibilityVersion = pdf.getAPIVersion();
		dropAdmissionController = new DropAdmissionController();

		try {
			airdropServiceLifecycle = new AirdropServiceLifecycle(this);
			airdropServiceLifecycle.register();

			languageManager = new LanguageManager(this);
			ChatHandler.init(languageManager);

			Objects.requireNonNull(this.getCommand(AIRDROP_COMMAND)).setExecutor(new CmdAirdrop());
			Objects.requireNonNull(this.getCommand(AIRDROP_COMMAND)).setTabCompleter(new AirdropTabCompleter());

			Bukkit.getPluginManager().registerEvents(new EconomyProviderListener(this), this);
			Bukkit.getPluginManager().registerEvents(new FallingCrateListener(), this);
			Bukkit.getPluginManager().registerEvents(new CrateCloseListener(), this);
			Bukkit.getPluginManager().registerEvents(new CrateHopperListener(this), this);
			Bukkit.getPluginManager().registerEvents(new CrateOpenListener(), this);
			Bukkit.getPluginManager().registerEvents(new CrateDestroyListener(this), this);
			Bukkit.getPluginManager().registerEvents(new CrateCleanupListener(), this);

			configurationCoordinator = new ConfigCoordinator(
					this,
					languageManager,
					this::commitConfiguration,
					this::commitPackages);
			configurationCoordinator.startup().whenComplete((ignored, failure) -> {
				if (failure != null) {
					failStartup(unwrap(failure));
				}
			});
		} catch (RuntimeException | LinkageError failure) {
			failStartup(failure);
		}
	}

	@Override
	public void onDisable() {
		shuttingDown = true;
		ready = false;
		AirdropServiceLifecycle serviceLifecycle = airdropServiceLifecycle;
		airdropServiceLifecycle = null;
		runDisableStep("unregister Airdrop extension service", () -> {
			if (serviceLifecycle != null) {
				serviceLifecycle.stop();
			}
		});
		runDisableStep("close package editors", PackageGui::closeOpenEditors);
		runDisableStep("close package browser", () -> {
			if (packagesGui != null) {
				packagesGui.closeAndUnregister();
			}
		});
		ConfigCoordinator coordinator = configurationCoordinator;
		configurationCoordinator = null;
		runDisableStep("close configuration coordinator", () -> {
			if (coordinator != null) {
				coordinator.close();
			}
		});

		DropAdmissionController admission = dropAdmissionController;
		try {
			if (admission != null) {
				admission.stopAccepting();
			}
			if (Bukkit.isStopping()) {
				CrateManager.prepareForShutdown(this);
			} else {
				CrateManager.purgeForHotDisable(this);
			}
		} catch (RuntimeException failure) {
			try {
				AirdropLogger.log(Level.SEVERE, "Could not complete crate shutdown cleanup", failure);
			} catch (RuntimeException ignored) {
				// Continue the remaining disable cleanup even if logging is unavailable.
			}
		} finally {
			runDisableStep("clear drop admission", () -> {
				if (admission != null) {
					admission.clear();
				}
			});
			runDisableStep("cancel plugin tasks", () -> Bukkit.getScheduler().cancelTasks(this));
			runDisableStep("clear packages", PackageManager::clear);
			runDisableStep("unregister package GUI", () -> {
				if (packagesGui != null) {
					packagesGui.closeAndUnregister();
				}
			});
			runDisableStep("unregister plugin listeners", () -> HandlerList.unregisterAll(this));
			packagesGui = null;
			pluginInstance = null;
			pluginVersion = null;
			paperCompatibilityVersion = null;
			economyProvider = null;
			configuration = null;
			packagesConfiguration = null;
			dropAdmissionController = null;
		}
	}

	private void runDisableStep(String description, Runnable action) {
		try {
			action.run();
		} catch (RuntimeException failure) {
			try {
				getLogger().log(Level.WARNING, "Could not " + description, failure);
			} catch (RuntimeException ignored) {
				// Disable must continue even if the logger is also unavailable.
			}
		}
	}

	private EconomyProviderRefreshResult commitConfiguration(ConfigCoordinator.ConfigurationCandidate candidate) {
		EconomySelection selection = selectEconomyProvider(candidate.economyEnabled());
		Config replacementConfiguration = new Config(candidate.configuration());
		PackagesConfig replacementPackagesConfiguration = new PackagesConfig(candidate.packagesConfiguration());

		configuration = replacementConfiguration;
		packagesConfiguration = replacementPackagesConfiguration;
		languageManager.publishLanguage(candidate.language());
		ChatHandler.init(languageManager);
		OptionalIntegrations.State optionalIntegrations = candidate.startup()
				? initializeStartupIntegrations()
				: null;
		PackageManager.publishPackages(candidate.packages());
		AirdropServiceLifecycle serviceLifecycle = airdropServiceLifecycle;
		if (serviceLifecycle != null) {
			serviceLifecycle.publishLimits(ConfigKeys.getDropLimitSettings());
			long revision = serviceLifecycle.publishPackages(
					candidate.packages(), candidate.cause());
			AirdropLogger.debugPublication(
					AirdropLogger.Publication.CONFIGURATION,
					candidate.cause(),
					revision,
					candidate.packages().size());
		}
		publishEconomyProvider(selection);

		refreshPackageBrowser();
		if (candidate.startup()) {
			ready = true;
			serviceLifecycle = airdropServiceLifecycle;
			if (serviceLifecycle != null) {
				serviceLifecycle.publishReady(selection.result(), optionalIntegrations);
			}
		}
		return selection.result();
	}

	private void commitPackages(ConfigCoordinator.PackageCandidate candidate) {
		packagesConfiguration = new PackagesConfig(candidate.configuration());
		PackageManager.publishPackages(candidate.packages());
		AirdropServiceLifecycle serviceLifecycle = airdropServiceLifecycle;
		if (serviceLifecycle != null) {
			serviceLifecycle.publishPackages(candidate.packages(), candidate.cause());
		}
		if (candidate.refreshBrowser() && packagesGui != null) {
			try {
				packagesGui.initializeItems();
			} catch (RuntimeException failure) {
				getLogger().log(Level.WARNING, "Could not refresh the packages GUI", failure);
			}
		}
	}

	private void refreshPackageBrowser() {
		try {
			setupPackageGuis();
		} catch (RuntimeException failure) {
			getLogger().log(Level.WARNING, "Could not refresh the packages GUI", failure);
		}
	}

	private OptionalIntegrations.State initializeStartupIntegrations() {
		CrateManager.recoverLoadedCrates(this, dropAdmissionController);
		return OptionalIntegrations.initialize(getServer());
	}

	public CompletionStage<EconomyProviderRefreshResult> reloadConfiguration() {
		ConfigCoordinator coordinator = configurationCoordinator;
		if (coordinator == null || shuttingDown || !ready) {
			return unavailableStage();
		}
		return observeOperation(
				coordinator.reload(),
				AirdropDiagnostics.Category.CONFIGURATION,
				AirdropDiagnostics.Category.CONFIGURATION,
				AirdropDiagnostics.Category.PACKAGE_REGISTRY);
	}

	public CompletionStage<Boolean> createPackageAsync(Package pkg) {
		ConfigCoordinator coordinator = configurationCoordinator;
		if (coordinator == null || shuttingDown || !ready) {
			return unavailableStage();
		}
		return observeOperation(
				coordinator.createPackage(pkg),
				AirdropDiagnostics.Category.PACKAGE_REGISTRY,
				AirdropDiagnostics.Category.PACKAGE_REGISTRY);
	}

	public CompletionStage<Boolean> updatePackageInventoryAsync(String packageName, List<ItemStack> items) {
		ConfigCoordinator coordinator = configurationCoordinator;
		if (coordinator == null || shuttingDown || !ready) {
			return unavailableStage();
		}
		return observeOperation(
				coordinator.updatePackageInventory(packageName, items),
				AirdropDiagnostics.Category.PACKAGE_REGISTRY,
				AirdropDiagnostics.Category.PACKAGE_REGISTRY);
	}

	public CompletionStage<Boolean> deletePackageAsync(String packageName) {
		ConfigCoordinator coordinator = configurationCoordinator;
		if (coordinator == null || shuttingDown || !ready) {
			return unavailableStage();
		}
		return observeOperation(
				coordinator.deletePackage(packageName),
				AirdropDiagnostics.Category.PACKAGE_REGISTRY,
				AirdropDiagnostics.Category.PACKAGE_REGISTRY);
	}

	private <T> CompletionStage<T> observeOperation(
			CompletionStage<T> operation,
			AirdropDiagnostics.Category failureCategory,
			AirdropDiagnostics.Category... successfulPublications) {
		return operation.whenComplete((result, failure) -> {
			AirdropServiceLifecycle serviceLifecycle = airdropServiceLifecycle;
			if (serviceLifecycle == null) {
				return;
			}
			if (failure == null) {
				for (AirdropDiagnostics.Category category : successfulPublications) {
					serviceLifecycle.clearDiagnostic(category);
				}
			} else {
				serviceLifecycle.recordDiagnostic(failureCategory, unwrap(failure));
			}
		});
	}

	private static <T> CompletionStage<T> unavailableStage() {
		return CompletableFuture.failedFuture(
				new IllegalStateException("Airdrop configuration is not available"));
	}

	public EconomyProviderRefreshResult refreshEconomyProvider() {
		EconomySelection selection = selectEconomyProvider(ConfigKeys.isEconomyEnabled());
		publishEconomyProvider(selection);
		return selection.result();
	}

	private EconomySelection selectEconomyProvider(boolean enabled) {
		EconomyProvider replacement = null;
		EconomyProviderRefreshResult result;
		Throwable failure = null;
		try {
			if (!enabled) {
				result = EconomyProviderRefreshResult.disabled();
			} else {
				replacement = EconomyProviderDiscovery.discover(getServer().getServicesManager()).orElse(null);
				result = replacement == null
						? EconomyProviderRefreshResult.unavailable()
						: EconomyProviderRefreshResult.active(
								AirdropDiagnostics.sanitizeLabel(providerName(replacement)));
			}
		} catch (LinkageError | RuntimeException exception) {
			replacement = null;
			result = EconomyProviderRefreshResult.unavailable();
			failure = exception;
		}
		return new EconomySelection(replacement, result, failure);
	}

	private void publishEconomyProvider(EconomySelection selection) {
		economyProvider = selection.provider();
		AirdropServiceLifecycle serviceLifecycle = airdropServiceLifecycle;
		if (serviceLifecycle != null) {
			serviceLifecycle.publishEconomy(selection.result());
		}
		com.airdropmc.api.EconomyState economyState = switch (selection.result().outcome()) {
			case ACTIVE -> com.airdropmc.api.EconomyState.ACTIVE;
			case DISABLED -> com.airdropmc.api.EconomyState.DISABLED;
			case UNAVAILABLE -> com.airdropmc.api.EconomyState.UNAVAILABLE;
		};
		AirdropLogger.debugEconomy(economyState, selection.result().providerName());
		switch (selection.result().outcome()) {
			case ACTIVE -> AirdropLogger.info("Using economy provider: " + selection.result().providerName());
			case DISABLED -> AirdropLogger.info("Economy support is disabled");
			case UNAVAILABLE -> {
				String message = "No economy provider is available; paid drops are blocked";
				if (selection.failure() == null) {
					AirdropLogger.warning(message);
				} else {
					AirdropLogger.log(Level.WARNING, message, selection.failure());
				}
			}
		}
	}

	private void failStartup(Throwable failure) {
		if (shuttingDown || pluginInstance != this) {
			return;
		}
		AirdropServiceLifecycle serviceLifecycle = airdropServiceLifecycle;
		if (serviceLifecycle != null) {
			serviceLifecycle.publishFailure(failure);
		}
		getLogger().log(Level.SEVERE,
				"Airdrop startup failed; the plugin will be disabled", failure);
		Bukkit.getPluginManager().disablePlugin(this);
	}

	private static String providerName(EconomyProvider provider) {
		try {
			String name = provider.getName();
			if (name != null && !name.isBlank()) {
				return name;
			}
		} catch (LinkageError | RuntimeException ignored) {
			// The provider is still usable even if it cannot supply a display name.
		}
		String fallback = provider.getClass().getSimpleName();
		return fallback.isBlank() ? "unknown" : fallback;
	}

	private static Throwable unwrap(Throwable failure) {
		Throwable current = failure;
		while ((current instanceof java.util.concurrent.CompletionException
				|| current instanceof java.util.concurrent.ExecutionException)
				&& current.getCause() != null) {
			current = current.getCause();
		}
		return current;
	}

	public void setupPackageGuis() {
		if (packagesGui != null) {
			packagesGui.closeAndUnregister();
		}
		packagesGui = new PackagesGui();
	}

	public static Airdrop getPluginInstance() {
		return pluginInstance;
	}

	public static void setPluginInstance(Airdrop pluginInstance) {
		Airdrop.pluginInstance = pluginInstance;
	}

	/**
	 * @deprecated This value describes Paper compatibility, not Airdrop's extension API.
	 *             Use {@link #getPaperApiVersion()}.
	 */
	@Deprecated
	public static String getPluginApiVersion() {
		return getPaperApiVersion();
	}

	/**
	 * Returns the Paper API compatibility declared in the generated plugin metadata.
	 *
	 * @return declared Paper API compatibility, or {@code null} while disabled
	 */
	public static String getPaperApiVersion() {
		return paperCompatibilityVersion;
	}

	/** Returns the independently versioned supported extension API contract. */
	public static String getExtensionApiVersion() {
		return AirdropVersionMetadata.versions().extensionApiVersion();
	}

	/** Returns the Java feature version required by this build. */
	public static String getJavaCompatibilityVersion() {
		return AirdropVersionMetadata.versions().javaVersion();
	}

	public static PackagesGui getPackagesGui() {
		return packagesGui;
	}

	public static EconomyProvider getEconomyProvider() {
		return economyProvider;
	}

	public static String getVersion() {
		return pluginVersion;
	}

	public static Config getConfiguration() {
		return configuration;
	}

	public static PackagesConfig getPackagesConfiguration() {
		return packagesConfiguration;
	}

	public static DropAdmissionController getDropAdmissionController() {
		return dropAdmissionController;
	}

	public static boolean isShuttingDown() {
		return shuttingDown;
	}

	public static boolean isReady() {
		return ready;
	}

	public LanguageManager getLanguageManager() {
		return languageManager;
	}

	private record EconomySelection(
			EconomyProvider provider,
			EconomyProviderRefreshResult result,
			Throwable failure) {
	}
}
