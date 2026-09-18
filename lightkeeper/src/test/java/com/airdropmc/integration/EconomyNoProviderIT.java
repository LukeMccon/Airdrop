package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.FreshServer;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.airdropmc.integration.AirdropIntegrationSupport.BARREL_POSITION;
import static com.airdropmc.integration.AirdropIntegrationSupport.BARREL_Y;
import static com.airdropmc.integration.AirdropIntegrationSupport.DROP_EVENT;
import static com.airdropmc.integration.AirdropIntegrationSupport.LANDING_X;
import static com.airdropmc.integration.AirdropIntegrationSupport.LANDING_Z;
import static com.airdropmc.integration.AirdropIntegrationSupport.LAND_EVENT;
import static com.airdropmc.integration.AirdropIntegrationSupport.PAID_PACKAGE_PERMISSION;
import static com.airdropmc.integration.AirdropIntegrationSupport.REJECTED_MARKER_TYPES;
import static org.assertj.core.api.Assertions.assertThat;

@FreshServer
@ExtendWith(LightkeeperExtension.class)
class EconomyNoProviderIT {
	private static final String EXPECTED_SEQUENCE = "REQUEST, OUTCOME";

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void fullLoginPricedRequestIsTypedWithoutProviderOrSideEffects(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		PlayerHandle player = AirdropIntegrationSupport.createPlayer(
				framework, world, PAID_PACKAGE_PERMISSION);
		int outputLineCount = framework.server().output().size();

		try (var drops = framework.events().capture(DROP_EVENT);
			 var landings = framework.events().capture(LAND_EVENT)) {
			player.executeCommand("airdrop paid");

			List<AirdropIntegrationSupport.ConsumerMarker> markers =
					AirdropIntegrationSupport.awaitConsumerMarkers(
							framework, outputLineCount, REJECTED_MARKER_TYPES);
			assertThat(markers).as(EXPECTED_SEQUENCE).hasSize(2);
			AirdropIntegrationSupport.assertCorrelatedPrimaryThreadSequence(markers);

			AirdropIntegrationSupport.ConsumerMarker outcome = markers.getLast();
			assertThat(outcome.required("delivery")).isEqualTo("REJECTED");
			assertThat(outcome.required("payment")).isEqualTo("REJECTED");
			assertThat(outcome.required("reason"))
					.isEqualTo("ECONOMY_PROVIDER_UNAVAILABLE");
			assertThat(drops.getCapturedEvents()).isEmpty();
			assertThat(landings.getCapturedEvents()).isEmpty();
			AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:air");
			AirdropIntegrationSupport.awaitNoDropEntities(world);
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			try {
				AirdropIntegrationSupport.cleanupCrate(framework, world);
			} finally {
				player.remove();
			}
		}
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void legacyPricedRequestReceivesLocalizedRejectionWithoutProvider(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		// Only the legacy spawn path has an embedded channel for receivedMessages capture.
		PlayerHandle player = framework.bots().builder()
				.withRandomName()
				.atLocation(world, LANDING_X + 0.5, BARREL_Y, LANDING_Z + 0.5)
				.withPermissions(PAID_PACKAGE_PERMISSION)
				.build();

		try {
			int messageOffset = player.receivedMessages().size();
			player.executeCommand("airdrop paid");

			String localized = "Priced packages are unavailable because economy is disabled or no provider is available. Contact a server administrator.";
			framework.waitUntil(() -> player.receivedMessages().stream().skip(messageOffset)
					.anyMatch(message -> message.contains(localized)), Duration.ofSeconds(10));
			assertThat(player.receivedMessages().stream().skip(messageOffset)
					.filter(message -> message.contains(localized)).toList())
					.as("localized rejection actually received by the requesting player").hasSize(1);
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			try {
				AirdropIntegrationSupport.cleanupCrate(framework, world);
			} finally {
				player.remove();
			}
		}
	}
}
