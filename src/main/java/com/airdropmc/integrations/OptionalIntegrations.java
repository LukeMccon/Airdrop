package com.airdropmc.integrations;

import com.airdropmc.helpers.AirdropLogger;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Discovers optional integrations without linking their API types into Airdrop's core classes.
 */
public final class OptionalIntegrations {

	private static final String LUCKPERMS_PLUGIN_NAME = "LuckPerms";
	private static final String LUCKPERMS_SERVICE_NAME = "LuckPerms";

	private OptionalIntegrations() {
		// Utility class
	}

	/**
	 * Initializes integrations that are present and reports absence or failure as a degraded state.
	 *
	 * @param server Bukkit server used for plugin and service discovery
	 * @return the LuckPerms integration state
	 */
	public static State initialize(Server server) {
		Objects.requireNonNull(server, "server");
		try {
			Plugin plugin = server.getPluginManager().getPlugin(LUCKPERMS_PLUGIN_NAME);
			if (plugin == null || !plugin.isEnabled()) {
				AirdropLogger.info("LuckPerms is not installed; using Bukkit permissions only");
				return State.NOT_INSTALLED;
			}

			RegisteredServiceProvider<?> registration = server.getServicesManager()
					.getRegistrations(plugin)
					.stream()
					.filter(candidate -> candidate.getService().getSimpleName()
							.equals(LUCKPERMS_SERVICE_NAME))
					.findFirst()
					.orElse(null);
			if (registration == null) {
				AirdropLogger.warning("LuckPerms is installed but its service is unavailable; "
						+ "using Bukkit permissions only");
				return State.SERVICE_UNAVAILABLE;
			}

			new LuckPermsIntegration(registration.getProvider()).initialize();
			AirdropLogger.info("LuckPerms convenience groups are available");
			return State.ACTIVE;
		} catch (LinkageError | RuntimeException failure) {
			AirdropLogger.log(Level.WARNING,
					"Could not initialize LuckPerms convenience groups; using Bukkit permissions only",
					failure);
			return State.FAILED;
		}
	}

	public enum State {
		NOT_INSTALLED,
		SERVICE_UNAVAILABLE,
		ACTIVE,
		FAILED
	}
}
