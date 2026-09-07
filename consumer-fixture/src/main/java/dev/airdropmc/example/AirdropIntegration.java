package dev.airdropmc.example;

import com.airdropmc.api.AirdropApi;
import org.bukkit.plugin.java.JavaPlugin;

final class AirdropIntegration {
	static void enable(JavaPlugin plugin) {
		AirdropApi api = plugin.getServer().getServicesManager().load(AirdropApi.class);
		if (api == null) {
			return;
		}

		api.readiness().whenComplete((ready, failure) -> {
			plugin.getServer().getScheduler().runTask(plugin, () -> {
				if (failure != null) {
					plugin.getLogger().warning("Airdrop did not become ready");
					return;
				}
				plugin.getLogger().info("Airdrop API "
						+ ready.versions().extensionApiVersion() + " is ready");
			});
		});
	}
}
