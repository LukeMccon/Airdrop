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

class CmdAirdropReloadTest {

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
	void onCommand_reload_reloadsConfigLanguageAndPackages() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);

		Command command = mock(Command.class);
		Config config = mock(Config.class);
		FileConfiguration fileConfig = mock(FileConfiguration.class);
		LanguageManager languageManager = mock(LanguageManager.class);
		Airdrop plugin = mock(Airdrop.class);

		when(config.getConfig()).thenReturn(fileConfig);
		when(fileConfig.getString("language", "en")).thenReturn("en");
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.getLanguageManager()).thenReturn(languageManager);

		try (MockedStatic<Airdrop> airdropMock = Mockito.mockStatic(Airdrop.class);
				MockedStatic<PackageManager> packageManagerMock = Mockito.mockStatic(PackageManager.class)) {
			airdropMock.when(Airdrop::getConfiguration).thenReturn(config);
			airdropMock.when(Airdrop::getPluginInstance).thenReturn(plugin);
			packageManagerMock.when(PackageManager::reload).thenReturn(true);

			boolean handled = new CmdAirdrop().onCommand(player, command, "airdrop", new String[]{"reload"});

			assertTrue(handled);
			verify(config).reloadConfig();
			verify(languageManager).loadLanguage("en");
			packageManagerMock.verify(PackageManager::reload);
		}
	}
}
