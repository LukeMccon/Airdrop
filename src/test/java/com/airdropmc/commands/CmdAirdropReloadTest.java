package com.airdropmc.commands;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.Config;
import com.airdropmc.lang.LanguageManager;
import com.airdropmc.packages.PackageManager;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLimitSettings;
import com.airdropmc.limits.DropLocationKey;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
	void tearDown() throws Exception {
		setStatic("dropAdmissionController", null);
		setStatic("configuration", null);
		Airdrop.setPluginInstance(null);
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

	@Test
	void onCommand_reload_preservesAdmissionInstanceAndCooldowns() throws Exception {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		Command command = mock(Command.class);
		Config config = mock(Config.class);
		FileConfiguration fileConfig = mock(FileConfiguration.class);
		LanguageManager languageManager = mock(LanguageManager.class);
		Airdrop plugin = mock(Airdrop.class);
		DropAdmissionController admission = new DropAdmissionController();
		DropAdmissionController.Lease lease = admission.acquirePlayer(
				player.getUniqueId(), false,
				new DropLocationKey(server.addSimpleWorld("reload_world").getUID(), 0, 65, 0),
				new DropLimitSettings(Duration.ofSeconds(30), 3, 10, Duration.ofSeconds(600)));
		lease.commitSpawn();
		lease.close();
		when(config.getConfig()).thenReturn(fileConfig);
		when(fileConfig.getString("language", "en")).thenReturn("en");
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.getLanguageManager()).thenReturn(languageManager);
		setStatic("dropAdmissionController", admission);
		setStatic("configuration", config);
		Airdrop.setPluginInstance(plugin);

		try (MockedStatic<PackageManager> packageManager = Mockito.mockStatic(PackageManager.class)) {
			packageManager.when(PackageManager::reload).thenReturn(true);
			new CmdAirdrop().onCommand(player, command, "airdrop", new String[]{"reload"});
		}

		assertSame(admission, Airdrop.getDropAdmissionController());
		assertEquals(1, admission.snapshot().cooldowns());
	}

	private void setStatic(String fieldName, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}
}
