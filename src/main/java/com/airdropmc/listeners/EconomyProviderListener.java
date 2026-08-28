package com.airdropmc.listeners;

import com.airdropmc.Airdrop;
import com.airdropmc.economy.EconomyProviderDiscovery;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EconomyProviderListener implements Listener {

	private final Airdrop plugin;

	public EconomyProviderListener(Airdrop plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onServiceRegister(ServiceRegisterEvent event) {
		refresh(event.getProvider());
	}

	@EventHandler
	public void onServiceUnregister(ServiceUnregisterEvent event) {
		refresh(event.getProvider());
	}

	private void refresh(RegisteredServiceProvider<?> registration) {
		if (Airdrop.isShuttingDown()
				|| !Airdrop.isReady()
				|| !EconomyProviderDiscovery.isSupportedService(registration.getService())) {
			return;
		}
		plugin.refreshEconomyProvider();
	}
}
