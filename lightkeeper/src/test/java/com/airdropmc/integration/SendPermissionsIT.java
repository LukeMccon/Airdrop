package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.FreshServer;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.airdropmc.integration.EconomyIntegrationSupport.assertAccountState;
import static com.airdropmc.integration.EconomyIntegrationSupport.resetAccount;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static org.assertj.core.api.Assertions.assertThat;

/** Real LuckPerms effective wildcard/denial checks, isolated from attachment-only scenarios. */
@FreshServer
@ExtendWith(LightkeeperExtension.class)
class SendPermissionsIT {
	private static final List<String> LANDED = List.of("REQUEST", "SPAWNED", "LANDING_ATTEMPT", "LANDED", "OUTCOME");

	@Test
	@Timeout(value = 240, unit = TimeUnit.SECONDS)
	void effectivePermissionsKeepAuthorityPriceAndPlayerAdmissionIndependent(ILightkeeperFramework framework)
			throws Exception {
		AirdropIntegrationSupport.awaitReady(framework, true);
		var configFile = SendIT.configFile(framework);
		byte[] config = Files.readAllBytes(configFile);
		List<PlayerHandle> players = new ArrayList<>();
		WorldHandle world = null;
		try {
			GuiReloadIntegrationSupport.enableEconomyProvider(framework);
			world = SendIT.createWorld(framework);
			PlayerHandle sender = fullLoginPlayer(framework, world, players);
			SendIT.moveToCallerPlatform(sender, world);
			PlayerHandle recipient = fullLoginPlayer(framework, world, players);
			assertThat(sender.permissions().has("airdrop.send")).isTrue();
			assertThat(sender.permissions().has("airdrop.cost.bypass")).isFalse();
			setPermission(framework, sender, "airdrop.package.*", true);
			assertThat(sender.permissions().has("airdrop.cost.bypass")).isFalse();
			setPermission(framework, sender, "airdrop.admin", true);
			assertThat(sender.permissions().has("airdrop.cost.bypass")).isFalse();
			setPermission(framework, sender, "airdrop.*", true);
			assertThat(sender.permissions().has("airdrop.cost.bypass")).isTrue();
			setPermission(framework, sender, "airdrop.cost.bypass", false);
			setPermission(framework, sender, "airdrop.cooldown.bypass", false);
			setPermission(framework, recipient, "airdrop.cost.bypass", true);
			resetAccount(framework, sender.uniqueId(), "100.00");
			resetAccount(framework, recipient.uniqueId(), "7.00");

			// Explicit send denial must override the admin/wildcard grants without charging.
			setPermission(framework, sender, "airdrop.send", false);
			int deniedOffset = framework.server().output().size();
			sender.executeCommand("airdrop send premium " + recipient.name());
			assertAccountState(framework, sender.uniqueId(), "100.00", 0, 0, 0);
			assertThat(AirdropIntegrationSupport.consumerMarkers(framework, deniedOffset)).isEmpty();
			setPermission(framework, sender, "airdrop.send", true);

			int paidOffset = framework.server().output().size();
			sender.executeCommand("airdrop send premium " + recipient.name());
			SendIT.assertOutcome(framework, paidOffset, LANDED, "LANDED", "CHARGED", "NONE");
			assertAccountState(framework, sender.uniqueId(), "89.75", 1, 1, 0);
			assertAccountState(framework, recipient.uniqueId(), "7.00", 0, 0, 0);
			AirdropIntegrationSupport.cleanupCrate(framework, world);

			// Free coordinates use the sender's world and player cooldown, even under a wildcard.
			setPermission(framework, sender, "airdrop.cost.bypass", true);
			setPermission(framework, sender, "airdrop.cooldown.bypass", false);
			int cooldownOffset = framework.server().output().size();
			sender.executeCommand("airdrop send premium 0 0");
			// Full-login bots do not expose chat capture; the shared handle reports early rejections.
			var cooldown = ConsumerIntegrationSupport.request(framework, sender, "premium");
			var rejected = ConsumerIntegrationSupport.awaitHandleResult(framework, cooldown, "HANDLE_OUTCOME");
			assertThat(rejected.required("reason")).isEqualTo("COOLDOWN");
			assertThat(rejected.required("payment")).isEqualTo("NOT_APPLICABLE");
			assertThat(AirdropIntegrationSupport.consumerMarkers(framework, cooldownOffset)).isEmpty();
			assertAccountState(framework, sender.uniqueId(), "89.75", 1, 1, 0);
			setPermission(framework, sender, "airdrop.cooldown.bypass", true);
			int freeOffset = framework.server().output().size();
			sender.executeCommand("airdrop send premium 0 0");
			SendIT.assertOutcome(framework, freeOffset, LANDED, "LANDED", "NOT_APPLICABLE", "NONE");
			assertAccountState(framework, sender.uniqueId(), "89.75", 1, 1, 0);
			AirdropIntegrationSupport.cleanupCrate(framework, world);

			// Catalog/self requests go through the same exempt player purchase path.
			int selfOffset = framework.server().output().size();
			sender.executeCommand("airdrop premium");
			SendIT.assertOutcome(framework, selfOffset, LANDED, "LANDED", "NOT_APPLICABLE", "NONE");
			assertAccountState(framework, sender.uniqueId(), "89.75", 1, 1, 0);
			AirdropIntegrationSupport.cleanupCrate(framework, world);

			// Pre-context rejections report the captured payment policy on both handle stages.
			SendIT.moveToCallerPlatform(sender, world);
			world.setBlockAt(new BlockPos(32, AirdropIntegrationSupport.BARREL_Y + 10, 0), "minecraft:stone");
			for (boolean costExempt : List.of(false, true)) {
				setPermission(framework, sender, "airdrop.cost.bypass", costExempt);
				var request = ConsumerIntegrationSupport.request(framework, sender, "premium");
				String payment = costExempt ? "NOT_APPLICABLE" : "REJECTED";
				var spawn = ConsumerIntegrationSupport.awaitHandleResult(framework, request, "HANDLE_SPAWN");
				assertThat(spawn.required("spawned")).isEqualTo("false");
				assertThat(spawn.required("payment")).isEqualTo(payment);
				var outcome = ConsumerIntegrationSupport.awaitHandleResult(framework, request, "HANDLE_OUTCOME");
				assertThat(outcome.required("delivery")).isEqualTo("REJECTED");
				assertThat(outcome.required("reason")).isEqualTo("SKY_NOT_CLEAR");
				assertThat(outcome.required("payment")).isEqualTo(payment);
				assertAccountState(framework, sender.uniqueId(), "89.75", 1, 1, 0);
			}
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			SendIT.restoreAndCleanup(framework, world, players, configFile, config);
		}
	}

	private static PlayerHandle fullLoginPlayer(ILightkeeperFramework framework, WorldHandle world,
			List<PlayerHandle> players) {
		// LuckPerms injects its permissible during login; legacy synthetic bots skip that event.
		PlayerHandle player = framework.bots().builder().withRandomName()
				.atLocation(world, 0.5, AirdropIntegrationSupport.BARREL_Y, 0.5).fullLogin().build();
		players.add(player);
		return player;
	}

	private static void setPermission(ILightkeeperFramework framework, PlayerHandle player, String node, boolean value) {
		assertThat(framework.server().executeCommand(CommandSource.CONSOLE,
				"lp user " + player.uniqueId() + " permission set " + node + " " + value).success()).isTrue();
		eventually(Duration.ofSeconds(10), () -> assertThat(player.permissions().has(node)).isEqualTo(value));
	}
}
