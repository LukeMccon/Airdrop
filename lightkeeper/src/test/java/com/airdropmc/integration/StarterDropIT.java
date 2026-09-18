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
			try {
				player.remove();
			} finally {
				AirdropIntegrationSupport.cleanupCrate(framework, world);
			}
		}
	}

	@Test
	@FreshServer
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void malformedPackageReloadRetainsStarterAndReportsIndexedFailure(ILightkeeperFramework framework)
			throws Exception {
		AirdropIntegrationSupport.awaitReady(framework);
		Path packagesFile = framework.server().pluginDataDirectory("Airdrop").resolve("packages.yml");
		byte[] originalBytes = Files.readAllBytes(packagesFile);
		String originalPackages = new String(originalBytes, StandardCharsets.UTF_8);
		var baseline = ConsumerIntegrationSupport.snapshot(framework, "starter");
		assertThat(baseline.price()).isEqualTo("0.0");
		assertThat(baseline.items()).containsExactly(
				"IRON_HELMET:1", "IRON_CHESTPLATE:1", "IRON_LEGGINGS:1", "IRON_BOOTS:1", "BREAD:2");
		String repairedPackages = originalPackages.replaceFirst("    price: 0\\.0", "    price: 3.5")
				.replaceFirst("      count: 2", "      count: 3");
		assertThat(repairedPackages).isNotEqualTo(originalPackages);
		String malformedPackages = repairedPackages.replace("    price: 3.5",
				"    - not-an-item\n    price: 3.5");
		assertThat(malformedPackages).isNotEqualTo(repairedPackages);
		byte[] rejectedBytes = malformedPackages.getBytes(StandardCharsets.UTF_8);

		try {
			Files.write(packagesFile, rejectedBytes);
			int rejectedOffset = framework.server().output().size();
			CommandResult reload = framework.server().executeCommand(CommandSource.CONSOLE, "airdrop reload");
			assertThat(reload.success()).as("malformed package reload command dispatch").isTrue();
			eventually(Duration.ofSeconds(20), () -> {
				List<String> output = GuiReloadIntegrationSupport.outputSince(framework, rejectedOffset);
				assertThat(output).anyMatch(line -> line.contains(
						"Reload failed. The previous configuration remains active"));
				assertThat(output).anyMatch(line -> line.contains(
						"Package 'starter' has invalid item at index 5"));
			});
			assertThat(ConsumerIntegrationSupport.snapshot(framework, "starter"))
					.as("rejected reload preserves the complete public definition and revision").isEqualTo(baseline);
			assertThat(ConsumerIntegrationSupport.registryMarkers(framework, rejectedOffset)).isEmpty();
			assertThat(Files.readAllBytes(packagesFile))
					.as("rejected operator input is not rewritten").isEqualTo(rejectedBytes);

			// Preserve the original live-drop regression: invalid input must not just leave
			// the API snapshot intact, but must leave the free starter usable as well.
			assertRetainedStarterDrops(framework);
			assertThat(ConsumerIntegrationSupport.snapshot(framework, "starter")).isEqualTo(baseline);
			assertThat(ConsumerIntegrationSupport.registryMarkers(framework, rejectedOffset)).isEmpty();
			assertThat(Files.readAllBytes(packagesFile)).isEqualTo(rejectedBytes);

			Files.writeString(packagesFile, repairedPackages, StandardCharsets.UTF_8);
			int repairedOffset = framework.server().output().size();
			GuiReloadIntegrationSupport.reloadSuccessfully(framework);
			var repaired = ConsumerIntegrationSupport.snapshot(framework, "starter");
			assertThat(repaired.revision()).isEqualTo(baseline.revision() + 1);
			assertThat(repaired.name()).isEqualTo(baseline.name());
			assertThat(repaired.price()).isEqualTo("3.5");
			assertThat(repaired.items()).containsExactly(
					"IRON_HELMET:1", "IRON_CHESTPLATE:1", "IRON_LEGGINGS:1", "IRON_BOOTS:1", "BREAD:3");
			assertThat(ConsumerIntegrationSupport.registryMarkers(framework, repairedOffset))
					.singleElement().satisfies(line -> assertThat(line)
							.contains("cause=RELOAD", "revision=" + repaired.revision(), "primaryThread=true"));
			assertThat(Files.readAllBytes(packagesFile))
					.isEqualTo(repairedPackages.getBytes(StandardCharsets.UTF_8));
		} finally {
			Files.write(packagesFile, originalBytes);
			GuiReloadIntegrationSupport.reloadSuccessfully(framework);
			var restored = ConsumerIntegrationSupport.snapshot(framework, "starter");
			assertThat(restored.price()).isEqualTo(baseline.price());
			assertThat(restored.items()).isEqualTo(baseline.items());
			assertThat(Files.readAllBytes(packagesFile)).isEqualTo(originalBytes);
		}
	}

	private static void assertRetainedStarterDrops(ILightkeeperFramework framework) {
		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		PlayerHandle player = AirdropIntegrationSupport.createPlayer(framework, world, PACKAGE_PERMISSION);
		try {
			try (var drops = framework.events().capture(DROP_EVENT);
				 var lands = framework.events().capture(LAND_EVENT)) {
				player.executeCommand("airdrop starter");
				framework.waitUntil(() -> drops.getCapturedEvents().size() == 1, Duration.ofSeconds(10));
				AirdropIntegrationSupport.moveAway(player, world);
				framework.waitUntil(() -> lands.getCapturedEvents().size() == 1, Duration.ofSeconds(30));
				AirdropIntegrationSupport.assertStarterContents(framework, world, BARREL_POSITION);
				assertThat(drops.getCapturedEvents()).hasSize(1);
				assertThat(lands.getCapturedEvents()).hasSize(1);
			}
		} finally {
			try {
				player.remove();
			} finally {
				// Captures must be closed before cleanup generates a destruction event.
				AirdropIntegrationSupport.cleanupCrate(framework, world);
			}
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
