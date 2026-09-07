package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.FreshServer;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.airdropmc.integration.AirdropIntegrationSupport.BARREL_POSITION;
import static com.airdropmc.integration.AirdropIntegrationSupport.FREE_MARKER_TYPES;
import static com.airdropmc.integration.AirdropIntegrationSupport.PACKAGE_PERMISSION;
import static org.assertj.core.api.Assertions.assertThat;

@FreshServer
@ExtendWith(LightkeeperExtension.class)
class ApiConsumerIT {
	private static final String EXPECTED_SEQUENCE =
			"READY, REQUEST, SPAWNED, LANDING_ATTEMPT, LANDED, OUTCOME";

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void packagedConsumerObservesOneCorrelatedPrimaryThreadLifecycle(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		AirdropIntegrationSupport.keepLandingChunkLoaded(framework, world);
		PlayerHandle player = AirdropIntegrationSupport.createPlayer(
				framework, world, PACKAGE_PERMISSION);

		try {
			player.executeCommand("airdrop starter");
			AirdropIntegrationSupport.awaitConsumerMarkers(
					framework, 0, FREE_MARKER_TYPES.subList(0, 3));

			List<AirdropIntegrationSupport.ConsumerMarker> markers =
					AirdropIntegrationSupport.awaitConsumerMarkers(
							framework, 0, FREE_MARKER_TYPES);
			assertThat(markers).as(EXPECTED_SEQUENCE).hasSize(6);
			AirdropIntegrationSupport.assertCorrelatedPrimaryThreadSequence(markers);

			AirdropIntegrationSupport.ConsumerMarker outcome = markers.getLast();
			assertThat(outcome.required("delivery")).isEqualTo("LANDED");
			assertThat(outcome.required("payment")).isEqualTo("NOT_APPLICABLE");
			assertThat(outcome.required("reason")).isEqualTo("NONE");
			AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:barrel");
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			player.remove();
		}
	}
}
