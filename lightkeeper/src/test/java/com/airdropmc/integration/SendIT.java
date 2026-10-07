package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.CapturedEventSnapshot;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.InteractionResult;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PBool;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PEnum;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PRecord;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PRef;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PString;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PUuid;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue.PVec;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.airdropmc.integration.AirdropIntegrationSupport.BARREL_POSITION;
import static com.airdropmc.integration.AirdropIntegrationSupport.BARREL_Y;
import static com.airdropmc.integration.AirdropIntegrationSupport.DROP_EVENT;
import static com.airdropmc.integration.AirdropIntegrationSupport.LAND_EVENT;
import static com.airdropmc.integration.EconomyIntegrationSupport.OPERATION_EVENT;
import static com.airdropmc.integration.EconomyIntegrationSupport.assertAccountState;
import static com.airdropmc.integration.EconomyIntegrationSupport.operationsFor;
import static com.airdropmc.integration.EconomyIntegrationSupport.resetAccount;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static org.assertj.core.api.Assertions.assertThat;

/** AIRDR-73: command delivery on Paper, without claims about native player item transfer. */
@ExtendWith(LightkeeperExtension.class)
class SendIT {
	private static final String PREMIUM_PERMISSION = "airdrop.package.premium";
	private static final String SEND_PERMISSION = "airdrop.send";
	private static final String COST_PERMISSION = "airdrop.cost.bypass";
	private static final BlockPos CALLER_POSITION = new BlockPos(32, BARREL_Y, 0);
	private static final List<String> LANDED_SEQUENCE =
			List.of("REQUEST", "SPAWNED", "LANDING_ATTEMPT", "LANDED", "OUTCOME");

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void defaultSendChargesOnlySenderAndLandsPublicCrateAtRecipientSnapshot(
			ILightkeeperFramework framework) throws Exception {
		AirdropIntegrationSupport.awaitReady(framework);
		Path configFile = configFile(framework);
		byte[] originalConfig = Files.readAllBytes(configFile);
		List<PlayerHandle> players = new ArrayList<>();
		WorldHandle world = null;
		try {
			GuiReloadIntegrationSupport.enableEconomyProvider(framework);
			// Leave enough fall time to observe retention before the landing callback releases it.
			Files.writeString(configFile, new String(originalConfig, StandardCharsets.UTF_8)
					.replace("  height: 8", "  height: 40"), StandardCharsets.UTF_8);
			GuiReloadIntegrationSupport.reloadSuccessfully(framework);
			world = createWorld(framework);
			PlayerHandle sender = player(framework, world, players, PREMIUM_PERMISSION);
			moveToCallerPlatform(sender, world);
			PlayerHandle recipient = player(framework, world, players);
			assertThat(sender.permissions().has(SEND_PERMISSION)).as("send is enabled by default").isTrue();
			assertThat(recipient.permissions().has(PREMIUM_PERMISSION)).isFalse();
			resetAccount(framework, sender.uniqueId(), "100.00");
			resetAccount(framework, recipient.uniqueId(), "7.00");
			// Legacy message-capture bots do not provide Paper's real player chunk tickets.
			PlayerHandle tickingPlayer = framework.bots().builder().withRandomName()
					.atLocation(world, 0.5, BARREL_Y, 0.5).fullLogin().build();
			players.add(tickingPlayer);
			WorldHandle destination = world;
			eventually(Duration.ofSeconds(10), () -> assertThat(
					AirdropIntegrationSupport.remoteFixture(framework, "observe", destination, 0, 0))
					.contains("level=ENTITY_TICKING", "tickets=0"));

			try (var operations = framework.events().capture(OPERATION_EVENT);
				 var drops = framework.events().capture(DROP_EVENT);
				 var landings = framework.events().capture(LAND_EVENT)) {
				int offset = framework.server().output().size();
				int senderMessages = sender.receivedMessages().size();
				int recipientMessages = recipient.receivedMessages().size();
				sender.executeCommand("airdrop send premium " + recipient.name());
				framework.waitUntil(() -> drops.getCapturedEvents().size() == 1, Duration.ofSeconds(20));
				assertThat(AirdropIntegrationSupport.remoteFixture(framework, "observe", world, 0, 0))
						.contains("tickets=1");
				tickingPlayer.remove();
				players.remove(tickingPlayer);
				// Moving after spawn must not retarget the physical crate.
				AirdropIntegrationSupport.moveAway(recipient, world);
				framework.waitUntil(() -> landings.getCapturedEvents().size() == 1, Duration.ofSeconds(30));
				eventually(Duration.ofSeconds(10), () -> assertThat(
						AirdropIntegrationSupport.remoteFixture(framework, "observe", destination, 0, 0))
						.contains("tickets=0", "auxiliaries=0"));
				var outcome = assertOutcome(framework, offset, LANDED_SEQUENCE, "LANDED", "CHARGED", "NONE");
				String request = "Send request " + outcome.requestId();
				awaitMessage(sender, senderMessages, request + ": premium is on its way to "
						+ recipient.name() + ". $10.25 was taken from your account.");
				awaitMessage(sender, senderMessages, request + ": premium for " + recipient.name()
						+ " landed at X: 0, Y: 81, Z: 0 in " + world.name() + ".");
				awaitMessage(recipient, recipientMessages, sender.name()
						+ " is sending you package premium. You will not be charged.");
				awaitMessage(recipient, recipientMessages, sender.name()
						+ " landed at X: 0, Y: 81, Z: 0 in " + world.name() + ".");
				assertLanding(landings.getCapturedEvents().getFirst(), world);
				AirdropIntegrationSupport.assertStarterContents(framework, world, BARREL_POSITION);
				AirdropIntegrationSupport.awaitBlock(world, CALLER_POSITION, "minecraft:air");
				assertAccountState(framework, sender.uniqueId(), "89.75", 1, 1, 0);
				assertAccountState(framework, recipient.uniqueId(), "7.00", 0, 0, 0);
				assertThat(operationsFor(operations.getCapturedEvents(), sender.uniqueId()))
						.hasSize(2).satisfies(events -> {
							assertOperation(events.getFirst(), sender, "CAN_WITHDRAW", "100.00");
							assertOperation(events.getLast(), sender, "WITHDRAW", "89.75");
						});
				assertThat(operationsFor(operations.getCapturedEvents(), recipient.uniqueId())).isEmpty();

				int retryOffset = framework.server().output().size();
				var retry = ConsumerIntegrationSupport.request(framework, sender, "premium");
				var rejected = ConsumerIntegrationSupport.awaitHandleResult(framework, retry, "HANDLE_OUTCOME");
				assertThat(rejected.required("reason")).isEqualTo("COOLDOWN");
				assertThat(AirdropIntegrationSupport.consumerMarkers(framework, retryOffset)).isEmpty();
				assertAccountState(framework, sender.uniqueId(), "89.75", 1, 1, 0);
				assertThat(drops.getCapturedEvents()).hasSize(1);
				assertThat(landings.getCapturedEvents()).hasSize(1);
			}

			// An unrelated unprivileged player may interact with this ordinary public barrel.
			PlayerHandle visitor = player(framework, world, players);
			visitor.teleport(world, 1.5, BARREL_Y, 0.5);
			assertThat(visitor.rightClickBlock(BARREL_POSITION, BlockFace.EAST))
					.isEqualTo(new InteractionResult(true, false));
			AirdropIntegrationSupport.assertStarterContents(framework, world, BARREL_POSITION);
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			restoreAndCleanup(framework, world, players, configFile, originalConfig);
		}
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void strictRecipientPermissionRejectsWithoutMoneyMovementThenAllowsSameSenderRetry(
			ILightkeeperFramework framework) throws Exception {
		AirdropIntegrationSupport.awaitReady(framework);
		Path configFile = configFile(framework);
		byte[] originalConfig = Files.readAllBytes(configFile);
		List<PlayerHandle> players = new ArrayList<>();
		WorldHandle world = null;
		try {
			GuiReloadIntegrationSupport.enableEconomyProvider(framework);
			requireRecipientPermission(framework, configFile);
			world = createWorld(framework);
			PlayerHandle sender = player(framework, world, players, PREMIUM_PERMISSION);
			moveToCallerPlatform(sender, world);
			PlayerHandle recipient = player(framework, world, players);
			resetAccount(framework, sender.uniqueId(), "100.00");
			resetAccount(framework, recipient.uniqueId(), "7.00");

			try (var operations = framework.events().capture(OPERATION_EVENT);
				 var drops = framework.events().capture(DROP_EVENT);
				 var landings = framework.events().capture(LAND_EVENT)) {
				int rejectedOffset = framework.server().output().size();
				int messages = sender.receivedMessages().size();
				sender.executeCommand("airdrop send premium " + recipient.name());
				awaitMessage(sender, messages, recipient.name()
						+ " cannot receive package premium; the server requires recipient package permission.");
				assertThat(AirdropIntegrationSupport.consumerMarkers(framework, rejectedOffset)).isEmpty();
				assertThat(operations.getCapturedEvents()).isEmpty();
				assertAccountState(framework, sender.uniqueId(), "100.00", 0, 0, 0);
				assertAccountState(framework, recipient.uniqueId(), "7.00", 0, 0, 0);
				assertThat(drops.getCapturedEvents()).isEmpty();
				AirdropIntegrationSupport.awaitBlock(world, BARREL_POSITION, "minecraft:air");

				recipient.permissions().grant(PREMIUM_PERMISSION);
				int retryOffset = framework.server().output().size();
				sender.executeCommand("airdrop send premium " + recipient.name());
				framework.waitUntil(() -> drops.getCapturedEvents().size() == 1, Duration.ofSeconds(20));
				AirdropIntegrationSupport.moveAway(recipient, world);
				framework.waitUntil(() -> landings.getCapturedEvents().size() == 1, Duration.ofSeconds(30));
				assertOutcome(framework, retryOffset, LANDED_SEQUENCE, "LANDED", "CHARGED", "NONE");
				assertLanding(landings.getCapturedEvents().getFirst(), world);
				AirdropIntegrationSupport.assertStarterContents(framework, world, BARREL_POSITION);
				assertAccountState(framework, sender.uniqueId(), "89.75", 1, 1, 0);
				assertAccountState(framework, recipient.uniqueId(), "7.00", 0, 0, 0);
				assertThat(operationsFor(operations.getCapturedEvents(), recipient.uniqueId())).isEmpty();
			}
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			restoreAndCleanup(framework, world, players, configFile, originalConfig);
		}
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void explicitSendDenialBlocksAdminButCostExemptSendPreservesEligibility(
			ILightkeeperFramework framework) throws Exception {
		AirdropIntegrationSupport.awaitReady(framework);
		Path configFile = configFile(framework);
		byte[] originalConfig = Files.readAllBytes(configFile);
		List<PlayerHandle> players = new ArrayList<>();
		WorldHandle world = null;
		try {
			disableEconomyProvider(framework);
			requireRecipientPermission(framework, configFile);
			world = createWorld(framework);
			PlayerHandle sender = player(framework, world, players, "airdrop.admin");
			moveToCallerPlatform(sender, world);
			PlayerHandle recipient = player(framework, world, players);
			sender.permissions().revoke(SEND_PERMISSION);
			resetAccount(framework, sender.uniqueId(), "0.00");
			resetAccount(framework, recipient.uniqueId(), "7.00");

			try (var operations = framework.events().capture(OPERATION_EVENT);
				 var drops = framework.events().capture(DROP_EVENT);
				 var landings = framework.events().capture(LAND_EVENT)) {
				int rejectedOffset = framework.server().output().size();
				int messages = sender.receivedMessages().size();
				sender.executeCommand("airdrop send premium " + recipient.name());
				awaitMessage(sender, messages, "You need airdrop.send permission to use this command.");
				assertThat(AirdropIntegrationSupport.consumerMarkers(framework, rejectedOffset)).isEmpty();
				assertThat(operations.getCapturedEvents()).isEmpty();
				assertThat(drops.getCapturedEvents()).isEmpty();

				sender.permissions().revoke("airdrop.admin").grant(SEND_PERMISSION).grant(COST_PERMISSION).grant(PREMIUM_PERMISSION);
				recipient.permissions().grant(PREMIUM_PERMISSION);
				assertThat(sender.permissions().has(PREMIUM_PERMISSION)).isTrue();
				assertThat(recipient.permissions().has(PREMIUM_PERMISSION)).isTrue();
				int offset = framework.server().output().size();
				int senderMessages = sender.receivedMessages().size();
				sender.executeCommand("airdrop send premium " + recipient.name());
				framework.waitUntil(() -> drops.getCapturedEvents().size() == 1, Duration.ofSeconds(20));
				AirdropIntegrationSupport.moveAway(recipient, world);
				framework.waitUntil(() -> landings.getCapturedEvents().size() == 1, Duration.ofSeconds(30));
				var outcome = assertOutcome(framework, offset, LANDED_SEQUENCE, "LANDED", "NOT_APPLICABLE", "NONE");
				awaitMessage(sender, senderMessages, "Send request " + outcome.requestId()
						+ ": premium is on its way to " + recipient.name() + "; no payment was taken.");
				assertLanding(landings.getCapturedEvents().getFirst(), world);
				AirdropIntegrationSupport.assertStarterContents(framework, world, BARREL_POSITION);
				assertAccountState(framework, sender.uniqueId(), "0.00", 0, 0, 0);
				assertAccountState(framework, recipient.uniqueId(), "7.00", 0, 0, 0);
				assertThat(operations.getCapturedEvents()).isEmpty();
			}
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			restoreAndCleanup(framework, world, players, configFile, originalConfig);
		}
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void paidCoordinatesChargeInitiatorAndLandAtCapturedSurface(ILightkeeperFramework framework) throws Exception {
		AirdropIntegrationSupport.awaitReady(framework);
		Path configFile = configFile(framework);
		byte[] originalConfig = Files.readAllBytes(configFile);
		List<PlayerHandle> players = new ArrayList<>();
		WorldHandle world = null;
		try {
			GuiReloadIntegrationSupport.enableEconomyProvider(framework);
			requireRecipientPermission(framework, configFile);
			world = createWorld(framework);
			PlayerHandle sender = player(framework, world, players, PREMIUM_PERMISSION);
			moveToCallerPlatform(sender, world);
			resetAccount(framework, sender.uniqueId(), "100.00");
			try (var drops = framework.events().capture(DROP_EVENT);
				 var landings = framework.events().capture(LAND_EVENT)) {
				// A malformed destination cannot become a paid self-order.
				int invalidMessages = sender.receivedMessages().size();
				sender.executeCommand("airdrop send premium NaN 0 " + world.name());
				awaitMessage(sender, invalidMessages, "Coordinates must be finite absolute X/Z");
				assertAccountState(framework, sender.uniqueId(), "100.00", 0, 0, 0);
				assertThat(drops.getCapturedEvents()).isEmpty();
				int offset = framework.server().output().size();
				sender.executeCommand("airdrop send premium 0 0 " + world.name());
				framework.waitUntil(() -> drops.getCapturedEvents().size() == 1, Duration.ofSeconds(20));
				AirdropIntegrationSupport.moveAway(sender, world);
				framework.waitUntil(() -> landings.getCapturedEvents().size() == 1, Duration.ofSeconds(30));
				assertOutcome(framework, offset, LANDED_SEQUENCE, "LANDED", "CHARGED", "NONE");
				assertLanding(landings.getCapturedEvents().getFirst(), world);
				AirdropIntegrationSupport.assertStarterContents(framework, world, BARREL_POSITION);
				assertAccountState(framework, sender.uniqueId(), "89.75", 1, 1, 0);
			}
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			restoreAndCleanup(framework, world, players, configFile, originalConfig);
		}
	}

	static Path configFile(ILightkeeperFramework framework) {
		return framework.server().pluginDataDirectory("Airdrop").resolve("config.yml");
	}

	private static void requireRecipientPermission(ILightkeeperFramework framework, Path configFile)
			throws Exception {
		String config = Files.readString(configFile, StandardCharsets.UTF_8);
		if (config.contains("require-recipient-permission:")) {
			config = config.replaceFirst("(?m)^(\\s*require-recipient-permission:)[^\\r\\n]*$", "$1 true");
		} else {
			config += "\ngifting:\n  require-recipient-permission: true\n";
		}
		Files.writeString(configFile, config, StandardCharsets.UTF_8);
		GuiReloadIntegrationSupport.reloadSuccessfully(framework);
	}

	static WorldHandle createWorld(ILightkeeperFramework framework) {
		WorldHandle world = AirdropIntegrationSupport.createLandingWorld(framework);
		world.setBlockAt(new BlockPos(CALLER_POSITION.x(), BARREL_Y - 1, CALLER_POSITION.z()), "minecraft:stone");
		return world;
	}

	static PlayerHandle player(ILightkeeperFramework framework, WorldHandle world,
			List<PlayerHandle> players, String... permissions) {
		// Legacy bots capture real outbound chat; the pinned full-login path does not.
		PlayerHandle player = framework.bots().builder().withRandomName()
				.atLocation(world, 0.5, BARREL_Y, 0.5).withPermissions(permissions).build();
		players.add(player);
		return player;
	}

	static void moveToCallerPlatform(PlayerHandle player, WorldHandle world) {
		player.teleport(world, CALLER_POSITION.x() + 0.5, BARREL_Y, CALLER_POSITION.z() + 0.5);
	}

	private static void awaitMessage(PlayerHandle player, int offset, String text) {
		eventually(Duration.ofSeconds(10), () -> assertThat(player.receivedMessages().stream().skip(offset))
				.anyMatch(message -> message.contains(text)));
	}

	private static void disableEconomyProvider(ILightkeeperFramework framework) {
		int offset = framework.server().output().size();
		assertThat(framework.server().executeCommand(CommandSource.CONSOLE, "lkeconomy disable").success()).isTrue();
		eventually(Duration.ofSeconds(10), () -> assertThat(GuiReloadIntegrationSupport.outputSince(framework, offset))
				.anyMatch(line -> line.contains("Disabled LightKeeper economy provider")));
	}

	private static void assertLanding(CapturedEventSnapshot event, WorldHandle world) {
		assertThat(event.value("getWorld")).isInstanceOfSatisfying(PRef.class,
				ref -> assertThat(ref.id()).isEqualTo(world.name()));
		assertThat(event.value("getLandingLocation")).isInstanceOfSatisfying(PRecord.class,
				location -> assertThat(location.fields().get("position")).isEqualTo(new PVec(0, BARREL_Y, 0)));
	}

	private static void assertOperation(CapturedEventSnapshot event, PlayerHandle sender,
			String operation, String balance) {
		assertThat(event.value("getOperation")).isEqualTo(
				new PEnum("com.airdropmc.lightkeeper.economy.EconomyOperationType", operation));
		assertThat(event.value("getCaller")).isEqualTo(new PString("Airdrop"));
		assertThat(event.value("getPlayerId")).isEqualTo(new PUuid(sender.uniqueId()));
		assertThat(event.value("getAmount")).isEqualTo(new PString("10.25"));
		assertThat(event.value("getBalance")).isEqualTo(new PString(balance));
		assertThat(event.value("isSuccessful")).isEqualTo(new PBool(true));
	}

	static AirdropIntegrationSupport.ConsumerMarker assertOutcome(ILightkeeperFramework framework, int offset,
			List<String> sequence, String delivery, String payment, String reason) {
		var markers = AirdropIntegrationSupport.awaitConsumerMarkers(framework, offset, sequence);
		AirdropIntegrationSupport.assertCorrelatedPrimaryThreadSequence(markers);
		var outcome = markers.getLast();
		assertThat(outcome.required("delivery")).isEqualTo(delivery);
		assertThat(outcome.required("payment")).isEqualTo(payment);
		assertThat(outcome.required("reason")).isEqualTo(reason);
		return outcome;
	}

	static void restoreAndCleanup(ILightkeeperFramework framework, WorldHandle world,
			List<PlayerHandle> players, Path configFile, byte[] originalConfig) throws Exception {
		List<Throwable> failures = new ArrayList<>();
		try {
			Files.write(configFile, originalConfig);
		} finally {
			try {
				for (PlayerHandle player : players) {
					try {
						GuiReloadIntegrationSupport.ordinaryClose(framework, player);
						resetAccount(framework, player.uniqueId(), "0.00");
					} catch (RuntimeException | AssertionError failure) {
						failures.add(failure);
					} finally {
						try {
							player.remove();
						} catch (RuntimeException | AssertionError failure) {
							failures.add(failure);
						}
					}
				}
				if (world != null) {
					try {
						framework.server().executeCommand(CommandSource.CONSOLE,
								"airdrop-lightkeeper-block-explode %s 32.5 81.5 0.5 4".formatted(world.name()));
						AirdropIntegrationSupport.awaitBlock(world, CALLER_POSITION, "minecraft:air");
						AirdropIntegrationSupport.cleanupCrate(framework, world);
						AirdropIntegrationSupport.awaitNoDropEntities(world);
					} catch (RuntimeException | AssertionError failure) {
						failures.add(failure);
					}
				}
				disableEconomyProvider(framework);
			} finally {
				// Await restored configuration publication before the next method shares this server.
				GuiReloadIntegrationSupport.reloadSuccessfully(framework);
			}
		}
		assertThat(Files.readAllBytes(configFile)).isEqualTo(originalConfig);
		if (!failures.isEmpty()) {
			AssertionError failure = new AssertionError("Send fixture cleanup failed");
			failures.forEach(failure::addSuppressed);
			throw failure;
		}
	}
}
