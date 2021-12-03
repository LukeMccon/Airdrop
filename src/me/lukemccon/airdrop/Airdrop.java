package me.lukemccon.airdrop;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import me.lukemccon.airdrop.PackagesConfig;
import me.lukemccon.airdrop.commands.*;
import me.lukemccon.airdrop.listeners.FallingBlockListener;
import me.lukemccon.airdrop.packages.PackageManager;

public class Airdrop extends JavaPlugin {
	
	public static Airdrop PLUGIN_INSTANCE;
	
	@Override
	public void onEnable() {
		
		PLUGIN_INSTANCE = this;
		
		// Register Commands
		this.getCommand("airdrop").setExecutor(new CmdAirdrop());
		this.getCommand("dropzone").setExecutor(new CmdDropzone());
		
		// Register Listeners
		Bukkit.getPluginManager().registerEvents(new FallingBlockListener(), this);
		
		// Load configuration files
		PackagesConfig.loadConfig();
		
		// Start the package manager
		PackageManager.reload();
		
	}
	
	@Override
	public void onDisable() {
		
	}
		
}
