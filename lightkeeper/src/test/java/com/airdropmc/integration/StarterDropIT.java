package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.CommandResult;
import nl.pim16aap2.lightkeeper.framework.FreshServer;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.InteractionResult;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.airdropmc.integration.AirdropIntegrationSupport.BARREL_POSITION;
import static com.airdropmc.integration.AirdropIntegrationSupport.BARREL_Y;
import static com.airdropmc.integration.AirdropIntegrationSupport.DROP_EVENT;
import static com.airdropmc.integration.AirdropIntegrationSupport.LANDING_X;
import static com.airdropmc.integration.AirdropIntegrationSupport.LANDING_Z;
import static com.airdropmc.integration.AirdropIntegrationSupport.LAND_EVENT;
import static com.airdropmc.integration.AirdropIntegrationSupport.PACKAGE_PERMISSION;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(LightkeeperExtension.class)
class StarterDropIT {
	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void permittedPlayerDropsZeroPricedStarterPackageWithExpectedContents(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);

		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		PlayerHandle player = AirdropIntegrationSupport.createPlayer(
				framework, world, PACKAGE_PERMISSION);

		try {
			try (var dropCapture = framework.events().capture(DROP_EVENT);
				 var landCapture = framework.events().capture(LAND_EVENT)) {
				player.executeCommand("airdrop starter");
				framework.waitUntil(() -> dropCapture.getCapturedEvents().size() == 1, Duration.ofSeconds(10));
				assertThat(dropCapture.getCapturedEvents()).hasSize(1);

				AirdropIntegrationSupport.moveAway(player, world);
				framework.waitUntil(() -> landCapture.getCapturedEvents().size() == 1, Duration.ofSeconds(30));
				player.andWaitTicks(5);

				assertThat(dropCapture.getCapturedEvents()).hasSize(1);
				assertThat(landCapture.getCapturedEvents()).hasSize(1);
				assertLandingEvent(landCapture.getCapturedEvents().getFirst(), world);
			}

			AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:barrel");

			player.teleport(world, 1.5, BARREL_Y, 0.5);
			InteractionResult interaction = player.rightClickBlock(BARREL_POSITION, BlockFace.EAST);
			assertThat(interaction).isEqualTo(new InteractionResult(true, false));

			AirdropIntegrationSupport.assertStarterContents(framework, world, BARREL_POSITION);
			player.andWaitTicks(65);
			AirdropIntegrationSupport.awaitNoDropEntities(world);
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			player.remove();
		}
	}

	@Test
	@FreshServer
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void malformedPackageReloadRetainsStarterAndReportsIndexedFailure(ILightkeeperFramework framework)
			throws Exception {
		AirdropIntegrationSupport.awaitReady(framework);
		Path packagesFile = framework.server().pluginDataDirectory("Airdrop").resolve("packages.yml");
		String originalPackages = Files.readString(packagesFile, StandardCharsets.UTF_8);
		String malformedPackages = originalPackages.replace(
				"    price: 0.0",
				"    - not-an-item\n    price: 0.0");
		assertThat(malformedPackages).isNotEqualTo(originalPackages);

		try {
			Files.writeString(packagesFile, malformedPackages, StandardCharsets.UTF_8);
			int outputLineCount = framework.server().output().size();
			CommandResult reload = framework.server().executeCommand(CommandSource.CONSOLE, "airdrop reload");
			assertThat(reload.success()).as("malformed package reload command").isTrue();

			eventually(Duration.ofSeconds(20), () -> {
				List<String> output = framework.server().output();
				List<String> newOutput = output.subList(outputLineCount, output.size());
				assertThat(newOutput).anyMatch(line -> line.contains(
						"Reload failed. The previous configuration remains active"));
				assertThat(newOutput).anyMatch(line -> line.contains(
						"Package 'starter' has invalid item at index 5"));
			});

			WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
			PlayerHandle player = AirdropIntegrationSupport.createPlayer(
					framework, world, PACKAGE_PERMISSION);
			try (var drops = framework.events().capture(DROP_EVENT)) {
				player.executeCommand("airdrop starter");
				framework.waitUntil(() -> drops.getCapturedEvents().size() == 1, Duration.ofSeconds(10));
				assertThat(drops.getCapturedEvents()).hasSize(1);
			} finally {
				player.remove();
			}
		} finally {
			Files.writeString(packagesFile, originalPackages, StandardCharsets.UTF_8);
		}
	}

	private void assertLandingEvent(
			nl.pim16aap2.lightkeeper.framework.CapturedEventSnapshot event,
			WorldHandle world
	) {
		assertThat(event.value("getWorld"))
				.isInstanceOfSatisfying(IProtocolValue.PRef.class,
						worldRef -> assertThat(worldRef.id()).isEqualTo(world.name()));
		assertThat(event.value("getLandingLocation")).isInstanceOf(IProtocolValue.PRecord.class);
		IProtocolValue.PRecord location = (IProtocolValue.PRecord) event.value("getLandingLocation");
		assertThat(location.fields().get("position"))
				.isEqualTo(new IProtocolValue.PVec(LANDING_X, BARREL_Y, LANDING_Z));
	}

}
