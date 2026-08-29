package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.CommandResult;
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

import java.time.Duration;
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
		PlayerHandle player = AirdropIntegrationSupport.createPlayer(framework, world);

		try {
			grantPermission(framework, player, PACKAGE_PERMISSION);

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
			unsetPermission(framework, player, PACKAGE_PERMISSION);
			player.remove();
		}
	}

	private void grantPermission(ILightkeeperFramework framework, PlayerHandle player, String permission) {
		CommandResult result = framework.server().executeCommand(CommandSource.CONSOLE,
				"lp user %s permission set %s true".formatted(player.name(), permission));
		assertThat(result.success()).as("LuckPerms grant for %s", permission).isTrue();
		eventually(Duration.ofSeconds(20), () ->
				assertThat(player.permissions().has(permission)).as(permission).isTrue());
	}

	private void unsetPermission(ILightkeeperFramework framework, PlayerHandle player, String permission) {
		framework.server().executeCommand(CommandSource.CONSOLE,
				"lp user %s permission unset %s".formatted(player.name(), permission));
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
