package com.airdropmc.commands;

import com.airdropmc.Airdrop;
import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.DropRejection;
import com.airdropmc.api.DropRejectionReason;
import com.airdropmc.api.DropRequestDescriptor;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.api.DropSource;
import com.airdropmc.api.DropSpawnResult;
import com.airdropmc.api.PaymentStatus;
import com.airdropmc.controllers.DropController;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.internal.drop.DropRequestCoordinator;
import com.airdropmc.lang.LanguageManager;
import com.airdropmc.packages.Package;
import com.airdropmc.packages.PackageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class DropCommandSurfaceFeedbackTest {

	@TempDir
	Path tempDir;
	private WorldMock world;
	private PlayerMock player;
	private DropRequestCoordinator requests;

	@BeforeEach
	void setUp() throws Exception {
		var server = MockBukkit.mock();
		world = server.addSimpleWorld("surface_world");
		player = server.addPlayer();
		player.teleport(new Location(world, 2, 64, 3));
		YamlConfiguration config = new YamlConfiguration();
		config.set("packages.starter.price", 0);
		config.set("packages.starter.items", List.of());
		PackageManager.clear();
		PackageManager.publishPackages(PackageManager.materializePackages(config));
		ChatHandler.init(new LanguageManager(mock(Airdrop.class)));
		requests = new DropRequestCoordinator(MockBukkit.createMockPlugin());
		requests.startAccepting();
	}

	@AfterEach
	void tearDown() {
		requests.stop();
		PackageManager.clear();
		ChatHandler.init(null);
		MockBukkit.unmock();
	}

	@ParameterizedTest
	@CsvSource({"STONE_BRICKS,stone bricks", "OAK_LEAVES,oak leaves"})
	void describesCapturedHighestSurfaceAfterWorldAndPlayerChange(Material material, String readable) {
		world.getBlockAt(2, 70, 3).setType(Material.GLASS);
		world.getBlockAt(2, 82, 3).setType(material);
		DropHandle handle = request();
		assertRejection(handle, DropRejectionReason.SKY_NOT_CLEAR);

		world.getBlockAt(2, 82, 3).setType(Material.AIR);
		world.getBlockAt(2, 95, 3).setType(Material.DIAMOND_BLOCK);
		player.teleport(new Location(world, 20, 100, 30));

		String message = feedback(handle);

		assertTrue(message.contains("highest surface (" + readable + ") at Y: 82"), message);
		assertTrue(message.contains("Move into open sky and try again."), message);
		assertFalse(message.contains("surface_world"), message);
		assertFalse(message.contains("GLASS") || message.contains("diamond") || message.contains("95"), message);
	}

	@Test
	void formatsNegativeSurfaceHeight() {
		WorldMock underground = new WorldMock(Material.AIR, -64, 320, -64);
		MockBukkit.getMock().addWorld(underground);
		underground.getBlockAt(2, -20, 3).setType(Material.DEEPSLATE);
		player.teleport(new Location(underground, 2, -30, 3));

		String message = feedback(request());

		assertTrue(message.contains("highest surface (deepslate) at Y: -20"), message);
	}

	@Test
	void invalidResolutionDoesNotBlameSurface() {
		world.getBlockAt(2, 82, 3).setType(Material.STONE);
		Package broken = mock(Package.class);
		when(broken.getName()).thenReturn("starter");
		when(broken.getItems()).thenThrow(new IllegalStateException("items unavailable"));
		PackageManager.publishPackages(Map.of("starter", broken));
		DropHandle handle = request();
		assertRejection(handle, DropRejectionReason.INVALID_TARGET);

		String message = feedback(handle);

		assertTrue(message.contains("Could not resolve the drop location. Please try again."), message);
		assertFalse(message.contains("sky") || message.contains("surface") || message.contains("Y:"), message);
	}

	@Test
	void skyRejectionWithoutInternalDetailsKeepsGenericFallback() {
		DropRequestDescriptor descriptor = new DropRequestDescriptor(
				UUID.randomUUID(), DropSource.PLAYER, player.getUniqueId(), "starter", player.getLocation());
		DropOutcome.Rejected outcome = new DropOutcome.Rejected(descriptor, Optional.empty(),
				DropRejection.of(DropRejectionReason.SKY_NOT_CLEAR, "blocked"), PaymentStatus.NOT_APPLICABLE);
		DropHandle handle = mock(DropHandle.class);
		when(handle.spawn()).thenReturn(CompletableFuture.completedFuture(new DropSpawnResult.NotSpawned(outcome)));
		when(handle.outcome()).thenReturn(CompletableFuture.completedFuture(outcome));

		String message = feedback(handle);

		assertTrue(message.contains("Sky must be clear above your location"), message);
		assertFalse(message.contains("{"), message);
	}

	@Test
	void usesConfiguredSurfaceMessageAndPlaceholders() throws Exception {
		Files.createDirectories(tempDir.resolve("lang"));
		Files.writeString(tempDir.resolve("lang/fr.yml"),
				"errors:\n  sky-blocked-surface: 'Surface haute : {material}, altitude {y}. Changez de position.'\n");
		Airdrop plugin = mock(Airdrop.class);
		when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
		LanguageManager language = new LanguageManager(plugin);
		language.publishLanguage(language.prepareLanguage("fr"));
		ChatHandler.init(language);
		world.getBlockAt(2, 82, 3).setType(Material.OAK_LEAVES);

		String message = feedback(request());

		assertTrue(message.contains("Surface haute : oak leaves, altitude 82. Changez de position."), message);
		assertFalse(message.contains("{"), message);
	}

	private DropHandle request() {
		return requests.requestPlayerDrop(player, "starter", DropRequestOptions.defaults());
	}

	private void assertRejection(DropHandle handle, DropRejectionReason reason) {
		DropOutcome.Rejected rejected = assertInstanceOf(
				DropOutcome.Rejected.class, handle.outcome().toCompletableFuture().join());
		assertEquals(reason, rejected.rejection().reason());
		assertTrue(handle.context().isEmpty());
		assertEquals(0, requests.pendingCount());
	}

	private String feedback(DropHandle handle) {
		try (MockedStatic<DropController> controller = mockStatic(DropController.class)) {
			controller.when(() -> DropController.requestPlayerDrop(
					player, "starter", DropRequestOptions.defaults())).thenReturn(handle);
			DropCommand.onCommand(player, new String[]{"starter"});
		}
		Component message = player.nextComponentMessage();
		assertNotNull(message);
		assertNull(player.nextComponentMessage(), "A rejection should send exactly one message");
		return PlainTextComponentSerializer.plainText().serialize(message);
	}
}
