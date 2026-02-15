package com.airdropmc.commands;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.Config;
import com.airdropmc.lang.LanguageManager;
import com.airdropmc.packages.PackageManager;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CmdAirdropLifecycleSafetyTest {

	private ServerMock server;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void onCommand_version_handlesNullVersionFields() {
		PlayerMock player = server.addPlayer();
		Command command = mock(Command.class);

		try (MockedStatic<Airdrop> airdropMock = Mockito.mockStatic(Airdrop.class)) {
			airdropMock.when(Airdrop::getVersion).thenReturn(null);
			airdropMock.when(Airdrop::getPluginApiVersion).thenReturn(null);

			boolean handled = new CmdAirdrop().onCommand(player, command, "airdrop", new String[]{"version"});

			assertTrue(handled);
		}
	}

	@Test
	void onCommand_reload_handlesUnavailablePluginState() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		Command command = mock(Command.class);

		try (MockedStatic<Airdrop> airdropMock = Mockito.mockStatic(Airdrop.class);
			 MockedStatic<PackageManager> packageManagerMock = Mockito.mockStatic(PackageManager.class)) {
			airdropMock.when(Airdrop::getConfiguration).thenReturn(null);
			airdropMock.when(Airdrop::getPluginInstance).thenReturn(null);

			boolean handled = new CmdAirdrop().onCommand(player, command, "airdrop", new String[]{"reload"});

			assertTrue(handled);
			packageManagerMock.verify(PackageManager::reload, Mockito.never());
		}
	}

	@Test
	void onCommand_reload_stopsWhenPackageReloadUnavailable() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		Command command = mock(Command.class);
		Config configuration = mock(Config.class);
		FileConfiguration fileConfiguration = mock(FileConfiguration.class);
		Airdrop plugin = mock(Airdrop.class);
		LanguageManager languageManager = mock(LanguageManager.class);

		when(configuration.getConfig()).thenReturn(fileConfiguration);
		when(fileConfiguration.getString("language", "en")).thenReturn("en");
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.getLanguageManager()).thenReturn(languageManager);

		try (MockedStatic<Airdrop> airdropMock = Mockito.mockStatic(Airdrop.class);
			 MockedStatic<PackageManager> packageManagerMock = Mockito.mockStatic(PackageManager.class)) {
			airdropMock.when(Airdrop::getConfiguration).thenReturn(configuration);
			airdropMock.when(Airdrop::getPluginInstance).thenReturn(plugin);
			packageManagerMock.when(PackageManager::reload).thenReturn(false);

			boolean handled = new CmdAirdrop().onCommand(player, command, "airdrop", new String[]{"reload"});

			assertTrue(handled);
			verify(configuration).reloadConfig();
			verify(languageManager).loadLanguage("en");
			packageManagerMock.verify(PackageManager::reload);
		}
	}
}
