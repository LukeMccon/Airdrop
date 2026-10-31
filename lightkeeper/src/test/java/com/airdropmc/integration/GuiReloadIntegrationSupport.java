package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static org.assertj.core.api.Assertions.assertThat;

/** Completion barriers for AIRDR-87's GUI and reload scenarios. */
final class GuiReloadIntegrationSupport {
	private GuiReloadIntegrationSupport() {
	}

	static void ordinaryClose(ILightkeeperFramework framework, PlayerHandle player) {
		String token = UUID.randomUUID().toString();
		int offset = framework.server().output().size();
		assertThat(framework.server().executeCommand(CommandSource.CONSOLE,
				"lkmenu-close " + player.uniqueId() + " " + token).success()).isTrue();
		String marker = "AIRDR_MENU_CLOSE token=" + token + " player=" + player.uniqueId() + " success=true";
		eventually(Duration.ofSeconds(10), () -> {
			assertThat(outputSince(framework, offset)).anyMatch(line -> line.endsWith(marker));
			assertThat(player.getMenu()).as("ordinary close completed").isNull();
		});
	}

	static void enableEconomyProvider(ILightkeeperFramework framework) {
		int offset = framework.server().output().size();
		AirdropIntegrationSupport.enableEconomyProvider(framework);
		// The provider-selection log is earlier than completion/publication of the reload.
		awaitSuccessfulReload(framework, offset);
	}

	static void reloadSuccessfully(ILightkeeperFramework framework) {
		int offset = framework.server().output().size();
		assertThat(framework.server().executeCommand(CommandSource.CONSOLE, "airdrop reload").success()).isTrue();
		awaitSuccessfulReload(framework, offset);
	}

	private static void awaitSuccessfulReload(ILightkeeperFramework framework, int offset) {
		// Dispatch acceptance is not completion: wait for the final, post-publication feedback.
		eventually(Duration.ofSeconds(20), () -> assertThat(outputSince(framework, offset))
				.anyMatch(line -> line.contains("Configuration, language, packages, and economy reloaded.")
						|| line.contains("Reload completed, but no economy provider is available; paid drops are blocked")));
	}

	static List<String> outputSince(ILightkeeperFramework framework, int offset) {
		List<String> output = framework.server().output();
		assertThat(output).hasSizeGreaterThanOrEqualTo(offset);
		return output.subList(offset, output.size());
	}
}
