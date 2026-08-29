package com.airdropmc.helpers;

import be.seeseemelk.mockbukkit.MockBukkit;
import com.airdropmc.lang.MessageKey;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChatHandlerSenderRoutingTest {

	@BeforeEach
	void setUp() {
		MockBukkit.mock();
		ChatHandler.init(null);
	}

	@AfterEach
	void tearDown() {
		ChatHandler.init(null);
		MockBukkit.unmock();
	}

	@Test
	void sendMessageRepliesToRemoteConsoleSender() {
		RemoteConsoleCommandSender sender = mock(RemoteConsoleCommandSender.class);

		ChatHandler.sendMessage(sender, "reload complete");

		verify(sender).sendMessage(contains("reload complete"));
	}

	@Test
	void sendErrorMessageRepliesToCommandBlockSender() {
		BlockCommandSender sender = mock(BlockCommandSender.class);

		ChatHandler.sendErrorMessage(sender, "invalid command");

		verify(sender).sendMessage(contains("invalid command"));
	}

	@Test
	void sendWithoutPrefixRepliesToSuppliedConsoleSender() {
		ConsoleCommandSender sender = mock(ConsoleCommandSender.class);

		ChatHandler.sendWithoutPrefix(sender, MessageKey.SYSTEM_VERSION_INFO, Map.of(
				"version", "4.0.0",
				"api_version", "1.21.11"));

		verify(sender).sendMessage(anyString());
	}
}
