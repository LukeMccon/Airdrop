package com.airdropmc.economy;

import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class EconomyProviderDiscovery {

	private static final String MODERN_SERVICE = "net.milkbowl.vault2.economy.Economy";
	private static final String LEGACY_SERVICE = "net.milkbowl.vault.economy.Economy";

	private EconomyProviderDiscovery() {
	}

	public static boolean isSupportedService(Class<?> service) {
		String serviceName = Objects.requireNonNull(service, "service").getName();
		return MODERN_SERVICE.equals(serviceName) || LEGACY_SERVICE.equals(serviceName);
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
		if (registration == null) {
			return Optional.empty();
		}
		net.milkbowl.vault.economy.Economy economy = registration.getProvider();
		if (economy == null || !isEnabled(economy::isEnabled)) {
			return Optional.empty();
		}
		return Optional.of(new VaultEconomyProvider(economy));
	}

	private static boolean isEnabled(BooleanSupplier enabledCheck) {
		try {
			return enabledCheck.getAsBoolean();
		} catch (LinkageError | RuntimeException ignored) {
			return false;
		}
	}

	private static final class ModernDiscovery {

		private ModernDiscovery() {
		}

		private static Optional<EconomyProvider> discover(ServicesManager services) {
			RegisteredServiceProvider<net.milkbowl.vault2.economy.Economy> registration =
					services.getRegistration(net.milkbowl.vault2.economy.Economy.class);
			if (registration == null) {
				return Optional.empty();
			}

			net.milkbowl.vault2.economy.Economy economy = registration.getProvider();
			if (economy == null || !isEnabled(economy::isEnabled)) {
				return Optional.empty();
			}
			if (!economy.supportsAsync()) {
				return Optional.empty();
			}
			return economy.async().map(async ->
					new VaultUnlockedEconomyProvider(async, economy.getName()));
		}
	}
}
