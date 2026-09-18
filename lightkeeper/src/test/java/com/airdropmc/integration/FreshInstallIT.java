package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.FreshServer;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.airdropmc.integration.AirdropIntegrationSupport.BARREL_POSITION;
import static com.airdropmc.integration.AirdropIntegrationSupport.DROP_EVENT;
import static com.airdropmc.integration.AirdropIntegrationSupport.LAND_EVENT;
import static com.airdropmc.integration.AirdropIntegrationSupport.PACKAGE_PERMISSION;
import static com.airdropmc.integration.AirdropIntegrationSupport.REJECTED_MARKER_TYPES;
import static org.assertj.core.api.Assertions.assertThat;

/** AIRDR-87: separate provisioning lane, with neither the overlay nor optional dependency plugins. */
@FreshServer
@ExtendWith(LightkeeperExtension.class)
class FreshInstallIT {
	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void generatesPaidStarterWithoutOptionalDependenciesAndPreservesCustomizationAcrossBoots(
			ILightkeeperFramework framework
	) throws Exception {
		awaitDependencyFreeReady(framework, 0);
		Path data = framework.server().pluginDataDirectory("Airdrop");
		Path packagesFile = data.resolve("packages.yml");
		Path configFile = data.resolve("config.yml");
		assertThat(packagesFile).isRegularFile();
		assertThat(configFile).isRegularFile();
		assertThat(data.resolve("lang/en.yml")).isRegularFile();

		var starter = ConsumerIntegrationSupport.snapshot(framework, "starter");
		assertThat(starter.price()).isEqualTo("10.0");
		assertThat(starter.items()).containsExactly(
				"IRON_HELMET:1", "IRON_CHESTPLATE:1", "IRON_LEGGINGS:1", "IRON_BOOTS:1", "BREAD:2");
		assertProviderlessRejection(framework);
		AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);

		framework.server().stop();
		String customizedPackages = """
				packages:
				  starter:
				    price: 7.5
				    items:
				    - ==: org.bukkit.inventory.ItemStack
				      DataVersion: 4671
				      schema_version: 1
				      id: minecraft:bread
				      count: 16
				  custom:
				    price: 0.0
				    items: []
				""";
		String customizedConfig = Files.readString(configFile)
				+ "\n# AIRDR-87 operator customization retained on the second boot\n";
		Files.writeString(packagesFile, customizedPackages);
		Files.writeString(configFile, customizedConfig);
		int secondBootOffset = framework.server().output().size();
		framework.server().start();
		awaitDependencyFreeReady(framework, secondBootOffset);

		var customizedStarter = ConsumerIntegrationSupport.snapshot(framework, "starter");
		assertThat(customizedStarter.price()).isEqualTo("7.5");
		assertThat(customizedStarter.items()).containsExactly("BREAD:16");
		var custom = ConsumerIntegrationSupport.snapshot(framework, "custom");
		assertThat(custom.price()).isEqualTo("0.0");
		assertThat(custom.items()).isEmpty();
		assertThat(Files.readString(packagesFile)).isEqualTo(customizedPackages);
		assertThat(Files.readString(configFile)).isEqualTo(customizedConfig);
		// Stop/start invalidates player/world handles; the second request creates fresh ones.
		assertProviderlessRejection(framework);
		AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		// This lane is disposable: let the extension capture failures before closing the server.
	}

	private static void awaitDependencyFreeReady(ILightkeeperFramework framework, int outputOffset) {
		AirdropIntegrationSupport.awaitConsumerMarkers(framework, outputOffset, List.of("READY"));
		nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat(framework).isPaper();
		assertThat(framework.server().plugin("Airdrop"))
				.hasValueSatisfying(plugin -> assertThat(plugin.isEnabled()).isTrue());
		assertThat(framework.server().plugin("AirdropConsumerFixture"))
				.hasValueSatisfying(plugin -> assertThat(plugin.isEnabled()).isTrue());
		assertThat(framework.server().plugin("Vault")).isEmpty();
		assertThat(framework.server().plugin("LuckPerms")).isEmpty();
	}

	private static void assertProviderlessRejection(ILightkeeperFramework framework) {
		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		PlayerHandle player = AirdropIntegrationSupport.createPlayer(framework, world, PACKAGE_PERMISSION);
		int outputOffset = framework.server().output().size();
		try (var drops = framework.events().capture(DROP_EVENT);
			 var landings = framework.events().capture(LAND_EVENT)) {
			player.executeCommand("airdrop starter");
			var markers = AirdropIntegrationSupport.awaitConsumerMarkers(
					framework, outputOffset, REJECTED_MARKER_TYPES);
			AirdropIntegrationSupport.assertCorrelatedPrimaryThreadSequence(markers);
			assertThat(markers.getLast().required("reason")).isEqualTo("ECONOMY_PROVIDER_UNAVAILABLE");
			assertThat(markers.getLast().required("delivery")).isEqualTo("REJECTED");
			assertThat(markers.getLast().required("payment")).isEqualTo("REJECTED");
			assertThat(drops.getCapturedEvents()).isEmpty();
			assertThat(landings.getCapturedEvents()).isEmpty();
			AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:air");
			AirdropIntegrationSupport.awaitNoDropEntities(world);
		} finally {
			player.remove();
		}
	}
}
