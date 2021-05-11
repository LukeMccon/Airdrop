package me.lukemccon.airdrop;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import me.lukemccon.airdrop.commands.*;
import me.lukemccon.airdrop.listeners.FallingBlockListener;

public class Airdrop extends JavaPlugin {
	
	@Override
	public void onEnable() {
		
		// Register Commands
		this.getCommand("airdrop").setExecutor(new CmdAirdrop());
		
		// Register Listeners
		Bukkit.getPluginManager().registerEvents(new FallingBlockListener(), this);
		
		// Load configuration files
		PackagesConfig.loadConfig();
		
	}
	
	@Override
	public void onDisable() {
		
	}
		
}
