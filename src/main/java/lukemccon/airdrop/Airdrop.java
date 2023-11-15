package lukemccon.airdrop;

import java.io.File;
import java.util.HashMap;

import lukemccon.airdrop.exceptions.PackageNotFoundException;
import lukemccon.airdrop.helpers.ChatHandler;
import lukemccon.airdrop.packages.PackageGui;
import lukemccon.airdrop.packages.PackagesGui;
import net.ess3.api.IEssentials;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.node.Node;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;

import lukemccon.airdrop.commands.CmdAirdrop;
import lukemccon.airdrop.listeners.BarrelInventoryCloseListener;
import lukemccon.airdrop.listeners.FallingBlockListener;
import lukemccon.airdrop.packages.PackageManager;

import static org.bukkit.Bukkit.getServer;


public class Airdrop extends JavaPlugin {

	public static IEssentials ESSENTIALS = (IEssentials) Bukkit.getPluginManager().getPlugin("Essentials");
	public static Airdrop PLUGIN_INSTANCE;
	public static String PLUGIN_VERSION;
	public static String PLUGIN_API_VERSION;
	public static LuckPerms LUCK_PERMS;
	public static PackagesGui PACKAGES_GUI;

	public static HashMap<String, PackageGui> PACKAGE_GUIS = new HashMap<String,PackageGui>();

	public static Economy AIRDROP_ECONOMY = null;
	
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

		PLUGIN_INSTANCE = this;
		PLUGIN_VERSION = pdf.getVersion();
		PLUGIN_API_VERSION = pdf.getAPIVersion();

		// Economy
		if (!setupEconomy() ) {
			ChatHandler.logMessage("Disabling due to no Vault dependency");
			getServer().getPluginManager().disablePlugin(Airdrop.PLUGIN_INSTANCE);
			return;
		}

		// Register Commands
		this.getCommand("airdrop").setExecutor(new CmdAirdrop());
		this.getCommand("airdrop").setTabCompleter(new AirdropTabCompleter());
		
		// Register Listeners
		Bukkit.getPluginManager().registerEvents(new FallingBlockListener(), this);
		Bukkit.getPluginManager().registerEvents(new BarrelInventoryCloseListener(), this);

		
		// Load configuration files
		PackagesConfig.loadConfig();
		
		// Start the package manager
		PackageManager.reload();

		RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
		if (provider != null) {
			LUCK_PERMS = provider.getProvider();

			GroupManager manager = LUCK_PERMS.getGroupManager();
			Group adminGroup = manager.getGroup("airdrop-admin");
			Group userGroup = manager.getGroup("airdrop-user");
			Node allPackagesNode = Node.builder("airdrop.package.all").build();
			Node adminNode = Node.builder("airdrop.admin").build();

			if (adminGroup == null) {
				// group doesn't exist.
				adminGroup = manager.createAndLoadGroup("airdrop-admin").join();
				adminGroup.data().add(adminNode);
			}

			if (userGroup == null) {
				userGroup = manager.createAndLoadGroup("airdrop-user").join();
				adminGroup.data().add(allPackagesNode);
			}

		}
		
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
		AIRDROP_ECONOMY = rsp.getProvider();
		return AIRDROP_ECONOMY != null;
	}

	public void setupPackageGuis () {
		PACKAGES_GUI = new PackagesGui();
		Bukkit.getPluginManager().registerEvents(PACKAGES_GUI, this);

		PackageManager.packages.values().stream()
				.map(pkg -> new PackageGui(pkg))
				.map(pkg -> {
					PACKAGE_GUIS.put(pkg.getName(), pkg);
					return pkg;
				})
				.forEach(gui -> Bukkit.getPluginManager().registerEvents(gui, this));
	}

}
