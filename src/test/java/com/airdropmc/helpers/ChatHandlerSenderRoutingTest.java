package com.airdropmc.helpers;

import org.mockbukkit.mockbukkit.MockBukkit;
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
				"plugin_version", "4.0.0",
				"extension_api_version", "unavailable",
				"paper_version", "1.21.11",
				"java_version", "21",
				"docs_url", "https://modrinth.com/plugin/airdrop"));

		verify(sender).sendMessage(anyString());
	}
	@Test
	void recipientNotificationPreservesSenderHeadAndLocalizedText() {
		org.bukkit.entity.Player recipient = mock(org.bukkit.entity.Player.class);
		java.util.UUID senderId = java.util.UUID.randomUUID();
		var label = net.kyori.adventure.text.Component.object(
				net.kyori.adventure.text.object.ObjectContents.playerHead(senderId))
				.append(net.kyori.adventure.text.Component.text(" Luke"));
		ChatHandler.sendWithSender(recipient, MessageKey.TARGETED_RECIPIENT_INCOMING,
				Map.of("sender", "Luke", "name", "starter", "request_id", "request-123"), label);
		var capture = org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
		verify(recipient).sendMessage(capture.capture());
		var message = capture.getValue();
		org.junit.jupiter.api.Assertions.assertTrue(message.contains(label));
		String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(message);
		org.junit.jupiter.api.Assertions.assertTrue(plain.contains("Luke is sending you package starter"), plain);
		org.junit.jupiter.api.Assertions.assertTrue(plain.contains("request-123"), plain);
		org.junit.jupiter.api.Assertions.assertTrue(plain.contains("You will not be charged"), plain);
		org.junit.jupiter.api.Assertions.assertFalse(plain.contains("{sender}"), plain);
	}

}
