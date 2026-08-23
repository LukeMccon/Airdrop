package com.airdropmc.helpers;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionsHelperTest {

	@Test
	void hasCooldownBypass_checksOnlyNarrowPermission() {
		Player player = mock(Player.class);
		when(player.hasPermission("airdrop.cooldown.bypass")).thenReturn(true);

		assertTrue(PermissionsHelper.hasCooldownBypass(player));
		verify(player).hasPermission("airdrop.cooldown.bypass");
		verify(player, never()).hasPermission("airdrop.admin");
	}

	private Locale defaultLocale;

	@BeforeEach
	void setUp() {
		defaultLocale = Locale.getDefault();
	}

	@AfterEach
	void tearDown() {
		Locale.setDefault(defaultLocale);
	}

	@Test
	void isAdmin_allowsNonPlayerSenderWithAdminPermission() {
		CommandSender sender = mock(CommandSender.class);
		when(sender.hasPermission("airdrop.admin")).thenReturn(true);

		assertTrue(PermissionsHelper.isAdmin(sender));
	}

	@Test
	void isAdmin_rejectsNonPlayerSenderWithoutAdminPermission() {
		CommandSender sender = mock(CommandSender.class);
		when(sender.hasPermission("airdrop.admin")).thenReturn(false);

		assertFalse(PermissionsHelper.isAdmin(sender));
	}

	@Test
	void hasPermission_usesLocaleRootWhenNormalizingPackagePermissionNode() {
		Locale.setDefault(Locale.forLanguageTag("tr-TR"));

		Player player = mock(Player.class);
		when(player.hasPermission("airdrop.admin")).thenReturn(false);
		when(player.isOp()).thenReturn(false);
		when(player.hasPermission("airdrop.package.title")).thenReturn(true);

		assertTrue(PermissionsHelper.hasPermission(player, "TITLE"));
		verify(player).hasPermission("airdrop.package.title");
		verify(player, never()).hasPermission("airdrop.package.t\u0131tle");
	}

	@Test
	void hasPermission_supportsExactCasePermissionNodeAsFallback() {
		Player player = mock(Player.class);
		when(player.hasPermission("airdrop.admin")).thenReturn(false);
		when(player.isOp()).thenReturn(false);
		when(player.hasPermission("airdrop.package.MixedCase")).thenReturn(true);

		assertTrue(PermissionsHelper.hasPermission(player, "MixedCase"));
	}
}
