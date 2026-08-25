package com.airdropmc.commands;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.Crate;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.economy.EconomyProviderRefreshResult;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLimitSettings;
import com.airdropmc.limits.DropLocationKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.entity.FallingBlock;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CmdAirdropLifecycleSafetyTest {

	private ServerMock server;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		ChatHandler.init(null);
		setStatic("ready", false);
		setStatic("shuttingDown", false);
	}

	@AfterEach
	void tearDown() throws Exception {
		CrateManager.clearAll();
		setStatic("dropAdmissionController", null);
		setStatic("economyProvider", null);
		setStatic("pluginInstance", null);
		setStatic("ready", false);
		setStatic("shuttingDown", false);
		ChatHandler.init(null);
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

		try (MockedStatic<Airdrop> airdropMock = Mockito.mockStatic(Airdrop.class)) {
			airdropMock.when(Airdrop::isReady).thenReturn(true);
			airdropMock.when(Airdrop::getPluginInstance).thenReturn(null);
			airdropMock.when(Airdrop::isShuttingDown).thenReturn(false);

			boolean handled = new CmdAirdrop().onCommand(player, command, "airdrop", new String[]{"reload"});

			assertTrue(handled);
			assertTrue(nextMessage(player).contains("Reload unavailable"));
		}
	}

	@Test
	void onCommand_reload_reportsAsyncFailureAndRetainsLiveState() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		Command command = mock(Command.class);
		Airdrop plugin = mock(Airdrop.class);
		CompletableFuture<EconomyProviderRefreshResult> reload = new CompletableFuture<>();

		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.reloadConfiguration()).thenReturn(reload);
		when(plugin.getLogger()).thenReturn(Logger.getLogger(getClass().getName()));

		try (MockedStatic<Airdrop> airdropMock = Mockito.mockStatic(Airdrop.class)) {
			airdropMock.when(Airdrop::isReady).thenReturn(true);
			airdropMock.when(Airdrop::getPluginInstance).thenReturn(plugin);
			airdropMock.when(Airdrop::isShuttingDown).thenReturn(false);

			boolean handled = new CmdAirdrop().onCommand(player, command, "airdrop", new String[]{"reload"});

			assertTrue(handled);
			verify(plugin).reloadConfiguration();
			assertTrue(nextMessage(player).contains("Reloading configuration"));
			assertNull(player.nextComponentMessage(), "failure feedback must wait for asynchronous completion");

			reload.completeExceptionally(new IllegalStateException("packages unavailable"));

			String failure = nextMessage(player);
			assertTrue(failure.contains("previous configuration remains active"), failure);
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
		setStatic("economyProvider", mock(EconomyProvider.class));
		setStatic("pluginInstance", plugin);

		try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class, Mockito.CALLS_REAL_METHODS)) {
			bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
			bukkit.when(Bukkit::isStopping).thenReturn(false);
			plugin.onDisable();
		}

		assertEquals(0, admission.snapshot().falling());
		assertEquals(0, admission.snapshot().landedClaims());
		assertEquals(0, admission.snapshot().cooldowns());
		assertNull(Airdrop.getDropAdmissionController());
		assertNull(Airdrop.getEconomyProvider());
		assertTrue(Airdrop.isShuttingDown());
		verify(crate).destroy();
		verify(scheduler).cancelTasks(plugin);
	}

	private String nextMessage(PlayerMock player) {
		Component message = player.nextComponentMessage();
		assertNotNull(message);
		return PlainTextComponentSerializer.plainText().serialize(message);
	}

	private void setStatic(String fieldName, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}
}
