package com.airdropmc.commands;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.packages.PackageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CmdAirdropArgumentCountTest {

	private ServerMock server;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		ChatHandler.init(null);
		PackageManager.clear();
		YamlConfiguration config = new YamlConfiguration();
		config.set("packages.starter.price", 0.0);
		config.set("packages.starter.items", List.of());
		PackageManager.publishPackages(PackageManager.materializePackages(config));
		setReady(true);
	}

	@AfterEach
	void tearDown() throws Exception {
		PackageManager.clear();
		ChatHandler.init(null);
		setReady(false);
		MockBukkit.unmock();
	}

	@Test
	void genericCommandFormsRejectExtraArguments() {
		PlayerMock player = server.addPlayer();
		Command command = mock(Command.class);
		CmdAirdrop executor = new CmdAirdrop();

		assertAll(
				() -> assertFalse(executor.onCommand(
						player, command, "airdrop", new String[]{"version", "extra"}), "version"),
				() -> assertFalse(executor.onCommand(
						player, command, "airdrop", new String[]{"packages", "extra"}), "packages"),
				() -> assertFalse(executor.onCommand(
						player, command, "airdrop", new String[]{"reload", "extra"}), "reload"),
				() -> assertFalse(executor.onCommand(
						player, command, "airdrop", new String[]{"starter", "extra"}), "direct drop"),
				() -> assertFalse(executor.onCommand(
						player, command, "airdrop", new String[]{"package", "starter", "extra"}),
						"package info"));
	}

	@Test
	void malformedNestedFormsUseTargetedFeedbackBeforeReadinessCheck() throws Exception {
		PlayerMock player = server.addPlayer();
		Command command = mock(Command.class);
		CmdAirdrop executor = new CmdAirdrop();
		setReady(false);

		assertTrue(executor.onCommand(player, command, "airdrop", new String[]{"package", "create"}));

		String message = nextMessage(player);
		assertTrue(message.contains("requires a name and price"), message);
		assertFalse(message.contains("still starting"), message);
	}

	private String nextMessage(PlayerMock player) {
		Component message = player.nextComponentMessage();
		return PlainTextComponentSerializer.plainText().serialize(message);
	}

	private void setReady(boolean ready) throws Exception {
		Field field = Airdrop.class.getDeclaredField("ready");
		field.setAccessible(true);
		field.set(null, ready);
	}
}
