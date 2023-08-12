package lukemccon.airdrop;

import java.io.File;

import lukemccon.airdrop.packages.PackagesGui;
import net.ess3.api.IEssentials;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.node.Node;
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

	public static IEssentials ESSENTIALS = (IEssentials) Bukkit.getPluginManager().getPlugin("Essentials");
	public static Airdrop PLUGIN_INSTANCE;
	public static String PLUGIN_VERSION;
	public static String PLUGIN_API_VERSION;
	public static LuckPerms LUCK_PERMS;

	public static PackagesGui PACKAGES_GUI;
	
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

		PACKAGES_GUI = new PackagesGui();
		Bukkit.getPluginManager().registerEvents(PACKAGES_GUI, this);

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
		
}
