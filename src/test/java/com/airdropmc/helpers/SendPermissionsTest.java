package com.airdropmc.helpers;

import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SendPermissionsTest {
	private ServerMock server;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		server.getPluginManager().addPermission(new Permission("airdrop.send", PermissionDefault.TRUE));
		server.getPluginManager().addPermission(new Permission("airdrop.cost.bypass", PermissionDefault.FALSE));
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void consoleAndRconAreExemptWithoutChangingBukkitPermissions() {
		for (var type : List.of(ConsoleCommandSender.class, RemoteConsoleCommandSender.class)) {
			CommandSender console = mock(type);
			PermissibleBase permissions = permissions(console);
			assertFalse(permissions.hasPermission("airdrop.cost.bypass"));
			assertFalse(permissions.isPermissionSet("airdrop.cost.bypass"));
			assertTrue(SendPermissions.isCostExempt(console));
			assertTrue(SendPermissions.canSend(console));
		}
	}

	@Test
	void directAndInheritedEffectiveDenialsSurvivePaperAllPermissionsOverride() {
		for (var type : List.of(ConsoleCommandSender.class, RemoteConsoleCommandSender.class)) {
			CommandSender console = mock(type);
			PermissibleBase permissions = permissions(console);
			doReturn(true).when(console).hasPermission(anyString());
			var plugin = MockBukkit.createMockPlugin();
			var attachment = permissions.addAttachment(plugin, "airdrop.send", false);
			assertTrue(console.hasPermission("airdrop.send"));
			assertFalse(SendPermissions.hasSendPermission(console));
			assertFalse(SendPermissions.canSend(console));
			permissions.removeAttachment(attachment);
			attachment = permissions.addAttachment(plugin, "airdrop.cost.bypass", false);
			assertTrue(console.hasPermission("airdrop.cost.bypass"));
			assertFalse(SendPermissions.isCostExempt(console));
			assertFalse(SendPermissions.canSend(console));
			permissions.removeAttachment(attachment);
			String parent = "test.console." + type.getSimpleName().toLowerCase(java.util.Locale.ROOT);
			server.getPluginManager().addPermission(new Permission(parent, PermissionDefault.FALSE,
					Map.of("airdrop.cost.bypass", true, "airdrop.send", true)));
			attachment = permissions.addAttachment(plugin, parent, false);
			assertFalse(SendPermissions.isCostExempt(console));
			assertFalse(SendPermissions.hasSendPermission(console));
			// A later effective grant overrides an older denial, following Bukkit's actual result.
			permissions.addAttachment(plugin, "airdrop.cost.bypass", true);
			assertTrue(SendPermissions.isCostExempt(console));
			assertFalse(SendPermissions.canSend(console));
			permissions.addAttachment(plugin, "airdrop.send", true);
			assertTrue(SendPermissions.canSend(console));
		}
	}

	@Test
	void playersAndOtherSendersOnlyUseTheirEffectiveCostPermission() {
		var player = server.addPlayer();
		player.setOp(true);
		player.addAttachment(MockBukkit.createMockPlugin(), "airdrop.admin", true);
		assertTrue(SendPermissions.canSend(player));
		assertFalse(SendPermissions.isCostExempt(player));
		player.addAttachment(MockBukkit.createMockPlugin(), "airdrop.cost.bypass", true);
		assertTrue(SendPermissions.isCostExempt(player));
		for (var type : List.of(CommandSender.class, BlockCommandSender.class)) {
			CommandSender other = mock(type);
			PermissibleBase permissions = permissions(other);
			assertFalse(SendPermissions.isCostExempt(other));
			assertFalse(SendPermissions.canSend(other));
			permissions.addAttachment(MockBukkit.createMockPlugin(), "airdrop.cost.bypass", true);
			assertTrue(SendPermissions.canSend(other));
		}
	}

	private static PermissibleBase permissions(CommandSender sender) {
		when(sender.isOp()).thenReturn(true);
		PermissibleBase permissions = new PermissibleBase(sender);
		when(sender.hasPermission(anyString())).thenAnswer(call -> permissions.hasPermission((String) call.getArgument(0)));
		when(sender.getEffectivePermissions()).thenAnswer(call -> permissions.getEffectivePermissions());
		return permissions;
	}
}
