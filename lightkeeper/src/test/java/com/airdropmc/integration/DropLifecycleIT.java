package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.BlockSpec;
import nl.pim16aap2.lightkeeper.framework.FreshServer;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PBool;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PEnum;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PRecord;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.airdropmc.integration.AirdropIntegrationSupport.BARREL_POSITION;
import static com.airdropmc.integration.AirdropIntegrationSupport.DROP_EVENT;
import static com.airdropmc.integration.AirdropIntegrationSupport.LAND_EVENT;
import static com.airdropmc.integration.AirdropIntegrationSupport.PACKAGE_PERMISSION;
import static com.airdropmc.integration.AirdropIntegrationSupport.PLATFORM_POSITION;
import static org.assertj.core.api.Assertions.assertThat;

@FreshServer
@ExtendWith(LightkeeperExtension.class)
class DropLifecycleIT {
	private static final String BLOCK_CHANGE_EVENT = "org.bukkit.event.entity.EntityChangeBlockEvent";
	private static final String INVENTORY_MOVE_EVENT = "org.bukkit.event.inventory.InventoryMoveItemEvent";
	private static final BlockPos CHEST_POSITION = new BlockPos(0, 79, 0);

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void cancelledLandingCleansUpAndReleasesLocation(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		PlayerHandle rejectedPlayer = AirdropIntegrationSupport.createPlayer(
				framework, world, PACKAGE_PERMISSION);
		PlayerHandle retryingPlayer = null;

		try (var drops = framework.events().capture(DROP_EVENT);
			 var landings = framework.events().capture(LAND_EVENT)) {
			try (var blockChanges = framework.events().capture(BLOCK_CHANGE_EVENT)) {
				// LightKeeper cancellation is class-wide, so cover background events only for this phase.
				blockChanges.cancelNext(Integer.MAX_VALUE);
				rejectedPlayer.executeCommand("airdrop starter");
				AirdropIntegrationSupport.moveAway(rejectedPlayer, world);

				framework.waitUntil(() -> !drops.getCapturedEvents().isEmpty(), Duration.ofSeconds(30));
				assertThat(drops.getCapturedEvents()).hasSize(1);
				assertThat(drops.getCapturedEvents().getFirst().value("getCrate"))
						.isInstanceOf(PRecord.class);
				PRecord droppedCrate = (PRecord) drops.getCapturedEvents().getFirst().value("getCrate");
				assertThat(droppedCrate.fields().get("getFallingCrate")).isInstanceOf(PRef.class);
				PRef fallingCrate = (PRef) droppedCrate.fields().get("getFallingCrate");

				framework.waitUntil(() -> blockChanges.getCapturedEvents().stream()
						.anyMatch(event -> fallingCrate.equals(event.value("getEntity"))),
						Duration.ofSeconds(30));
				rejectedPlayer.andWaitTicks(5);

				assertThat(blockChanges.getCapturedEvents())
						.filteredOn(event -> fallingCrate.equals(event.value("getEntity")))
						.singleElement()
						.satisfies(event -> {
							assertThat(event.value("getTo"))
									.isEqualTo(new PEnum("org.bukkit.Material", "BARREL"));
							assertThat(event.value("isCancelled")).isEqualTo(new PBool(true));
						});
				assertThat(landings.getCapturedEvents()).isEmpty();
				AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:air");
				AirdropIntegrationSupport.awaitNoDropEntities(world);
			}

			retryingPlayer = AirdropIntegrationSupport.createPlayer(framework, world, PACKAGE_PERMISSION);
			retryingPlayer.executeCommand("airdrop starter");
			AirdropIntegrationSupport.moveAway(retryingPlayer, world);

			framework.waitUntil(() -> landings.getCapturedEvents().size() == 1, Duration.ofSeconds(30));
			AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:barrel");
			assertThat(drops.getCapturedEvents()).hasSize(2);
			assertThat(landings.getCapturedEvents()).hasSize(1);

			retryingPlayer.andWaitTicks(65);
			AirdropIntegrationSupport.awaitNoDropEntities(world);
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			if (retryingPlayer != null) {
				retryingPlayer.remove();
			}
			rejectedPlayer.remove();
		}
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void hopperExtractionConservesContentsAndReleasesLocation(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		PlayerHandle firstPlayer = AirdropIntegrationSupport.createPlayer(framework, world, PACKAGE_PERMISSION);
		PlayerHandle retryingPlayer = null;

		try (var drops = framework.events().capture(DROP_EVENT);
			 var landings = framework.events().capture(LAND_EVENT);
			 var moves = framework.events().capture(INVENTORY_MOVE_EVENT)) {
			firstPlayer.executeCommand("airdrop starter");
			AirdropIntegrationSupport.moveAway(firstPlayer, world);

			framework.waitUntil(() -> landings.getCapturedEvents().size() == 1, Duration.ofSeconds(30));
			AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:barrel");

			world.setBlockAt(CHEST_POSITION, "minecraft:chest");
			world.setBlockAt(PLATFORM_POSITION,
					BlockSpec.parse("minecraft:hopper[facing=down,enabled=true]"));

			AirdropIntegrationSupport.awaitStarterContentsInDrainedChest(
					framework, world, CHEST_POSITION, PLATFORM_POSITION);
			assertThat(moves.getCapturedEvents()).isNotEmpty();
			AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:air");
			AirdropIntegrationSupport.awaitNoEntities(world, "minecraft:item");

			world.setBlockAt(PLATFORM_POSITION, "minecraft:stone");
			retryingPlayer = AirdropIntegrationSupport.createPlayer(framework, world, PACKAGE_PERMISSION);
			retryingPlayer.executeCommand("airdrop starter");
			AirdropIntegrationSupport.moveAway(retryingPlayer, world);

			framework.waitUntil(() -> landings.getCapturedEvents().size() == 2, Duration.ofSeconds(30));
			AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:barrel");
			assertThat(drops.getCapturedEvents()).hasSize(2);
			assertThat(landings.getCapturedEvents()).hasSize(2);
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			if (retryingPlayer != null) {
				retryingPlayer.remove();
			}
			firstPlayer.remove();
		}
	}
}
