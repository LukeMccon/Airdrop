package dev.airdropmc.example;

import org.bukkit.plugin.java.JavaPlugin;

public final class ExamplePlugin extends JavaPlugin {
	@Override
	public void onEnable() {
		if (getServer().getPluginManager().isPluginEnabled("Airdrop")) {
			AirdropIntegration.enable(this);
		}
	}
}
