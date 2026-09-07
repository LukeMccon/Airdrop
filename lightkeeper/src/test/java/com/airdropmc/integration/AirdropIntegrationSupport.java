package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.CommandResult;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static org.assertj.core.api.Assertions.assertThat;

final class AirdropIntegrationSupport {
	static final String DROP_EVENT = "com.airdropmc.events.PackageDropEvent";
	static final String LAND_EVENT = "com.airdropmc.events.PackageLandEvent";
	static final String CONSUMER_READY = "AIRDR_CONSUMER_READY";
	static final String CONSUMER_REQUEST = "AIRDR_CONSUMER_REQUEST";
	static final String CONSUMER_SPAWNED = "AIRDR_CONSUMER_SPAWNED";
	static final String CONSUMER_LANDING_ATTEMPT = "AIRDR_CONSUMER_LANDING_ATTEMPT";
	static final String CONSUMER_LANDED = "AIRDR_CONSUMER_LANDED";
	static final String CONSUMER_OUTCOME = "AIRDR_CONSUMER_OUTCOME";
	static final List<String> FREE_MARKER_TYPES = List.of(
			"READY", "REQUEST", "SPAWNED", "LANDING_ATTEMPT", "LANDED", "OUTCOME");
	static final List<String> REJECTED_MARKER_TYPES = List.of("REQUEST", "OUTCOME");
	static final String PACKAGE_PERMISSION = "airdrop.package.starter";
	static final String PAID_PACKAGE_PERMISSION = "airdrop.package.paid";
	static final int LANDING_X = 0;
	static final int PLATFORM_Y = 80;
	static final int BARREL_Y = PLATFORM_Y + 1;
	static final int LANDING_Z = 0;
	static final BlockPos BARREL_POSITION = new BlockPos(LANDING_X, BARREL_Y, LANDING_Z);
	static final BlockPos PLATFORM_POSITION = new BlockPos(LANDING_X, PLATFORM_Y, LANDING_Z);

	private static final BlockPos DROP_ENTITY_MIN = new BlockPos(-64, PLATFORM_Y - 8, -64);
	private static final BlockPos DROP_ENTITY_MAX = new BlockPos(64, PLATFORM_Y + 80, 64);
	private static final Pattern CONSUMER_MARKER = Pattern.compile(
			"\\b(" + String.join("|", List.of(
					CONSUMER_READY,
					CONSUMER_REQUEST,
					CONSUMER_SPAWNED,
					CONSUMER_LANDING_ATTEMPT,
					CONSUMER_LANDED,
					CONSUMER_OUTCOME)) + ")\\b");
	private static final Pattern MARKER_ATTRIBUTE = Pattern.compile(
			"\\b(requestId|sequence|primaryThread|delivery|payment|reason)=([^\\s]+)");
	private static final String STARTER_ITEMS_EXACT = "{Items:["
			+ "{Slot:0b,id:\"minecraft:iron_helmet\",count:1},"
			+ "{Slot:1b,id:\"minecraft:iron_chestplate\",count:1},"
			+ "{Slot:2b,id:\"minecraft:iron_leggings\",count:1},"
			+ "{Slot:3b,id:\"minecraft:iron_boots\",count:1},"
			+ "{Slot:4b,id:\"minecraft:bread\",count:2}]}";
	private static final String STARTER_ITEMS_SEMANTIC = "{Items:["
			+ "{id:\"minecraft:iron_helmet\",count:1},"
			+ "{id:\"minecraft:iron_chestplate\",count:1},"
			+ "{id:\"minecraft:iron_leggings\",count:1},"
			+ "{id:\"minecraft:iron_boots\",count:1},"
			+ "{id:\"minecraft:bread\",count:2}]}";
	private static final Map<String, Integer> STARTER_EXPLOSION_DROPS = Map.of(
			"minecraft:barrel", 1,
			"minecraft:iron_helmet", 1,
			"minecraft:iron_chestplate", 1,
			"minecraft:iron_leggings", 1,
			"minecraft:iron_boots", 1,
			"minecraft:bread", 2);

	private AirdropIntegrationSupport() {
	}

	static void awaitReady(ILightkeeperFramework framework) {
		eventually(Duration.ofSeconds(20), () ->
				assertThat(framework.server().output())
						.anyMatch(line -> line.contains(
								"No economy provider is available; paid drops are blocked")));
		nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat(framework)
				.isPaper();
		assertThat(framework.server().plugin("Airdrop"))
				.hasValueSatisfying(plugin -> assertThat(plugin.isEnabled()).isTrue());
		assertThat(framework.server().plugin("AirdropConsumerFixture"))
				.hasValueSatisfying(plugin -> assertThat(plugin.isEnabled()).isTrue());
		assertThat(framework.server().plugin("Vault"))
				.hasValueSatisfying(plugin -> assertThat(plugin.isEnabled()).isTrue());
		assertThat(framework.server().plugin("LuckPerms")).isEmpty();
		awaitConsumerMarkers(framework, 0, List.of("READY"));
	}

	static void enableEconomyProvider(ILightkeeperFramework framework) {
		int outputLineCount = framework.server().output().size();
		CommandResult enable = framework.server().executeCommand(
				CommandSource.CONSOLE, "lkeconomy enable");
		assertThat(enable.success()).as("enable fixture economy provider").isTrue();
		CommandResult reload = framework.server().executeCommand(
				CommandSource.CONSOLE, "airdrop reload");
		assertThat(reload.success()).as("reload Airdrop economy provider").isTrue();
		eventually(Duration.ofSeconds(20), () ->
				assertThat(newOutput(framework, outputLineCount))
						.anyMatch(line -> line.contains(
								"Using economy provider: LightKeeper Economy")));
	}

	static WorldHandle createLandingWorld(ILightkeeperFramework framework) {
		WorldHandle world = framework.worlds().builder()
				.withRandomName()
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

	static void keepLandingChunkLoaded(ILightkeeperFramework framework, WorldHandle world) {
		CommandResult result = framework.server().executeCommand(
				CommandSource.CONSOLE,
				"minecraft:execute in minecraft:%s run forceload add 0 0".formatted(world.name()));
		assertThat(result.success()).as("force-load consumer lifecycle landing chunk").isTrue();
	}

	static PlayerHandle createPlayer(
			ILightkeeperFramework framework,
			WorldHandle world,
			String... permissions
	) {
		return framework.bots().builder()
				.withRandomName()
				.atLocation(world, LANDING_X + 0.5, BARREL_Y, LANDING_Z + 0.5)
				.withPermissions(permissions)
				.fullLogin()
				.build();
	}

	static void moveAway(PlayerHandle player, WorldHandle world) {
		player.teleport(world, 10.5, BARREL_Y, 10.5);
	}

	static void awaitBlock(WorldHandle world, BlockPos position, String material) {
		eventually(Duration.ofSeconds(10), () ->
				nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat(world)
						.hasBlockAt(position)
						.ofType(material));
	}

	static void awaitNoDropEntities(WorldHandle world) {
		awaitNoEntities(world, "minecraft:falling_block", "minecraft:chicken", "minecraft:slime");
	}

	static void awaitNoEntities(WorldHandle world, String... entityTypes) {
		eventually(Duration.ofSeconds(10), () -> {
			for (String entityType : entityTypes) {
				assertThat(world.entities()
						.ofType(entityType)
						.within(DROP_ENTITY_MIN, DROP_ENTITY_MAX)
						.count())
						.as("%s entities near the airdrop", entityType)
						.isZero();
			}
		});
	}

	static void placeStarterBarrel(
			ILightkeeperFramework framework,
			WorldHandle world,
			BlockPos position
	) {
		CommandResult blockResult = framework.server().executeCommand(CommandSource.CONSOLE,
				("minecraft:execute in minecraft:%s run setblock %d %d %d minecraft:barrel")
						.formatted(world.name(), position.x(), position.y(), position.z()));
		assertThat(blockResult.success()).as("starter barrel block at %s", position).isTrue();
		CommandResult contentsResult = framework.server().executeCommand(CommandSource.CONSOLE,
				("minecraft:execute in minecraft:%s run data merge block %d %d %d %s")
						.formatted(world.name(), position.x(), position.y(), position.z(),
								STARTER_ITEMS_EXACT));
		assertThat(contentsResult.success()).as("starter barrel contents at %s", position).isTrue();
	}

	static void isolateExplosionTarget(WorldHandle world, BlockPos position) {
		int supportY = position.y() - 1;
		for (int x = position.x() - 2; x <= position.x() + 2; x++) {
			for (int z = position.z() - 2; z <= position.z() + 2; z++) {
				world.setBlockAt(new BlockPos(x, supportY, z), "minecraft:air");
			}
		}
		world.setBlockAt(new BlockPos(position.x(), supportY, position.z()), "minecraft:bedrock");
	}

	static void setExplosionDropDecay(
			ILightkeeperFramework framework,
			WorldHandle world,
			String gameRule
	) {
		CommandResult result = framework.server().executeCommand(CommandSource.CONSOLE,
				"minecraft:execute in minecraft:%s run gamerule %s false"
						.formatted(world.name(), gameRule));
		assertThat(result.success()).as("%s gamerule", gameRule).isTrue();
	}

	static void awaitEquivalentExplosionDrops(
			ILightkeeperFramework framework,
			WorldHandle world,
			BlockPos trackedPosition,
			BlockPos baselinePosition
	) {
		eventually(Duration.ofSeconds(10), () -> {
			int trackedDrops = itemEntitiesNear(world, trackedPosition);
			int baselineDrops = itemEntitiesNear(world, baselinePosition);
			assertThat(trackedDrops).as("tracked barrel drops").isEqualTo(6);
			assertThat(baselineDrops).as("unmanaged barrel drops").isEqualTo(trackedDrops);
		});

		assertStarterExplosionDrops(framework, world, trackedPosition);
		assertStarterExplosionDrops(framework, world, baselinePosition);
	}

	static void assertStarterContents(ILightkeeperFramework framework, WorldHandle world, BlockPos position) {
		String marker = uniqueMarker("STARTER_CONTENTS");
		int outputLineCount = framework.server().output().size();

		storeContainerItemCount(framework, world, position, "item_count");
		CommandResult contentsResult = framework.server().executeCommand(CommandSource.CONSOLE,
				("minecraft:execute if data storage airdropmc:lightkeeper {item_count:5} "
						+ "in minecraft:%s if data block %d %d %d %s run say %s")
						.formatted(world.name(), position.x(), position.y(), position.z(),
								STARTER_ITEMS_EXACT, marker));
		assertThat(contentsResult.success()).as("starter container contents assertion").isTrue();

		awaitMarker(framework, outputLineCount, marker);
	}

	static void awaitStarterContentsInDrainedChest(
			ILightkeeperFramework framework,
			WorldHandle world,
			BlockPos chestPosition,
			BlockPos hopperPosition
	) {
		String marker = uniqueMarker("HOPPER_CONTENTS");
		int outputLineCount = framework.server().output().size();

		eventually(Duration.ofSeconds(30), () -> {
			storeContainerItemCount(framework, world, chestPosition, "chest_stack_count");
			storeContainerItemCount(framework, world, hopperPosition, "hopper_stack_count");
			CommandResult contentsResult = framework.server().executeCommand(CommandSource.CONSOLE,
					("minecraft:execute if data storage airdropmc:lightkeeper "
							+ "{chest_stack_count:5,hopper_stack_count:0} in minecraft:%s "
							+ "if data block %d %d %d %s run say %s")
							.formatted(world.name(), chestPosition.x(), chestPosition.y(), chestPosition.z(),
									STARTER_ITEMS_SEMANTIC, marker));
			assertThat(contentsResult.success()).as("drained starter contents assertion").isTrue();
			assertThat(newOutput(framework, outputLineCount))
					.anyMatch(line -> line.endsWith("[Server] " + marker));
		});
	}

	static void assertNoUnexpectedServerErrors(ILightkeeperFramework framework) {
		nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat(framework)
				.hasNoServerErrors(error -> isExpectedServerError(error.loggerName(), error.message()));
	}

	static boolean isExpectedServerError(String loggerName, String message) {
		// Paper emits this while LightKeeper creates a valid flat test world.
		if ("net.minecraft.server.dedicated.DedicatedServerProperties".equals(loggerName)
				&& "No key layers in MapLike[{}]".equals(message)) {
			return true;
		}
		// Offline-mode integration tests do not use Paper's background Mojang key fetch.
		return "com.mojang.authlib.yggdrasil.YggdrasilServicesKeyInfo".equals(loggerName)
				&& "Failed to request yggdrasil public key".equals(message);
	}

	static List<ConsumerMarker> awaitConsumerMarkers(
			ILightkeeperFramework framework,
			int outputLineCount,
			List<String> expectedTypes
	) {
		eventually(Duration.ofSeconds(30), () ->
				assertThat(consumerMarkers(framework, outputLineCount))
						.extracting(ConsumerMarker::type)
						.containsExactlyElementsOf(expectedTypes));
		return consumerMarkers(framework, outputLineCount);
	}

	static List<ConsumerMarker> consumerMarkers(
			ILightkeeperFramework framework,
			int outputLineCount
	) {
		List<ConsumerMarker> markers = new ArrayList<>();
		for (String line : newOutput(framework, outputLineCount)) {
			Matcher markerMatcher = CONSUMER_MARKER.matcher(line);
			if (!markerMatcher.find()) {
				continue;
			}

			Map<String, String> values = new LinkedHashMap<>();
			Matcher attributeMatcher = MARKER_ATTRIBUTE.matcher(line.substring(markerMatcher.end()));
			while (attributeMatcher.find()) {
				values.put(attributeMatcher.group(1), attributeMatcher.group(2));
			}
			markers.add(new ConsumerMarker(
					markerMatcher.group(1).substring("AIRDR_CONSUMER_".length()),
					Map.copyOf(values)));
		}
		return List.copyOf(markers);
	}

	static void assertCorrelatedPrimaryThreadSequence(List<ConsumerMarker> markers) {
		List<ConsumerMarker> requestMarkers = markers.stream()
				.filter(marker -> !"READY".equals(marker.type()))
				.toList();
		assertThat(requestMarkers).isNotEmpty();
		assertThat(requestMarkers)
				.extracting(ConsumerMarker::requestId)
				.containsOnly(requestMarkers.getFirst().requestId());
		assertThat(markers)
				.extracting(ConsumerMarker::sequence)
				.doesNotHaveDuplicates()
				.isSorted();
		assertThat(markers)
				.extracting(marker -> marker.required("primaryThread"))
				.containsOnly("true");
	}

	private static void assertStarterExplosionDrops(
			ILightkeeperFramework framework,
			WorldHandle world,
			BlockPos position
	) {
		StringBuilder conditions = new StringBuilder();
		for (Map.Entry<String, Integer> expectedDrop : STARTER_EXPLOSION_DROPS.entrySet()) {
			conditions.append(" if entity @e[type=minecraft:item,distance=..6,nbt={Item:{id:\"")
					.append(expectedDrop.getKey())
					.append("\",count:")
					.append(expectedDrop.getValue())
					.append("}}]");
		}

		String marker = uniqueMarker("EXPLOSION_DROPS");
		int outputLineCount = framework.server().output().size();
		CommandResult result = framework.server().executeCommand(CommandSource.CONSOLE,
				("minecraft:execute in minecraft:%s positioned %d %d %d%s run say %s")
						.formatted(world.name(), position.x(), position.y(), position.z(),
								conditions, marker));
		assertThat(result.success()).as("starter drops near %s", position).isTrue();
		awaitMarker(framework, outputLineCount, marker);
	}

	private static int itemEntitiesNear(WorldHandle world, BlockPos position) {
		BlockPos minimum = new BlockPos(position.x() - 6, position.y() - 6, position.z() - 6);
		BlockPos maximum = new BlockPos(position.x() + 6, position.y() + 6, position.z() + 6);
		return world.entities().ofType("minecraft:item").within(minimum, maximum).count();
	}

	private static void storeContainerItemCount(
			ILightkeeperFramework framework,
			WorldHandle world,
			BlockPos position,
			String storageKey
	) {
		CommandResult result = framework.server().executeCommand(CommandSource.CONSOLE,
				("minecraft:execute in minecraft:%s store result storage airdropmc:lightkeeper "
						+ "%s int 1 run data get block %d %d %d Items")
						.formatted(world.name(), storageKey, position.x(), position.y(), position.z()));
		assertThat(result.success()).as("container item count read at %s", position).isTrue();
	}

	private static void awaitMarker(ILightkeeperFramework framework, int outputLineCount, String marker) {
		eventually(Duration.ofSeconds(10), () ->
				assertThat(newOutput(framework, outputLineCount))
						.anyMatch(line -> line.endsWith("[Server] " + marker)));
	}

	private static List<String> newOutput(ILightkeeperFramework framework, int outputLineCount) {
		List<String> lines = framework.server().output();
		assertThat(lines).hasSizeGreaterThanOrEqualTo(outputLineCount);
		return lines.subList(outputLineCount, lines.size());
	}

	private static String uniqueMarker(String description) {
		return "AIRDR_26_" + description + "_" + Long.toUnsignedString(System.nanoTime(), 36);
	}

	record ConsumerMarker(String type, Map<String, String> values) {
		ConsumerMarker {
			values = Map.copyOf(values);
		}

		String required(String key) {
			String value = values.get(key);
			assertThat(value).as("%s attribute on %s marker", key, type).isNotBlank();
			return value;
		}

		UUID requestId() {
			return UUID.fromString(required("requestId"));
		}

		long sequence() {
			return Long.parseLong(required("sequence"));
		}
	}
}
