package lukemccon.airdrop;

import java.io.File;
import java.util.HashMap;
import java.util.Objects;

import lukemccon.airdrop.helpers.ChatHandler;
import lukemccon.airdrop.helpers.PermissionsHelper;
import lukemccon.airdrop.packages.PackageGui;
import lukemccon.airdrop.packages.PackagesGui;
import net.luckperms.api.LuckPerms;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;

import lukemccon.airdrop.commands.CmdAirdrop;
import lukemccon.airdrop.listeners.BarrelInventoryCloseListener;
import lukemccon.airdrop.listeners.FallingBlockListener;
import lukemccon.airdrop.packages.PackageManager;


public class Airdrop extends JavaPlugin {

	public static final String PLUGIN_NAME = "Airdrop";
	private static Airdrop pluginInstance;
	private static String pluginVersion;
	private static String pluginApiVersion;
	private static LuckPerms luckPerms;

	private static PackagesGui packagesGui;

	public static final HashMap<String, PackageGui> PACKAGE_GUIS = new HashMap<String,PackageGui>();

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

		// Register Commands
		Objects.requireNonNull(this.getCommand("airdrop")).setExecutor(new CmdAirdrop());
		Objects.requireNonNull(this.getCommand("airdrop")).setTabCompleter(new AirdropTabCompleter());
		
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

		PackageManager.packages.values().stream()
				.map(pkg -> new PackageGui(pkg))
				.map(pkg -> {
					PACKAGE_GUIS.put(pkg.getName(), pkg);
					return pkg;
				})
				.forEach(gui -> Bukkit.getPluginManager().registerEvents(gui, this));
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
