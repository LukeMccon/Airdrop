package com.airdropmc.internal.api;

import com.airdropmc.Airdrop;
import com.airdropmc.api.AirdropApi;
import com.airdropmc.api.AirdropVersions;
import com.airdropmc.api.EconomyState;
import com.airdropmc.economy.EconomyProviderRefreshResult;
import com.airdropmc.integrations.OptionalIntegrations;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Owns registration and lifecycle transitions for one Airdrop service provider. */
@ApiStatus.Internal
public final class AirdropServiceLifecycle {

	private static final String EXTENSION_API_UNAVAILABLE = "unavailable";

	private final Airdrop plugin;
	private final ServicesManager services;
	private final DefaultAirdropApi api;
	private boolean registered;
	private OptionalIntegrations.State optionalIntegrationsState;

	public AirdropServiceLifecycle(Airdrop plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.services = plugin.getServer().getServicesManager();
		PluginDescriptionFile description = plugin.getDescription();
		this.api = new DefaultAirdropApi(plugin, new AirdropVersions(
				description.getVersion(),
				EXTENSION_API_UNAVAILABLE,
				requireVersion(description.getAPIVersion(), "unknown"),
				Integer.toString(Runtime.version().feature())));
	}

	public synchronized void register() {
		boolean providerPresent = services.getRegistrations(plugin).stream()
				.anyMatch(candidate -> candidate.getService() == AirdropApi.class
						&& candidate.getProvider() == api);
		if (registered && providerPresent) {
			return;
		}
		for (RegisteredServiceProvider<?> candidate : List.copyOf(services.getRegistrations(plugin))) {
			if (candidate.getService() == AirdropApi.class) {
				services.unregister(AirdropApi.class, candidate.getProvider());
			}
		}
		services.register(AirdropApi.class, api, plugin, ServicePriority.Normal);
		registered = true;
	}

	public void publishReady(
			EconomyProviderRefreshResult economy,
			OptionalIntegrations.State optionalIntegrations) {
		optionalIntegrationsState = Objects.requireNonNull(
				optionalIntegrations, "optionalIntegrations");
		publishEconomy(economy);
		api.publishReady();
	}

	public void publishEconomy(EconomyProviderRefreshResult economy) {
		Objects.requireNonNull(economy, "economy");
		EconomyState state = switch (economy.outcome()) {
			case ACTIVE -> EconomyState.ACTIVE;
			case DISABLED -> EconomyState.DISABLED;
			case UNAVAILABLE -> EconomyState.UNAVAILABLE;
		};
		String providerName = state == EconomyState.ACTIVE ? economy.providerName() : null;
		api.publishEconomy(state, providerName, degradedReasons(economy, optionalIntegrationsState));
	}

	public void refreshPackageCount() {
		api.refreshPackageCount();
	}

	public void publishFailure(Throwable failure) {
		api.publishFailure(failure);
	}

	public synchronized void stop() {
		api.publishStopping();
		api.stopRequests();
		services.unregister(AirdropApi.class, api);
		registered = false;
	}

	private static List<String> degradedReasons(
			EconomyProviderRefreshResult economy,
			OptionalIntegrations.State integrations) {
		List<String> reasons = new ArrayList<>();
		switch (economy.outcome()) {
			case DISABLED -> reasons.add("economy-disabled");
			case UNAVAILABLE -> reasons.add("economy-provider-unavailable");
			case ACTIVE -> {
				// Active economy is not degraded.
			}
		}
		if (integrations != null) {
			switch (integrations) {
				case NOT_INSTALLED -> reasons.add("luckperms-not-installed");
				case SERVICE_UNAVAILABLE -> reasons.add("luckperms-service-unavailable");
				case FAILED -> reasons.add("luckperms-initialization-failed");
				case ACTIVE -> {
					// Active optional integration is not degraded.
				}
			}
		}
		return List.copyOf(reasons);
	}

	private static String requireVersion(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}
}
