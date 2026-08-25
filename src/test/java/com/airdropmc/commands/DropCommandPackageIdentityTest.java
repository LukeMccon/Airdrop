package com.airdropmc.commands;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.controllers.DropController;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.LanguageManager;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.packages.Package;
import com.airdropmc.packages.PackageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DropCommandPackageIdentityTest {
	private ServerMock server;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		YamlConfiguration config = new YamlConfiguration();
		config.set("packages.Starter.price", 10.0);
		config.set("packages.Starter.items", List.of());
		ChatHandler.init(new LanguageManager(mock(Airdrop.class)));
		PackageManager.clear();
		PackageManager.publishPackages(PackageManager.materializePackages(config));
	}

	@AfterEach
	void tearDown() {
		PackageManager.clear();
		ChatHandler.init(null);
		MockBukkit.unmock();
	}

	@Test
	void dropCommandResolvesAcceptedPackageWithoutCaseDifferences() throws Exception {
		PlayerMock player = server.addPlayer();
		Package expected = PackageManager.get("Starter");

		try (MockedStatic<DropController> controller = mockStatic(DropController.class)) {
			DropCommand.onCommand(player, new String[]{"STARTER"});

			controller.verify(() -> DropController.playerInitiatedDropPackage(expected, player));
		}
	}

	@Test
	void permissionDenialDisplaysCanonicalNode() {
		PlayerMock player = server.addPlayer();

		DropCommand.onCommand(player, new String[]{"Starter"});

		Component message = player.nextComponentMessage();
		assertNotNull(message);
		String text = PlainTextComponentSerializer.plainText().serialize(message);
		assertTrue(text.contains("airdrop.package.starter"), text);
		assertFalse(text.contains("airdrop.package.Starter"));
	}

	@Test
	void permissionDenialSupportsLegacyPackagePlaceholder() {
		LanguageManager language = mock(LanguageManager.class);
		when(language.get(MessageKey.PREFIX)).thenReturn("[Airdrop]");
		when(language.get(eq(MessageKey.ERROR_INSUFFICIENT_PERMISSIONS), anyMap()))
				.thenAnswer(invocation -> {
					Map<String, String> placeholders = invocation.getArgument(1);
					return "requires airdrop.package." + placeholders.get("package");
				});
		ChatHandler.init(language);
		PlayerMock player = server.addPlayer();

		DropCommand.onCommand(player, new String[]{"Starter"});

		Component message = player.nextComponentMessage();
		assertNotNull(message);
		String text = PlainTextComponentSerializer.plainText().serialize(message);
		assertTrue(text.contains("airdrop.package.starter"), text);
		assertFalse(text.contains("airdrop.package.null"), text);
	}
}
