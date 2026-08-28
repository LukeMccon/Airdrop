package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.CommandResult;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.InteractionResult;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(LightkeeperExtension.class)
class StarterDropIT {
	private static final String DROP_EVENT = "com.airdropmc.events.PackageDropEvent";
	private static final String LAND_EVENT = "com.airdropmc.events.PackageLandEvent";
	private static final String PACKAGE_PERMISSION = "airdrop.package.starter";
	private static final int LANDING_X = 0;
	private static final int PLATFORM_Y = 80;
	private static final int BARREL_Y = PLATFORM_Y + 1;
	private static final int LANDING_Z = 0;

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void permittedPlayerDropsDefaultStarterPackageWithExpectedContents(ILightkeeperFramework framework) {
		eventually(Duration.ofSeconds(20), () ->
				assertThat(framework.server().output())
						.anyMatch(line -> line.contains("Economy support is disabled")));
		assertThat(framework.server().plugin("Airdrop")).isPresent();
		assertThat(framework.server().plugin("LuckPerms")).isPresent();

		WorldHandle world = createLandingWorld(framework);
		PlayerHandle player = framework.bots().builder()
				.withName("airdrop_it")
				.atLocation(world, LANDING_X + 0.5, BARREL_Y, LANDING_Z + 0.5)
				.fullLogin()
				.build();

		try {
			grantPermission(framework, player, PACKAGE_PERMISSION);

			try (var dropCapture = framework.events().capture(DROP_EVENT);
				 var landCapture = framework.events().capture(LAND_EVENT)) {
				player.executeCommand("airdrop starter");
				framework.waitUntil(() -> dropCapture.getCapturedEvents().size() == 1, Duration.ofSeconds(10));
				assertThat(dropCapture.getCapturedEvents()).hasSize(1);

				player.teleport(world, 10.5, BARREL_Y, 10.5);
				framework.waitUntil(() -> landCapture.getCapturedEvents().size() == 1, Duration.ofSeconds(30));
				player.andWaitTicks(5);

				assertThat(dropCapture.getCapturedEvents()).hasSize(1);
				assertThat(landCapture.getCapturedEvents()).hasSize(1);
				assertLandingEvent(landCapture.getCapturedEvents().getFirst(), world);
			}

			BlockPos barrelPosition = new BlockPos(LANDING_X, BARREL_Y, LANDING_Z);
			eventually(Duration.ofSeconds(10), () ->
					assertThat(world.blockAt(barrelPosition).material()).isEqualTo("minecraft:barrel"));

			player.teleport(world, 1.5, BARREL_Y, 0.5);
			InteractionResult interaction = player.rightClickBlock(barrelPosition, BlockFace.EAST);
			assertThat(interaction).isEqualTo(new InteractionResult(true, false));

			assertStarterContents(framework, world);
		} finally {
			unsetPermission(framework, player, PACKAGE_PERMISSION);
			player.remove();
		}
	}

	private WorldHandle createLandingWorld(ILightkeeperFramework framework) {
		WorldHandle world = framework.worlds().builder()
				.withName("airdrop_it_" + Long.toUnsignedString(System.nanoTime(), 36))
				.withWorldType(WorldSpec.WorldType.FLAT)
				.withSeed(26L)
				.build();

		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				world.setBlockAt(new BlockPos(x, PLATFORM_Y, z), "minecraft:stone");
			}
		}
		return world;
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

	private void assertStarterContents(ILightkeeperFramework framework, WorldHandle world) {
		String expectedItems = "{Items:["
				+ "{Slot:0b,id:\"minecraft:iron_helmet\",count:1},"
				+ "{Slot:1b,id:\"minecraft:iron_chestplate\",count:1},"
				+ "{Slot:2b,id:\"minecraft:iron_leggings\",count:1},"
				+ "{Slot:3b,id:\"minecraft:iron_boots\",count:1},"
				+ "{Slot:4b,id:\"minecraft:bread\",count:2}]}";
		String marker = "AIRDR_26_STARTER_CONTENTS_" + Long.toUnsignedString(System.nanoTime(), 36);
		int outputLineCount = framework.server().output().size();

		CommandResult countResult = framework.server().executeCommand(CommandSource.CONSOLE,
				("minecraft:execute in minecraft:%s store result storage airdropmc:lightkeeper "
						+ "item_count int 1 run data get block %d %d %d Items")
						.formatted(world.name(), LANDING_X, BARREL_Y, LANDING_Z));
		assertThat(countResult.success()).as("starter barrel item count read").isTrue();

		CommandResult contentsResult = framework.server().executeCommand(CommandSource.CONSOLE,
				("minecraft:execute if data storage airdropmc:lightkeeper {item_count:5} "
						+ "in minecraft:%s if data block %d %d %d %s run say %s")
						.formatted(world.name(), LANDING_X, BARREL_Y, LANDING_Z, expectedItems, marker));
		assertThat(contentsResult.success()).as("starter barrel contents assertion").isTrue();

		eventually(Duration.ofSeconds(10), () -> {
			List<String> lines = framework.server().output();
			assertThat(lines).hasSizeGreaterThan(outputLineCount);
			assertThat(lines.subList(outputLineCount, lines.size()))
					.anyMatch(line -> line.endsWith("[Server] " + marker));
		});
	}
}
