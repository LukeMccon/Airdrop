package com.airdropmc;

import java.io.File;
import java.util.Objects;

import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.lang.LanguageManager;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.listeners.CrateDestroyListener;
import com.airdropmc.listeners.CrateCloseListener;
import com.airdropmc.listeners.CrateOpenListener;
import com.airdropmc.listeners.FallingCrateListener;
import com.airdropmc.packages.PackageManager;
import com.airdropmc.packages.PackagesGui;
import net.luckperms.api.LuckPerms;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;

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
	private static Economy airdropEconomy = null;
	private static Config configuration;
	private static PackagesConfig packagesConfiguration;
	private LanguageManager languageManager;

	// Define constructors per BukkitMock setup instructions
	public Airdrop() {
		super();
	}

	protected Airdrop(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
		super(loader, description, dataFolder, file);
	}

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

		// Initialize language system
		this.languageManager = new LanguageManager(this);
		String lang = configuration.getConfig().getString("language", "en");
		languageManager.loadLanguage(lang);
		ChatHandler.init(languageManager);

		// Economy
		if (!setupEconomy()) {
			ChatHandler.logMessage(ChatHandler.get(MessageKey.SYSTEM_VAULT_MISSING));
			getServer().getPluginManager().disablePlugin(Airdrop.pluginInstance);
			return;
		}

		// Register Command and tab completer
		Objects.requireNonNull(this.getCommand(AIRDROP_COMMAND)).setExecutor(new CmdAirdrop());
		Objects.requireNonNull(this.getCommand(AIRDROP_COMMAND)).setTabCompleter(new AirdropTabCompleter());

		// Register Listeners
		Bukkit.getPluginManager().registerEvents(new FallingCrateListener(), this);
		Bukkit.getPluginManager().registerEvents(new CrateCloseListener(), this);
		Bukkit.getPluginManager().registerEvents(new CrateOpenListener(), this);
		Bukkit.getPluginManager().registerEvents(new CrateDestroyListener(), this);

		// Load packages configuration
		packagesConfiguration = new PackagesConfig(this);
		packagesConfiguration.getConfig();

		// Start the package manager
		PackageManager.reload();

		PermissionsHelper.initialize();

	}

	@Override
	public void onDisable() {

	}

	private boolean setupEconomy() {
		if (getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			return false;
		}
		airdropEconomy = rsp.getProvider();
		return airdropEconomy != null;
	}

	public void setupPackageGuis() {
		packagesGui = new PackagesGui();
		Bukkit.getPluginManager().registerEvents(packagesGui, this);
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

	public static Economy getAirdropEconomy() {
		return airdropEconomy;
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

	public LanguageManager getLanguageManager() {
		return languageManager;
	}

}
