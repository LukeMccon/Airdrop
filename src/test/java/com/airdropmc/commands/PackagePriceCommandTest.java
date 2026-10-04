package com.airdropmc.commands;

import com.airdropmc.Airdrop;
import com.airdropmc.config.ConfigCoordinator.PackagePriceChange;
import com.airdropmc.exceptions.PackageNotFoundException;
import com.airdropmc.helpers.ChatHandler;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PackagePriceCommandTest {
	private ServerMock server;
	private Airdrop plugin;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		ChatHandler.init(null);
		setReady(true);
		plugin = mock(Airdrop.class);
		Airdrop.setPluginInstance(plugin);
	}

	@AfterEach
	void tearDown() throws Exception {
		setReady(false);
		Airdrop.setPluginInstance(null);
		setShuttingDown(false);
		ChatHandler.init(null);
		MockBukkit.unmock();
	}

	@ParameterizedTest
	@ValueSource(strings = {"abc", "-1", "NaN", "Infinity", "-Infinity", "1e999"})
	void invalidPricesHaveSpecificFeedback(String price) {
		PlayerMock admin = server.addPlayer();
		admin.setOp(true);
		assertTrue(new CmdAirdrop().onCommand(admin, mock(Command.class), "airdrop",
				new String[]{"package", "price", "starter", price}));
		var message = admin.nextComponentMessage();
		assertNotNull(message);
		String text = PlainTextComponentSerializer.plainText().serialize(message);
		assertTrue(text.contains("finite non-negative"), text);
	}

	@ParameterizedTest
	@ValueSource(ints = {2, 3, 5})
	void wrongArgumentCountsHavePriceUsageEvenBeforeReadiness(int count) throws Exception {
		setReady(false);
		PlayerMock admin = server.addPlayer();
		admin.setOp(true);
		String[] args = Arrays.copyOf(new String[]{"package", "price", "starter", "2", "extra"}, count);
		new CmdAirdrop().onCommand(admin, mock(Command.class), "airdrop", args);
		var message = admin.nextComponentMessage();
		assertNotNull(message);
		String text = PlainTextComponentSerializer.plainText().serialize(message);
		assertTrue(text.contains("/airdrop package price <package> <new-price>"), text);
	}

	@Test
	void successFeedbackWaitsForCommitAndUsesCommittedPricesAndDisplayName() {
		PlayerMock admin = server.addPlayer();
		admin.setOp(true);
		var pending = new CompletableFuture<PackagePriceChange>();
		when(plugin.updatePackagePriceAsync("sTaRtEr", 12.5)).thenReturn(pending);
		new CmdAirdrop().onCommand(admin, mock(Command.class), "airdrop",
				new String[]{"package", "price", "sTaRtEr", "12.5"});
		assertNull(admin.nextComponentMessage());
		pending.complete(new PackagePriceChange("Starter", 10, 12.5));
		String message = nextMessage(admin);
		assertTrue(message.contains("Starter"), message);
		assertTrue(message.contains("$10.0"), message);
		assertTrue(message.contains("$12.5"), message);
		assertNull(admin.nextComponentMessage());
	}

	@Test
	void consoleAndRconCanRepriceWithoutBeingPlayers() {
		var result = CompletableFuture.completedFuture(
				new PackagePriceChange("Starter", 10, 0));
		when(plugin.updatePackagePriceAsync("Starter", 0)).thenReturn(result);
		var console = server.getConsoleSender();
		new CmdAirdrop().onCommand(console, mock(Command.class), "airdrop",
				new String[]{"package", "price", "Starter", "0"});
		assertTrue(console.nextMessage().contains("$0.0"));
		var rcon = mock(RemoteConsoleCommandSender.class);
		when(rcon.hasPermission("airdrop.admin")).thenReturn(true);
		new CmdAirdrop().onCommand(rcon, mock(Command.class), "airdrop",
				new String[]{"package", "price", "Starter", "0"});
		verify(plugin, times(2)).updatePackagePriceAsync("Starter", 0);
	}

	@Test
	void permissionDenialAndReadinessPreventMutation() throws Exception {
		PlayerMock denied = server.addPlayer();
		new CmdAirdrop().onCommand(denied, mock(Command.class), "airdrop",
				new String[]{"package", "price", "starter", "2"});
		assertTrue(nextMessage(denied).contains("airdrop.admin"));
		setReady(false);
		denied.setOp(true);
		new CmdAirdrop().onCommand(denied, mock(Command.class), "airdrop",
				new String[]{"package", "price", "starter", "2"});
		assertTrue(nextMessage(denied).contains("still starting"));
		verifyNoInteractions(plugin);
	}

	@Test
	void domainAndPersistenceFailuresAreSpecificAndShutdownSuppressesLateFeedback() throws Exception {
		PlayerMock admin = server.addPlayer();
		admin.setOp(true);
		when(plugin.updatePackagePriceAsync("missing", 2)).thenReturn(
				CompletableFuture.failedFuture(new CompletionException(
						new PackageNotFoundException("missing"))));
		new CmdAirdrop().onCommand(admin, mock(Command.class), "airdrop",
				new String[]{"package", "price", "missing", "2"});
		String missing = nextMessage(admin);
		assertTrue(missing.contains("missing") && missing.contains("not found"), missing);
		when(plugin.updatePackagePriceAsync("starter", 2)).thenReturn(
				CompletableFuture.failedFuture(new IOException("disk full")));
		new CmdAirdrop().onCommand(admin, mock(Command.class), "airdrop",
				new String[]{"package", "price", "starter", "2"});
		assertTrue(nextMessage(admin).contains("No changes were made"));
		var pending = new CompletableFuture<PackagePriceChange>();
		when(plugin.updatePackagePriceAsync("starter", 2)).thenReturn(pending);
		new CmdAirdrop().onCommand(admin, mock(Command.class), "airdrop",
				new String[]{"package", "price", "starter", "2"});
		setShuttingDown(true);
		pending.complete(new PackagePriceChange("starter", 1, 2));
		assertNull(admin.nextComponentMessage());
	}

	private static String nextMessage(PlayerMock player) {
		var message = player.nextComponentMessage();
		assertNotNull(message);
		return PlainTextComponentSerializer.plainText().serialize(message);
	}

	private static void setShuttingDown(boolean value) throws Exception {
		Field field = Airdrop.class.getDeclaredField("shuttingDown");
		field.setAccessible(true);
		field.set(null, value);
	}

	private static void setReady(boolean ready) throws Exception {
		Field field = Airdrop.class.getDeclaredField("ready");
		field.setAccessible(true);
		field.set(null, ready);
	}
}
