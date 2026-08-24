package com.airdropmc.economy;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

import java.util.Objects;
import java.util.Optional;

public final class EconomyProviderDiscovery {

	private EconomyProviderDiscovery() {
	}

	public static Optional<EconomyProvider> discover(ServicesManager services) {
		Objects.requireNonNull(services, "services");

		try {
			Optional<EconomyProvider> modern = ModernDiscovery.discover(services);
			if (modern.isPresent()) {
				return modern;
			}
		} catch (LinkageError | RuntimeException ignored) {
			// VaultUnlocked's modern classes or provider are unavailable; try the legacy service.
		}

		RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> registration =
				services.getRegistration(net.milkbowl.vault.economy.Economy.class);
		if (registration == null || registration.getProvider() == null) {
			return Optional.empty();
		}
		return Optional.of(new VaultEconomyProvider(registration.getProvider()));
	}

	private static final class ModernDiscovery {

		private ModernDiscovery() {
		}

		private static Optional<EconomyProvider> discover(ServicesManager services) {
			RegisteredServiceProvider<net.milkbowl.vault2.economy.Economy> registration =
					services.getRegistration(net.milkbowl.vault2.economy.Economy.class);
			if (registration == null || registration.getProvider() == null) {
				return Optional.empty();
			}

			net.milkbowl.vault2.economy.Economy economy = registration.getProvider();
			if (!economy.supportsAsync()) {
				return Optional.empty();
			}
			return economy.async().map(async ->
					new VaultUnlockedEconomyProvider(async, economy.getName()));
		}
	}
}
