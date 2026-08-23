package com.airdropmc;

import java.util.Objects;

import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.lang.LanguageManager;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.listeners.CrateDestroyListener;
import com.airdropmc.listeners.CrateCloseListener;
import com.airdropmc.listeners.CrateCleanupListener;
import com.airdropmc.listeners.CrateOpenListener;
import com.airdropmc.listeners.FallingCrateListener;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.packages.PackageManager;
import com.airdropmc.packages.PackagesGui;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.economy.TreasuryEconomyProvider;
import com.airdropmc.economy.VaultEconomyProvider;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredServiceProvider;
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
	private static EconomyProvider economyProvider = null;
	private static Config configuration;
	private static PackagesConfig packagesConfiguration;
	private static DropAdmissionController dropAdmissionController;
	private LanguageManager languageManager;


	@Override
	public void onEnable() {
		PluginDescriptionFile pdf = this.getDescription();

		pluginInstance = this;
		pluginVersion = pdf.getVersion();
		pluginApiVersion = pdf.getAPIVersion();

		// Load configuration
		configuration = new Config(this);
		configuration.saveDefaultConfig();
		configuration.getConfig();
		dropAdmissionController = new DropAdmissionController();

		// Initialize language system
		this.languageManager = new LanguageManager(this);
		String lang = configuration.getConfig().getString("language", "en");
		languageManager.loadLanguage(lang);
		ChatHandler.init(languageManager);

		// Economy
		if (ConfigKeys.isEconomyEnabled()) {
			if (!setupEconomy()) {
				ChatHandler.logMessage(ChatHandler.get(MessageKey.SYSTEM_ECONOMY_MISSING));
				getServer().getPluginManager().disablePlugin(Airdrop.pluginInstance);
				return;
			}
		}

		// Register Command and tab completer
		Objects.requireNonNull(this.getCommand(AIRDROP_COMMAND)).setExecutor(new CmdAirdrop());
		Objects.requireNonNull(this.getCommand(AIRDROP_COMMAND)).setTabCompleter(new AirdropTabCompleter());

		// Register Listeners
		Bukkit.getPluginManager().registerEvents(new FallingCrateListener(), this);
		Bukkit.getPluginManager().registerEvents(new CrateCloseListener(), this);
		Bukkit.getPluginManager().registerEvents(new CrateOpenListener(), this);
		Bukkit.getPluginManager().registerEvents(new CrateDestroyListener(), this);
		Bukkit.getPluginManager().registerEvents(new CrateCleanupListener(), this);

		// Load packages configuration
		packagesConfiguration = new PackagesConfig(this);
		packagesConfiguration.getConfig();

		// Start the package manager
		PackageManager.reload();

		PermissionsHelper.initialize();

	}

	@Override
	public void onDisable() {
		Bukkit.getScheduler().cancelTasks(this);
		CrateManager.clearAll();
		PackageManager.clear();
		if (packagesGui != null) {
			HandlerList.unregisterAll(packagesGui);
			packagesGui = null;
		}
		HandlerList.unregisterAll(this);
		pluginInstance = null;
		pluginVersion = null;
		pluginApiVersion = null;
		luckPerms = null;
		economyProvider = null;
		configuration = null;
		packagesConfiguration = null;
	}

	private boolean setupEconomy() {
		EconomyProvider treasuryProvider = setupTreasuryEconomy();
		if (treasuryProvider != null) {
			economyProvider = treasuryProvider;
			ChatHandler.logMessage("Using economy provider: " + treasuryProvider.getName());
			return true;
		}

		EconomyProvider vaultProvider = setupVaultEconomy();
		if (vaultProvider != null) {
			economyProvider = vaultProvider;
			ChatHandler.logMessage("Using economy provider: " + vaultProvider.getName());
			return true;
		}

		return false;
	}

	private EconomyProvider setupTreasuryEconomy() {
		try {
			return TreasuryEconomyProvider.fromServiceRegistry().orElse(null);
		} catch (NoClassDefFoundError ex) {
			return null;
		}
	}

	private EconomyProvider setupVaultEconomy() {
		if (getServer().getPluginManager().getPlugin("Vault") == null) {
			return null;
		}
		RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp =
				getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
		if (rsp == null) {
			return null;
		}
		net.milkbowl.vault.economy.Economy vault = rsp.getProvider();
		if (vault == null) {
			return null;
		}
		return new VaultEconomyProvider(vault);
	}

	public void setupPackageGuis() {
		if (packagesGui != null) {
			HandlerList.unregisterAll(packagesGui);
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

	public LanguageManager getLanguageManager() {
		return languageManager;
	}

}
