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
			sender.executeCommand("airdrop send premium 4104 4104 " + world.name());
			SendIT.assertOutcome(framework, offset, LANDED, "LANDED", "CHARGED", "NONE");
			EconomyIntegrationSupport.assertAccountState(framework, sender.uniqueId(), "89.75", 1, 1, 0);
			// Reload for a positive post-delivery observation even if Paper has already unloaded it.
			world.loadChunk(256, 256);
			AirdropIntegrationSupport.assertStarterContents(framework, world,
					new nl.pim16aap2.lightkeeper.framework.BlockPos(4104, 81, 4104));
			assertThat(AirdropIntegrationSupport.remoteFixture(framework, "observe", world, 4104, 4104)).contains("tickets=0", "auxiliaries=0");

			PlayerHandle denied = SendIT.player(framework, world, players, "airdrop.package.premium");
			EconomyIntegrationSupport.resetAccount(framework, denied.uniqueId(), "100.00");
			assertThat(AirdropIntegrationSupport.remoteFixture(framework, "observe", world, 8200, 8200)).contains("generated=false", "loaded=false");
			int messages = denied.receivedMessages().size();
			denied.executeCommand("airdrop send premium 8200 8200 " + world.name());
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
			generating.executeCommand("airdrop send premium 8200 8200 " + world.name());
			SendIT.assertOutcome(framework, offset, LANDED, "LANDED", "CHARGED", "NONE");
			EconomyIntegrationSupport.assertAccountState(framework, generating.uniqueId(), "89.75", 1, 1, 0);
			assertThat(AirdropIntegrationSupport.remoteFixture(framework, "observe", world, 8200, 8200)).contains("generated=true", "tickets=0", "auxiliaries=0");
			AirdropIntegrationSupport.remoteFixture(framework, "border", world, 10_000, 0);
			PlayerHandle edge = SendIT.player(framework, world, players, "airdrop.package.premium");
			EconomyIntegrationSupport.resetAccount(framework, edge.uniqueId(), "100.00");
			assertThat(AirdropIntegrationSupport.remoteFixture(framework, "observe", world, 5016, 0)).contains("generated=false");
			int edgeMessages = edge.receivedMessages().size();
			edge.executeCommand("airdrop send premium 4990 0 " + world.name());
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
