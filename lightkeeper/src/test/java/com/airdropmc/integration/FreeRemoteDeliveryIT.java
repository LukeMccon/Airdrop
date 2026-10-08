package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** AIRDR-95: actual Paper retention and scheduled cleanup with no players or force-loaded chunks. */
@ExtendWith(LightkeeperExtension.class)
class FreeRemoteDeliveryIT {
	private static final BlockPos BARREL = new BlockPos(4104, 81, 4104);
	private static final List<String> LANDED = List.of("REQUEST", "SPAWNED", "LANDING_ATTEMPT", "LANDED", "OUTCOME");

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void consoleRewardStaysCollectableUntilExpiryThenReleasesChunkWithoutReplay(
			ILightkeeperFramework framework) throws Exception {
		AirdropIntegrationSupport.awaitReady(framework);
		var configFile = SendIT.configFile(framework);
		byte[] originalConfig = Files.readAllBytes(configFile);
		WorldHandle world = framework.worlds().builder().withRandomName()
				.withWorldType(WorldSpec.WorldType.FLAT).withSeed(95L).build();
		try {
			String config = Files.readString(configFile);
			assertThat(config).contains("drop:\n").doesNotContain("landed-lifetime-seconds:");
			Files.writeString(configFile, config.replace("drop:\n",
					"drop:\n  limits:\n    landed-lifetime-seconds: 30\n"));
			GuiReloadIntegrationSupport.reloadSuccessfully(framework);
			AirdropIntegrationSupport.remoteFixture(framework, "prepare", world, BARREL.x(), BARREL.z());
			awaitUnloaded(world);
			int offset = framework.server().output().size();
			AirdropIntegrationSupport.remoteFixture(framework, "send-free", world, BARREL.x(), BARREL.z());
			var markers = AirdropIntegrationSupport.awaitConsumerMarkers(framework, offset, LANDED);
			AirdropIntegrationSupport.assertCorrelatedPrimaryThreadSequence(markers);
			assertThat(markers.getLast().required("delivery")).isEqualTo("LANDED");
			assertThat(markers.getLast().required("payment")).isEqualTo("NOT_APPLICABLE");
			UUID request = markers.getFirst().requestId();
			assertThat(observe(framework, world)).contains("loaded=true", "tickets=1", "auxiliaries=0");
			String identity = AirdropIntegrationSupport.remoteFixture(framework,
					"inspect-free", world, BARREL.x(), BARREL.z());
			assertThat(identity).contains("paid=false", "persistedPaid=false");
			long deadline = Long.parseLong(attribute(identity, "deadline"));
			String crateId = attribute(identity, "crateId");
			assertThat(deadline).isBetween(System.currentTimeMillis() + 20_000, System.currentTimeMillis() + 30_000);
			// Native unload attempt has no player or unrelated force-load retaining this chunk.
			assertThat(AirdropIntegrationSupport.remoteFixture(framework,
					"unload", world, BARREL.x(), BARREL.z())).contains("loaded=true", "players=0");
			AirdropIntegrationSupport.assertStarterContents(framework, world, BARREL);
			assertThat(lifecycle(framework, offset, "RETIRED", request)).isEmpty();
			framework.waitUntil(() -> !lifecycle(framework, offset, "RETIRED", request).isEmpty(), Duration.ofSeconds(50));
			assertThat(System.currentTimeMillis()).isGreaterThanOrEqualTo(deadline);
			assertThat(lifecycle(framework, offset, "RETIRED", request)).singleElement()
					.satisfies(line -> assertThat(line).contains("crateId=" + crateId, "reason=EXPIRED"));
			assertThat(observe(framework, world)).contains("tickets=0", "auxiliaries=0");
			awaitUnloaded(world);
			// Real unload/reload after expiry must not recreate a barrel or duplicate its contents.
			world.loadChunk(BARREL.x() >> 4, BARREL.z() >> 4);
			assertThat(world.blockTypeAt(BARREL)).isEqualTo("minecraft:air");
			assertThat(lifecycle(framework, offset, "RECOVERED", request)).isEmpty();
			assertThat(AirdropIntegrationSupport.awaitConsumerMarkers(framework, offset, LANDED)).hasSize(5);
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			try {
				AirdropIntegrationSupport.remoteFixture(framework, "cleanup", world, BARREL.x(), BARREL.z());
				assertThat(observe(framework, world)).contains("tickets=0", "auxiliaries=0");
			} finally {
				Files.write(configFile, originalConfig);
				GuiReloadIntegrationSupport.reloadSuccessfully(framework);
			}
		}
	}

	private static String observe(ILightkeeperFramework framework, WorldHandle world) {
		return AirdropIntegrationSupport.remoteFixture(framework, "observe", world, BARREL.x(), BARREL.z());
	}

	private static void awaitUnloaded(WorldHandle world) {
		eventually(Duration.ofSeconds(20), () -> {
			assertThatCode(() -> world.unloadChunk(BARREL.x() >> 4, BARREL.z() >> 4)).doesNotThrowAnyException();
			assertThat(world.isChunkLoaded(BARREL.x() >> 4, BARREL.z() >> 4)).isFalse();
		});
	}

	private static List<String> lifecycle(ILightkeeperFramework framework, int offset, String type, UUID request) {
		List<String> output = framework.server().output();
		return output.subList(offset, output.size()).stream()
				.filter(line -> line.contains("AIRDR_CONSUMER_" + type + " "))
				.filter(line -> Arrays.asList(line.split("\\s+")).contains("requestId=" + request)).toList();
	}

	private static String attribute(String line, String key) {
		return Arrays.stream(line.split("\\s+")).filter(word -> word.startsWith(key + "="))
				.map(word -> word.substring(key.length() + 1)).findFirst().orElseThrow();
	}
}
