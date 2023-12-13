package com.airdropmc;

import java.io.File;
import java.util.Objects;

import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.listeners.BarrelInventoryCloseListener;
import com.airdropmc.listeners.FallingBlockListener;
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


public class Airdrop extends JavaPlugin {

	public static final String PLUGIN_NAME = "Airdrop";
	public static final String AIRDROP_COMMAND = "airdrop";
	private static Airdrop pluginInstance;
	private static String pluginVersion;
	private static String pluginApiVersion;
	private static LuckPerms luckPerms;
	private static PackagesGui packagesGui;
	private static Economy airdropEconomy = null;

	
	// Define constructors per BukkitMock setup instructions
	public Airdrop() {
		super();
	}
	
	protected Airdrop(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file)
    {
        super(loader, description, dataFolder, file);
    }
	
	@Override
	public void onEnable() {
		PluginDescriptionFile pdf = this.getDescription();

		pluginInstance = this;
		pluginVersion = pdf.getVersion();
		pluginApiVersion = pdf.getAPIVersion();

		// Economy
		if (!setupEconomy() ) {
			ChatHandler.logMessage("Disabling due to no Vault dependency");
			getServer().getPluginManager().disablePlugin(Airdrop.pluginInstance);
			return;
		}

		// Register Command and tab completer
		Objects.requireNonNull(this.getCommand(AIRDROP_COMMAND)).setExecutor(new CmdAirdrop());
		Objects.requireNonNull(this.getCommand(AIRDROP_COMMAND)).setTabCompleter(new AirdropTabCompleter());
		
		// Register Listeners
		Bukkit.getPluginManager().registerEvents(new FallingBlockListener(), this);
		Bukkit.getPluginManager().registerEvents(new BarrelInventoryCloseListener(), this);
		
		// Load configuration files
		PackagesConfig.loadConfig();
		
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

	public void setupPackageGuis () {
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

}
