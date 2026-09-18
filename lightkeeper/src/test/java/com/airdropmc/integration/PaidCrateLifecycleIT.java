package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.CommandResult;
import nl.pim16aap2.lightkeeper.framework.FreshServer;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.airdropmc.integration.AirdropIntegrationSupport.DROP_EVENT;
import static com.airdropmc.integration.AirdropIntegrationSupport.LAND_EVENT;
import static com.airdropmc.integration.EconomyIntegrationSupport.OPERATION_EVENT;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@FreshServer
@ExtendWith(LightkeeperExtension.class)
class PaidCrateLifecycleIT {
	// Well outside spawn tickets, and centered within a chunk so the complete platform is in that chunk.
	private static final BlockPos BARREL = new BlockPos(4104, 81, 4104);
	private static final int CHUNK_X = BARREL.x() >> 4;
	private static final int CHUNK_Z = BARREL.z() >> 4;
	private static final List<String> LANDED_SEQUENCE =
			List.of("REQUEST", "SPAWNED", "LANDING_ATTEMPT", "LANDED", "OUTCOME");

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void partialPaidBarrelSurvivesRealChunkUnloadAndOneRecovery(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
		WorldHandle world = createOffsetWorld(framework);
		String token = unique("persistence");
		PlayerHandle player = null;
		try (var operations = framework.events().capture(OPERATION_EVENT);
			 var drops = framework.events().capture(DROP_EVENT);
			 var landings = framework.events().capture(LAND_EVENT)) {
			GuiReloadIntegrationSupport.enableEconomyProvider(framework);
			runFixture(framework, "begin", token, locationArguments(world) + " persistence");
			player = createPlayer(framework, world);
			UUID account = player.uniqueId();
			EconomyIntegrationSupport.resetAccount(framework, account, "100.00");
			int outputOffset = framework.server().output().size();
			player.executeCommand("airdrop premium");
			framework.waitUntil(() -> drops.getCapturedEvents().size() == 1, Duration.ofSeconds(20));
			moveBesideBarrel(player, world);
			framework.waitUntil(() -> landings.getCapturedEvents().size() == 1, Duration.ofSeconds(30));
			UUID request = assertDelivered(framework, outputOffset);
			Map<String, String> original = runFixture(framework, "capture", token, "");
			assertThat(original.get("requestId")).isEqualTo(request.toString());
			assertPaidOnce(framework, account);

			// No force-load, direct recovery call, world-save suppression or unrelated ticket removal.
			player.remove();
			player = null;
			eventually(Duration.ofSeconds(20), () -> {
				assertThatCode(() -> world.unloadChunk(CHUNK_X, CHUNK_Z))
						.as("native unload after the test player leaves").doesNotThrowAnyException();
				assertThat(world.isChunkLoaded(CHUNK_X, CHUNK_Z)).as("positive unloaded observation").isFalse();
			});
			assertIdentity(runFixture(framework, "suspended", token, ""), original);
			assertThat(world.isChunkLoaded(CHUNK_X, CHUNK_Z)).as("bookkeeping did not reload chunk").isFalse();
			assertLifecycleMarker(framework, outputOffset, "RETIRED", request, original.get("crateId"), "CHUNK_UNLOAD");
			assertThat(lifecycleLines(framework, outputOffset, "RECOVERED", request)).isEmpty();
			assertPaidOnce(framework, account);

			world.loadChunk(CHUNK_X, CHUNK_Z);
			assertThat(world.isChunkLoaded(CHUNK_X, CHUNK_Z)).isTrue();
			assertIdentity(runFixture(framework, "recovered", token, ""), original);
			assertLifecycleMarker(framework, outputOffset, "RECOVERED", request, original.get("crateId"), null);
			assertPaidOnce(framework, account);

			// Native load of an already-loaded chunk must be idempotent, including its expiry task.
			world.loadChunk(CHUNK_X, CHUNK_Z);
			assertIdentity(runFixture(framework, "recovered", token, ""), original);
			assertLifecycleMarker(framework, outputOffset, "RECOVERED", request, original.get("crateId"), null);
			assertLifecycleMarker(framework, outputOffset, "RETIRED", request, original.get("crateId"), "CHUNK_UNLOAD");
			assertPaidOnce(framework, account);
			assertThat(EconomyIntegrationSupport.operationsFor(operations.getCapturedEvents(), account)).hasSize(2);
			assertThat(drops.getCapturedEvents()).hasSize(1);
			assertThat(landings.getCapturedEvents()).hasSize(1);
			assertThat(assertDelivered(framework, outputOffset)).isEqualTo(request);
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			finish(framework, token, player);
		}
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void realScheduledExpiryRetainsNonemptyPaidContentsAsAnOrdinaryBarrel(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
		WorldHandle world = createOffsetWorld(framework);
		String token = unique("expiry");
		PlayerHandle player = null;
		try (var operations = framework.events().capture(OPERATION_EVENT);
			 var drops = framework.events().capture(DROP_EVENT);
			 var landings = framework.events().capture(LAND_EVENT)) {
			GuiReloadIntegrationSupport.enableEconomyProvider(framework);
			runFixture(framework, "begin", token, locationArguments(world) + " expiry");
			player = createPlayer(framework, world);
			UUID account = player.uniqueId();
			EconomyIntegrationSupport.resetAccount(framework, account, "100.00");
			int outputOffset = framework.server().output().size();
			player.executeCommand("airdrop premium");
			framework.waitUntil(() -> drops.getCapturedEvents().size() == 1, Duration.ofSeconds(20));
			moveBesideBarrel(player, world);
			framework.waitUntil(() -> landings.getCapturedEvents().size() == 1, Duration.ofSeconds(30));
			UUID request = assertDelivered(framework, outputOffset);
			Map<String, String> original = runFixture(framework, "capture", token, "");
			assertThat(original.get("requestId")).isEqualTo(request.toString());
			assertPaidOnce(framework, account);

			// Keep the logged-in player in the same chunk; wait for the actual scheduled 30-second expiry.
			framework.waitUntil(() -> {
				assertThat(world.isChunkLoaded(CHUNK_X, CHUNK_Z)).as("expiry chunk remains loaded").isTrue();
				return !lifecycleLines(framework, outputOffset, "RETIRED", request).isEmpty();
			}, Duration.ofSeconds(70));
			// Cleanup and this command are serialized on the server thread. Check once so an early
			// retirement cannot become a pass by retrying until the original deadline arrives.
			assertIdentity(runFixture(framework, "expired", token, ""), original);
			assertLifecycleMarker(framework, outputOffset, "RETIRED", request, original.get("crateId"), "EXPIRED");
			AirdropIntegrationSupport.awaitBlock(world, BARREL, "minecraft:barrel");
			assertPaidOnce(framework, account);
			assertThat(EconomyIntegrationSupport.operationsFor(operations.getCapturedEvents(), account)).hasSize(2);
			assertThat(drops.getCapturedEvents()).hasSize(1);
			assertThat(landings.getCapturedEvents()).hasSize(1);
			assertThat(lifecycleLines(framework, outputOffset, "RECOVERED", request)).isEmpty();
			assertThat(assertDelivered(framework, outputOffset)).isEqualTo(request);
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			finish(framework, token, player);
		}
	}

	private static void finish(ILightkeeperFramework framework, String token, PlayerHandle player) {
		try {
			// Captures have closed; restore the live settings, remove/save the barrel and release token references.
			runFixture(framework, "cleanup", token, "");
		} finally {
			try {
				if (player != null) {
					player.remove();
				}
			} finally {
				assertThat(framework.server().executeCommand(CommandSource.CONSOLE, "lkeconomy disable").success())
						.as("restore the initially disabled fixture provider").isTrue();
				GuiReloadIntegrationSupport.reloadSuccessfully(framework);
			}
		}
	}

	private static WorldHandle createOffsetWorld(ILightkeeperFramework framework) {
		WorldHandle world = framework.worlds().builder().withRandomName()
				.withWorldType(WorldSpec.WorldType.FLAT).withSeed(87L).build();
		for (int x = BARREL.x() - 2; x <= BARREL.x() + 2; x++) {
			for (int z = BARREL.z() - 2; z <= BARREL.z() + 2; z++) {
				world.setBlockAt(new BlockPos(x, BARREL.y() - 1, z), "minecraft:stone");
			}
		}
		return world;
	}

	private static PlayerHandle createPlayer(ILightkeeperFramework framework, WorldHandle world) {
		return framework.bots().builder().withRandomName()
				.atLocation(world, BARREL.x() + 0.5, BARREL.y(), BARREL.z() + 0.5)
				.withPermissions("airdrop.package.premium").fullLogin().build();
	}

	private static void moveBesideBarrel(PlayerHandle player, WorldHandle world) {
		player.teleport(world, BARREL.x() + 5.5, BARREL.y(), BARREL.z() + 5.5);
	}

	private static String locationArguments(WorldHandle world) {
		return "%s %d %d %d".formatted(world.name(), BARREL.x(), BARREL.y(), BARREL.z());
	}

	private static UUID assertDelivered(ILightkeeperFramework framework, int outputOffset) {
		var markers = AirdropIntegrationSupport.awaitConsumerMarkers(framework, outputOffset, LANDED_SEQUENCE);
		AirdropIntegrationSupport.assertCorrelatedPrimaryThreadSequence(markers);
		assertThat(markers.getLast().required("delivery")).isEqualTo("LANDED");
		assertThat(markers.getLast().required("payment")).isEqualTo("CHARGED");
		return markers.getFirst().requestId();
	}

	private static void assertPaidOnce(ILightkeeperFramework framework, UUID account) {
		EconomyIntegrationSupport.assertAccountState(framework, account, "89.75", 1, 1, 0);
	}

	private static List<String> lifecycleLines(
			ILightkeeperFramework framework, int outputOffset, String type, UUID request) {
		List<String> output = framework.server().output();
		return output.subList(outputOffset, output.size()).stream()
				.filter(line -> line.contains("AIRDR_CONSUMER_" + type + " "))
				.filter(line -> Arrays.asList(line.split("\\s+")).contains("requestId=" + request))
				.toList();
	}

	private static void assertLifecycleMarker(ILightkeeperFramework framework, int outputOffset,
			String type, UUID request, String crateId, String reason) {
		eventually(Duration.ofSeconds(10), () -> {
			List<String> lines = lifecycleLines(framework, outputOffset, type, request);
			assertThat(lines).hasSize(1);
			assertThat(attributes(lines.getFirst())).containsEntry("crateId", crateId)
					.containsEntry("primaryThread", "true");
			if (reason != null) {
				assertThat(attributes(lines.getFirst())).containsEntry("reason", reason);
			}
		});
	}

	private static Map<String, String> runFixture(
			ILightkeeperFramework framework, String action, String token, String arguments) {
		String probe = unique("probe");
		String marker = "AIRDR_87_LIFECYCLE token=" + token + " probe=" + probe + " action=" + action + " ";
		int offset = framework.server().output().size();
		CommandResult result = framework.server().executeCommand(CommandSource.CONSOLE,
				"airdrop-lightkeeper-paid-lifecycle " + action + " " + token + " " + probe + " " + arguments);
		assertThat(result.success()).as("paid lifecycle command dispatch: %s", result.message()).isTrue();
		framework.waitUntil(() -> newOutput(framework, offset).stream().anyMatch(line -> line.contains(marker)),
				Duration.ofSeconds(10));
		List<String> acknowledgements = newOutput(framework, offset).stream()
				.filter(line -> line.contains(marker)).toList();
		assertThat(acknowledgements).hasSize(1);
		String acknowledgement = acknowledgements.getFirst();
		assertThat(acknowledgement).as("server-side lifecycle assertions completed").contains(" status=OK");
		return attributes(acknowledgement.substring(acknowledgement.indexOf(marker)));
	}

	private static void assertIdentity(Map<String, String> current, Map<String, String> original) {
		for (String attribute : List.of("requestId", "crateId", "deadline")) {
			assertThat(current).containsEntry(attribute, original.get(attribute));
		}
	}

	private static List<String> newOutput(ILightkeeperFramework framework, int offset) {
		List<String> output = framework.server().output();
		return output.subList(offset, output.size());
	}

	private static Map<String, String> attributes(String line) {
		Map<String, String> attributes = new LinkedHashMap<>();
		for (String word : line.split("\\s+")) {
			int separator = word.indexOf('=');
			if (separator > 0) {
				attributes.put(word.substring(0, separator), word.substring(separator + 1));
			}
		}
		return Map.copyOf(attributes);
	}

	private static String unique(String label) {
		return label + "_" + UUID.randomUUID().toString().replace("-", "");
	}
}
