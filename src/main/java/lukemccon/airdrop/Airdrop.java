package lukemccon.airdrop;

import java.io.File;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;

import lukemccon.airdrop.commands.CmdAirdrop;
import lukemccon.airdrop.commands.CmdDropzone;
import lukemccon.airdrop.listeners.BarrelInventoryCloseListener;
import lukemccon.airdrop.listeners.FallingBlockListener;
import lukemccon.airdrop.packages.PackageManager;

public class Airdrop extends JavaPlugin {
	
	public static Airdrop PLUGIN_INSTANCE;
	
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
		
		PLUGIN_INSTANCE = this;
		
		// Register Commands
		this.getCommand("airdrop").setExecutor(new CmdAirdrop());
		this.getCommand("airdrop").setTabCompleter(new AirdropTabCompleter());
		this.getCommand("dropzone").setExecutor(new CmdDropzone());
		
		// Register Listeners
		Bukkit.getPluginManager().registerEvents(new FallingBlockListener(), this);
		Bukkit.getPluginManager().registerEvents(new BarrelInventoryCloseListener(), this);
		
		// Load configuration files
		PackagesConfig.loadConfig();
		
		// Start the package manager
		PackageManager.reload();
		
	}
	
	@Override
	public void onDisable() {
		
	}
		
}
