package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
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
	private static final String EXPLOSION_LOOT = "loot={BARREL=1, BREAD=2, IRON_BOOTS=1, "
			+ "IRON_CHESTPLATE=1, IRON_HELMET=1, IRON_LEGGINGS=1}";
	private static long lastConsoleSendNanos;

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void explodingSharedFreeCratesReleasesFinalTicketAndPreservesNativeLoot(
			ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
		awaitConsoleCooldown(framework);
		WorldHandle world = framework.worlds().builder().withRandomName()
				.withWorldType(WorldSpec.WorldType.FLAT).withSeed(96L).build();
		List<BlockPos> positions = List.of(new BlockPos(4097, 81, 4097), new BlockPos(4110, 81, 4110));
		List<UUID> requests = new java.util.ArrayList<>();
		try {
			for (BlockPos position : positions) {
				AirdropIntegrationSupport.remoteFixture(framework, "prepare-explosion", world, position.x(), position.z());
			}
			awaitUnloaded(world);
			int offset = framework.server().output().size();
			for (BlockPos position : positions) {
				int requestOffset = framework.server().output().size();
				sendFree(framework, world, position);
				var markers = AirdropIntegrationSupport.awaitConsumerMarkers(framework, requestOffset, LANDED);
				AirdropIntegrationSupport.assertCorrelatedPrimaryThreadSequence(markers);
				assertThat(markers.getLast().required("payment")).isEqualTo("NOT_APPLICABLE");
				requests.add(markers.getFirst().requestId());
				AirdropIntegrationSupport.assertStarterContents(framework, world, position);
			}
			assertThat(observe(framework, world)).contains("tickets=1");
			for (int index = 0; index < positions.size(); index++) {
				BlockPos position = positions.get(index);
				UUID request = requests.get(index);
				assertThat(AirdropIntegrationSupport.remoteFixture(framework,
						"explode-free", world, position.x(), position.z())).contains(EXPLOSION_LOOT);
				eventually(Duration.ofSeconds(10), () -> assertThat(lifecycle(framework, offset, "RETIRED", request))
						.singleElement().satisfies(line -> assertThat(line).contains("reason=EXPLODED")));
				assertThat(observe(framework, world)).contains("tickets=" + (index == 0 ? 1 : 0));
			}
			awaitUnloaded(world);
			world.loadChunk(BARREL.x() >> 4, BARREL.z() >> 4);
			for (int index = 0; index < positions.size(); index++) {
				assertThat(world.blockTypeAt(positions.get(index))).isEqualTo("minecraft:air");
				assertThat(lifecycle(framework, offset, "RECOVERED", requests.get(index))).isEmpty();
				assertThat(lifecycle(framework, offset, "RETIRED", requests.get(index))).hasSize(1);
			}
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			AirdropIntegrationSupport.remoteFixture(framework, "cleanup", world, BARREL.x(), BARREL.z());
			// Reload the generated neighborhood so loose native drops are removed even after chunk unload.
			AirdropIntegrationSupport.remoteFixture(framework, "prepare", world, BARREL.x(), BARREL.z());
			framework.server().executeCommand(CommandSource.CONSOLE,
					"minecraft:execute in minecraft:%s run kill @e[type=minecraft:item]".formatted(world.name()));
			assertThat(observe(framework, world)).contains("tickets=0", "auxiliaries=0");
		}
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void consoleRewardStaysCollectableUntilExpiryThenReleasesChunkWithoutReplay(
			ILightkeeperFramework framework) throws Exception {
		AirdropIntegrationSupport.awaitReady(framework);
		awaitConsoleCooldown(framework);
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
			sendFree(framework, world, BARREL);
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

	private static void sendFree(ILightkeeperFramework framework, WorldHandle world, BlockPos position) {
		AirdropIntegrationSupport.remoteFixture(framework, "send-free", world, position.x(), position.z());
		lastConsoleSendNanos = System.nanoTime();
	}

	private static void awaitConsoleCooldown(ILightkeeperFramework framework) {
		// The class shares a server and the default five-second console remote-attempt bucket.
		if (lastConsoleSendNanos != 0) {
			framework.waitUntil(() -> System.nanoTime() - lastConsoleSendNanos >= Duration.ofSeconds(5).toNanos(),
					Duration.ofSeconds(6));
		}
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
