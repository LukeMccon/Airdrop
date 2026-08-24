package com.airdropmc.commands;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.Config;
import com.airdropmc.Crate;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.lang.LanguageManager;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLimitSettings;
import com.airdropmc.limits.DropLocationKey;
import com.airdropmc.packages.PackageManager;
import org.bukkit.command.Command;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.FallingBlock;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doAnswer;
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
		CrateManager.clearAll();
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

	@Test
	void onDisable_stopsAdmissionsBeforeCratesAndCancelsRemainingTasksAfterCleanup() throws Exception {
		Airdrop plugin = mock(Airdrop.class, Mockito.CALLS_REAL_METHODS);
		DropAdmissionController admission = new DropAdmissionController();
		World world = mock(World.class);
		when(world.getUID()).thenReturn(java.util.UUID.randomUUID());
		DropAdmissionController.Lease lease = admission.acquireSystem(
				new DropLocationKey(world.getUID(), 0, 65, 0),
				new DropLimitSettings(Duration.ofSeconds(30), 3, 10, Duration.ofSeconds(600)));
		lease.commitSpawn();
		FallingBlock fallingBlock = mock(FallingBlock.class);
		Crate crate = mock(Crate.class);
		AtomicBoolean crateDestroyed = new AtomicBoolean();
		doAnswer(invocation -> {
			assertFalse(admission.snapshot().accepting(), "admission must stop before crate cleanup");
			assertTrue(Airdrop.isShuttingDown(), "shutdown flag must be visible before crate cleanup");
			crateDestroyed.set(true);
			return null;
		}).when(crate).destroy();
		CrateManager.addCrate(fallingBlock, crate);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		doAnswer(invocation -> {
			assertTrue(crateDestroyed.get(), "remaining tasks must be cancelled after crate cleanup");
			return null;
		}).when(scheduler).cancelTasks(plugin);
		setStatic("dropAdmissionController", admission);
		setStatic("pluginInstance", plugin);

		try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class, Mockito.CALLS_REAL_METHODS)) {
			bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
			plugin.onDisable();
		}

		assertEquals(0, admission.snapshot().falling());
		assertEquals(0, admission.snapshot().landedClaims());
		assertEquals(0, admission.snapshot().cooldowns());
		assertNull(Airdrop.getDropAdmissionController());
		assertTrue(Airdrop.isShuttingDown());
		verify(crate).destroy();
		verify(scheduler).cancelTasks(plugin);
	}

	private void setStatic(String fieldName, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}
}
