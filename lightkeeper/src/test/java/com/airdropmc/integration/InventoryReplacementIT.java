package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.CommandResult;
import nl.pim16aap2.lightkeeper.framework.FreshServer;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.airdropmc.integration.AirdropIntegrationSupport.BARREL_POSITION;
import static com.airdropmc.integration.AirdropIntegrationSupport.LAND_EVENT;
import static com.airdropmc.integration.AirdropIntegrationSupport.PACKAGE_PERMISSION;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static org.assertj.core.api.Assertions.assertThat;

@FreshServer
@ExtendWith(LightkeeperExtension.class)
class InventoryReplacementIT {
	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void staleInventoryEventsPreserveReplacementAndNormalCloseReleasesIt(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		PlayerHandle player = AirdropIntegrationSupport.createPlayer(framework, world, PACKAGE_PERMISSION);

		try (var landings = framework.events().capture(LAND_EVENT)) {
			runFixture(framework, "capture %s %s %d %d %d".formatted(
					player.name(), world.name(), BARREL_POSITION.x(), BARREL_POSITION.y(), BARREL_POSITION.z()));
			AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:air");
			player.executeCommand("airdrop starter");
			AirdropIntegrationSupport.moveAway(player, world);
			framework.waitUntil(() -> landings.getCapturedEvents().size() == 1, Duration.ofSeconds(30));
			AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:barrel");
			AirdropIntegrationSupport.assertStarterContents(framework, world, BARREL_POSITION);

			runFixture(framework, "verify " + player.name());

			AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:air");
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			player.remove();
		}
	}

	private static void runFixture(ILightkeeperFramework framework, String arguments) {
		String marker = "AIRDR_49_VERIFIED_" + Long.toUnsignedString(System.nanoTime(), 36);
		int firstNewLine = framework.server().output().size();
		CommandResult result = framework.server().executeCommand(CommandSource.CONSOLE,
				"airdrop-lightkeeper-inventory-replacement " + arguments + " " + marker);
		assertThat(result.success()).as("inventory replacement fixture: %s", result.message()).isTrue();
		eventually(Duration.ofSeconds(10), () -> {
			var output = framework.server().output();
			assertThat(output.subList(firstNewLine, output.size()))
					.as("fixture success marker emitted after server-side assertions")
					.anyMatch(line -> line.endsWith(" " + marker));
		});
	}
}
