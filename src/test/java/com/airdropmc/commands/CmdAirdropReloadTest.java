package com.airdropmc.commands;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.economy.EconomyProviderRefreshResult;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLimitSettings;
import com.airdropmc.limits.DropLocationKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CmdAirdropReloadTest {

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
		setStatic("dropAdmissionController", null);
		setStatic("configuration", null);
		setStatic("ready", false);
		setStatic("shuttingDown", false);
		Airdrop.setPluginInstance(null);
		ChatHandler.init(null);
		MockBukkit.unmock();
	}

	@Test
	void onCommand_reload_reportsCompletionOnlyAfterAsyncReloadFinishes() throws Exception {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		Command command = mock(Command.class);
		Airdrop plugin = mock(Airdrop.class);
		CompletableFuture<EconomyProviderRefreshResult> reload = new CompletableFuture<>();

		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.reloadConfiguration()).thenReturn(reload);
		Airdrop.setPluginInstance(plugin);
		setStatic("ready", true);

		boolean handled = new CmdAirdrop().onCommand(player, command, "airdrop", new String[]{"reload"});

		assertTrue(handled);
		verify(plugin).reloadConfiguration();
		assertTrue(nextMessage(player).contains("Reloading configuration"));
		assertNull(player.nextComponentMessage(), "reload completion must wait for the asynchronous result");

		reload.complete(EconomyProviderRefreshResult.active("TestEconomy"));

		String completion = nextMessage(player);
		assertTrue(completion.contains("Configuration, language, packages, and economy reloaded"), completion);
	}

	@Test
	void onCommand_reload_preservesAdmissionInstanceAndCooldowns() throws Exception {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		Command command = mock(Command.class);
		Airdrop plugin = mock(Airdrop.class);
		CompletableFuture<EconomyProviderRefreshResult> reload = new CompletableFuture<>();
		DropAdmissionController admission = new DropAdmissionController();
		DropAdmissionController.Lease lease = admission.acquirePlayer(
				player.getUniqueId(), false,
				new DropLocationKey(server.addSimpleWorld("reload_world").getUID(), 0, 65, 0),
				new DropLimitSettings(Duration.ofSeconds(30), 3, 10, Duration.ofSeconds(600)));
		lease.commitSpawn();
		lease.close();

		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.reloadConfiguration()).thenReturn(reload);
		setStatic("dropAdmissionController", admission);
		setStatic("ready", true);
		Airdrop.setPluginInstance(plugin);

		new CmdAirdrop().onCommand(player, command, "airdrop", new String[]{"reload"});

		assertSame(admission, Airdrop.getDropAdmissionController());
		assertEquals(1, admission.snapshot().cooldowns());

		reload.complete(EconomyProviderRefreshResult.active("TestEconomy"));

		assertSame(admission, Airdrop.getDropAdmissionController());
		assertEquals(1, admission.snapshot().cooldowns());
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
