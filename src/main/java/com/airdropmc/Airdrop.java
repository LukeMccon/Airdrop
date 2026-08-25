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
import com.airdropmc.helpers.PermissionsHelper;
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
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import com.airdropmc.commands.CmdAirdrop;

/**
 * Main plugin class
 */
public class Airdrop extends JavaPlugin {

	public static final String PLUGIN_NAME = "Airdrop";
	public static final String AIRDROP_COMMAND = "airdrop";
	private static Airdrop pluginInstance;
	private static String pluginVersion;
	private static String pluginApiVersion;
	private static LuckPerms luckPerms;
	private static PackagesGui packagesGui;
	private static volatile EconomyProvider economyProvider;
	private static volatile Config configuration;
	private static volatile PackagesConfig packagesConfiguration;
	private static DropAdmissionController dropAdmissionController;
	private static volatile boolean shuttingDown;
	private static volatile boolean ready;
	private LanguageManager languageManager;
	private ConfigCoordinator configurationCoordinator;

	@Override
	public void onEnable() {
		shuttingDown = false;
		ready = false;
		PluginDescriptionFile pdf = this.getDescription();

		pluginInstance = this;
		pluginVersion = pdf.getVersion();
		pluginApiVersion = pdf.getAPIVersion();
		dropAdmissionController = new DropAdmissionController();

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
			if (failure == null || shuttingDown || pluginInstance != this) {
				return;
			}
			getLogger().log(Level.SEVERE,
					"Airdrop startup configuration failed; the plugin will be disabled", unwrap(failure));
			Bukkit.getPluginManager().disablePlugin(this);
		});
	}

	@Override
	public void onDisable() {
		shuttingDown = true;
		ready = false;
		runDisableStep("close package editors", PackageGui::closeOpenEditors);
		runDisableStep("close package browser", () -> {
			if (packagesGui != null) {
				packagesGui.closeAndUnregister();
			}
		});
		ConfigCoordinator coordinator = configurationCoordinator;
		configurationCoordinator = null;
		if (coordinator != null) {
			coordinator.close();
		}

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
			pluginApiVersion = null;
			luckPerms = null;
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
		PackageManager.publishPackages(candidate.packages());
		publishEconomyProvider(selection);

		refreshPackageBrowser();
		if (candidate.startup()) {
			initializeStartupIntegrations();
			ready = true;
		}
		return selection.result();
	}

	private void commitPackages(ConfigCoordinator.PackageCandidate candidate) {
		packagesConfiguration = new PackagesConfig(candidate.configuration());
		PackageManager.publishPackages(candidate.packages());
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

	private void initializeStartupIntegrations() {
		try {
			CrateManager.recoverLoadedCrates(this, dropAdmissionController);
		} catch (RuntimeException failure) {
			getLogger().log(Level.WARNING, "Could not recover saved crates", failure);
		}
		try {
			PermissionsHelper.initialize();
		} catch (RuntimeException failure) {
			getLogger().log(Level.WARNING, "Could not initialize permissions", failure);
		}
	}

	public CompletionStage<EconomyProviderRefreshResult> reloadConfiguration() {
		ConfigCoordinator coordinator = configurationCoordinator;
		if (coordinator == null || shuttingDown || !ready) {
			return unavailableStage();
		}
		return coordinator.reload();
	}

	public CompletionStage<Boolean> createPackageAsync(Package pkg) {
		ConfigCoordinator coordinator = configurationCoordinator;
		if (coordinator == null || shuttingDown || !ready) {
			return unavailableStage();
		}
		return coordinator.createPackage(pkg);
	}

	public CompletionStage<Boolean> updatePackageInventoryAsync(String packageName, List<ItemStack> items) {
		ConfigCoordinator coordinator = configurationCoordinator;
		if (coordinator == null || shuttingDown || !ready) {
			return unavailableStage();
		}
		return coordinator.updatePackageInventory(packageName, items);
	}

	public CompletionStage<Boolean> deletePackageAsync(String packageName) {
		ConfigCoordinator coordinator = configurationCoordinator;
		if (coordinator == null || shuttingDown || !ready) {
			return unavailableStage();
		}
		return coordinator.deletePackage(packageName);
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
						: EconomyProviderRefreshResult.active(providerName(replacement));
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

	public static String getPluginApiVersion() {
		return pluginApiVersion;
	}

	public static LuckPerms getLuckPerms() {
		return luckPerms;
	}

	public static void setLuckPerms(LuckPerms luckPerms) {
		Airdrop.luckPerms = luckPerms;
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
