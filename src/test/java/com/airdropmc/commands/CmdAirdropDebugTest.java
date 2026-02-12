package com.airdropmc.commands;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.Config;
import com.airdropmc.config.ConfigKeys;
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

class CmdAirdropDebugTest {

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
	void onCommand_debug_requiresAdmin() {
		PlayerMock player = server.addPlayer();
		Command command = mock(Command.class);

		boolean handled = new CmdAirdrop().onCommand(player, command, "airdrop", new String[]{"debug", "on"});

		assertTrue(handled);
	}

	@Test
	void onCommand_debug_on_updatesConfigAndSaves() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		Command command = mock(Command.class);
		Config config = mock(Config.class);
		FileConfiguration fileConfig = mock(FileConfiguration.class);

		when(config.getConfig()).thenReturn(fileConfig);
		when(fileConfig.getBoolean(ConfigKeys.LOGGING_DEBUG, false)).thenReturn(false);

		try (MockedStatic<Airdrop> airdropMock = Mockito.mockStatic(Airdrop.class)) {
			airdropMock.when(Airdrop::getConfiguration).thenReturn(config);

			boolean handled = new CmdAirdrop().onCommand(player, command, "airdrop", new String[]{"debug", "on"});

			assertTrue(handled);
			verify(fileConfig).set(ConfigKeys.LOGGING_DEBUG, true);
			verify(config).saveConfig();
		}
	}

	@Test
	void onCommand_debug_toggle_usesCurrentValue() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		Command command = mock(Command.class);
		Config config = mock(Config.class);
		FileConfiguration fileConfig = mock(FileConfiguration.class);

		when(config.getConfig()).thenReturn(fileConfig);
		when(fileConfig.getBoolean(ConfigKeys.LOGGING_DEBUG, false)).thenReturn(true);

		try (MockedStatic<Airdrop> airdropMock = Mockito.mockStatic(Airdrop.class)) {
			airdropMock.when(Airdrop::getConfiguration).thenReturn(config);

			boolean handled = new CmdAirdrop().onCommand(player, command, "airdrop", new String[]{"debug", "toggle"});

			assertTrue(handled);
			verify(fileConfig).set(ConfigKeys.LOGGING_DEBUG, false);
			verify(config).saveConfig();
		}
	}

	@Test
	void onCommand_debug_invalidValue_doesNotSaveConfig() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		Command command = mock(Command.class);
		Config config = mock(Config.class);
		FileConfiguration fileConfig = mock(FileConfiguration.class);

		when(config.getConfig()).thenReturn(fileConfig);
		when(fileConfig.getBoolean(ConfigKeys.LOGGING_DEBUG, false)).thenReturn(false);

		try (MockedStatic<Airdrop> airdropMock = Mockito.mockStatic(Airdrop.class)) {
			airdropMock.when(Airdrop::getConfiguration).thenReturn(config);

			boolean handled = new CmdAirdrop().onCommand(player, command, "airdrop", new String[]{"debug", "wat"});

			assertTrue(handled);
			Mockito.verify(fileConfig, Mockito.never()).set(ConfigKeys.LOGGING_DEBUG, true);
			Mockito.verify(fileConfig, Mockito.never()).set(ConfigKeys.LOGGING_DEBUG, false);
			Mockito.verify(config, Mockito.never()).saveConfig();
		}
	}
}
