package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** AIRDR-73: real Paper terrain preparation and entity ticking, with no nearby player. */
@ExtendWith(LightkeeperExtension.class)
class RemoteDeliveryIT {
	private static final List<String> LANDED = List.of("REQUEST", "SPAWNED", "LANDING_ATTEMPT", "LANDED", "OUTCOME");

	@Test
	@Timeout(value = 60, unit = TimeUnit.SECONDS)
	void nativeHolderLossSuppressesOnlyParachuteLeadDrops(ILightkeeperFramework framework) {
		AirdropIntegrationSupport.awaitReady(framework);
		WorldHandle world = SendIT.createWorld(framework);
		List<PlayerHandle> players = new ArrayList<>();
		try {
			SendIT.player(framework, world, players).teleport(world, 0.5, 200, 0.5);
			String result = AirdropIntegrationSupport.remoteFixture(framework, "holder-loss", world, 0, 0);
			assertThat(result).as("ordinary chickens retain native lead drops")
					.contains("controlEvent=HOLDER_GONE:true", "controlLeadItems=1");
			assertThat(result).as("Airdrop parachutes never drop leads when their real holder disappears")
					.contains("parachuteEvent=HOLDER_GONE:false", "parachuteLeadItems=0");
		} finally {
			players.forEach(PlayerHandle::remove);
			eventually(Duration.ofSeconds(10), () -> {
				for (String type : List.of("minecraft:falling_block", "minecraft:chicken", "minecraft:slime", "minecraft:item")) {
					assertThat(world.entities().ofType(type)
							.within(new nl.pim16aap2.lightkeeper.framework.BlockPos(-16, 160, -16),
									new nl.pim16aap2.lightkeeper.framework.BlockPos(24, 240, 16)).count())
							.as("holder-loss fixture cleans up %s entities and dropped leads", type).isZero();
				}
			});
		}
	}

	@Test
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void remoteDeliveryUsesExistingTerrainAndOnlyGeneratesAfterOptIn(ILightkeeperFramework framework) throws Exception {
		AirdropIntegrationSupport.awaitReady(framework);
		var configFile = SendIT.configFile(framework);
		byte[] originalConfig = Files.readAllBytes(configFile);
		WorldHandle world = SendIT.createWorld(framework);
		List<PlayerHandle> players = new ArrayList<>();
		try {
			GuiReloadIntegrationSupport.enableEconomyProvider(framework);
			AirdropIntegrationSupport.remoteFixture(framework, "prepare", world, 4104, 4104);
			eventually(Duration.ofSeconds(15), () -> {
				assertThatCode(() -> world.unloadChunk(256, 256)).doesNotThrowAnyException();
				assertThat(world.isChunkLoaded(256, 256)).isFalse();
			});
			PlayerHandle sender = SendIT.player(framework, world, players, "airdrop.package.premium");
			EconomyIntegrationSupport.resetAccount(framework, sender.uniqueId(), "100.00");
			int offset = framework.server().output().size();
			sender.executeCommand("airdrop send premium at 4104 4104 " + world.name());
			SendIT.assertOutcome(framework, offset, LANDED, "LANDED", "CHARGED", "NONE");
			EconomyIntegrationSupport.assertAccountState(framework, sender.uniqueId(), "89.75", 1, 1, 0);
			// Reload for observation even if Paper has already unloaded the landing chunk.
			world.loadChunk(256, 256);
			AirdropIntegrationSupport.assertStarterContents(framework, world,
					new nl.pim16aap2.lightkeeper.framework.BlockPos(4104, 81, 4104));
			assertThat(AirdropIntegrationSupport.remoteFixture(framework, "observe", world, 4104, 4104))
					.as("remote landing cleans up parachutes without dropping leads in the isolated world")
					.contains("tickets=0", "auxiliaries=0", "leadItems=0");

			PlayerHandle denied = SendIT.player(framework, world, players, "airdrop.package.premium");
			EconomyIntegrationSupport.resetAccount(framework, denied.uniqueId(), "100.00");
			assertThat(AirdropIntegrationSupport.remoteFixture(framework, "observe", world, 8200, 8200)).contains("generated=false", "loaded=false");
			int messages = denied.receivedMessages().size();
			denied.executeCommand("airdrop send premium at 8200 8200 " + world.name());
			eventually(Duration.ofSeconds(15), () -> {
				List<String> receivedMessages = denied.receivedMessages();
				assertThat(receivedMessages.subList(messages, receivedMessages.size()))
						.anyMatch(text -> text.contains("generation is disabled"));
			});
			assertThat(AirdropIntegrationSupport.remoteFixture(framework, "observe", world, 8200, 8200)).contains("generated=false", "tickets=0");
			EconomyIntegrationSupport.assertAccountState(framework, denied.uniqueId(), "100.00", 0, 0, 0);

			AirdropIntegrationSupport.remoteFixture(framework, "generation", world, 1, 0);
			PlayerHandle generating = SendIT.player(framework, world, players, "airdrop.package.premium");
			EconomyIntegrationSupport.resetAccount(framework, generating.uniqueId(), "100.00");
			offset = framework.server().output().size();
			generating.executeCommand("airdrop send premium at 8200 8200 " + world.name());
			SendIT.assertOutcome(framework, offset, LANDED, "LANDED", "CHARGED", "NONE");
			EconomyIntegrationSupport.assertAccountState(framework, generating.uniqueId(), "89.75", 1, 1, 0);
			world.loadChunk(512, 512);
			assertThat(AirdropIntegrationSupport.remoteFixture(framework, "observe", world, 8200, 8200))
					.as("generated-terrain landing also leaves no parachute lead items")
					.contains("generated=true", "tickets=0", "auxiliaries=0", "leadItems=0");
			AirdropIntegrationSupport.remoteFixture(framework, "border", world, 10_000, 0);
			PlayerHandle edge = SendIT.player(framework, world, players, "airdrop.package.premium");
			EconomyIntegrationSupport.resetAccount(framework, edge.uniqueId(), "100.00");
			assertThat(AirdropIntegrationSupport.remoteFixture(framework, "observe", world, 5016, 0)).contains("generated=false");
			int edgeMessages = edge.receivedMessages().size();
			edge.executeCommand("airdrop send premium at 4990 0 " + world.name());
			eventually(Duration.ofSeconds(10), () -> {
				List<String> receivedMessages = edge.receivedMessages();
				assertThat(receivedMessages.subList(edgeMessages, receivedMessages.size()))
						.anyMatch(text -> text.contains("outside the world border"));
			});
			assertThat(AirdropIntegrationSupport.remoteFixture(framework, "observe", world, 4990, 0)).contains("generated=false", "tickets=0");
			assertThat(AirdropIntegrationSupport.remoteFixture(framework, "observe", world, 5016, 0)).contains("generated=false", "tickets=0");
			EconomyIntegrationSupport.assertAccountState(framework, edge.uniqueId(), "100.00", 0, 0, 0);

			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			try {
				for (int coordinate : List.of(4104, 8200)) {
					world.loadChunk(coordinate >> 4, coordinate >> 4);
					framework.server().executeCommand(CommandSource.CONSOLE,
							"airdrop-lightkeeper-block-explode %s %s 81.5 %s 4"
									.formatted(world.name(), coordinate + 0.5, coordinate + 0.5));
					AirdropIntegrationSupport.awaitBlock(world,
							new nl.pim16aap2.lightkeeper.framework.BlockPos(coordinate, 81, coordinate), "minecraft:air");
				}
			} finally {
				SendIT.restoreAndCleanup(framework, world, players, configFile, originalConfig);
			}
		}
	}

}
