package com.airdropmc.commands;

import com.airdropmc.Airdrop;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.packages.PackagesGui;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PackagesCommandTest {
	private ServerMock server;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		ChatHandler.init(null);
	}

	@AfterEach
	void tearDown() {
		ChatHandler.init(null);
		MockBukkit.unmock();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void ordinaryPlayersAndAdminsOpenTheSameCatalog(boolean admin) {
		PlayerMock player = server.addPlayer();
		player.setOp(admin);
		PackagesGui catalog = mock(PackagesGui.class);
		try (MockedStatic<Airdrop> airdrop = mockStatic(Airdrop.class)) {
			airdrop.when(Airdrop::isReady).thenReturn(true);
			airdrop.when(Airdrop::getPackagesGui).thenReturn(catalog);

			assertTrue(new CmdAirdrop().onCommand(
					player, mock(Command.class), "airdrop", new String[]{"packages"}));

			verify(catalog).openInventory(player);
			assertNull(player.nextComponentMessage());
		}
	}

	@Test
	void consoleReceivesPlayerOnlyFeedbackWithoutOpeningCatalog() {
		PackagesGui catalog = mock(PackagesGui.class);
		try (MockedStatic<Airdrop> airdrop = mockStatic(Airdrop.class)) {
			airdrop.when(Airdrop::isReady).thenReturn(true);
			airdrop.when(Airdrop::getPackagesGui).thenReturn(catalog);

			PackagesCommand.onCommand(server.getConsoleSender());

			assertTrue(server.getConsoleSender().nextMessage().contains("Must be a player"));
			assertTrue(server.getConsoleSender().nextMessage().contains("inventory GUI"));
			verifyNoInteractions(catalog);
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void unavailableCatalogReportsReadinessToOrdinaryPlayers(boolean ready) {
		PlayerMock player = server.addPlayer();
		try (MockedStatic<Airdrop> airdrop = mockStatic(Airdrop.class)) {
			airdrop.when(Airdrop::isReady).thenReturn(ready);

			PackagesCommand.onCommand(player);

			var message = player.nextComponentMessage();
			assertNotNull(message);
			assertTrue(PlainTextComponentSerializer.plainText().serialize(message).contains("still starting"));
		}
	}
}
