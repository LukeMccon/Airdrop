package com.airdropmc;

import com.airdropmc.helpers.PermissionsHelper;
import org.bukkit.permissions.PermissionAttachment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirdropOptionalDependenciesTest {

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void startsWithoutLuckPermsAndUsesBukkitPermissionNodes() throws Exception {
		ServerMock server = MockBukkit.mock();
		assertNull(server.getPluginManager().getPlugin("LuckPerms"));

		Airdrop plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(plugin.getDataFolder().toPath());
		Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"),
				"language: en\neconomy:\n  enabled: false\n");
		Files.writeString(plugin.getDataFolder().toPath().resolve("packages.yml"), "packages: {}\n");

		server.getPluginManager().enablePlugin(plugin);
		awaitReady(server, plugin);

		PlayerMock player = server.addPlayer();
		PermissionAttachment attachment = player.addAttachment(plugin);
		attachment.setPermission("airdrop.package.starter", true);
		player.recalculatePermissions();

		assertTrue(plugin.isEnabled());
		assertTrue(PermissionsHelper.hasPermission(player, "starter"));
	}

	private static void awaitReady(ServerMock server, Airdrop plugin) {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline && plugin.isEnabled() && !Airdrop.isReady()) {
			server.getScheduler().performOneTick();
			LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
		}
		assertTrue(Airdrop.isReady(), "Timed out waiting for asynchronous startup");
	}
}
