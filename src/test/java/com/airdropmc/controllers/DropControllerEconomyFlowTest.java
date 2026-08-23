package com.airdropmc.controllers;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.Config;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.config.DropOptions;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.economy.EconomyResult;
import com.airdropmc.exceptions.CannotAffordException;
import com.airdropmc.exceptions.DropLimitException;
import com.airdropmc.exceptions.DropLimitException.Reason;
import com.airdropmc.exceptions.EconomyUnavailableException;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.lang.MessageKey;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLocationKey;
import com.airdropmc.packages.Package;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class DropControllerEconomyFlowTest {

	private ServerMock server;
	private WorldMock world;
	private DropAdmissionController admission;
	private EconomyProvider economy;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("test_world");
		CrateManager.clearAll();
		admission = new DropAdmissionController();
		setStatic("dropAdmissionController", admission);
		setStatic("configuration", null);
		economy = mock(EconomyProvider.class);
		when(economy.deposit(org.mockito.ArgumentMatchers.any(), anyDouble())).thenReturn(EconomyResult.ok());
		setStatic("economyProvider", economy);
		Airdrop.setPluginInstance(null);
	}

	@AfterEach
	void tearDown() throws Exception {
		CrateManager.clearAll();
		admission.clear();
		setStatic("dropAdmissionController", null);
		setStatic("economyProvider", null);
		setStatic("configuration", null);
		Airdrop.setPluginInstance(null);
		MockBukkit.unmock();
	}

	@Test
	void playerDrop_capacityRejectionOccursBeforeChargeOrItems() throws Exception {
		installLimitConfig(1, 10);
		admission.acquireSystem(new DropLocationKey(world.getUID(), 50, 65, 50),
				ConfigKeys.getDropLimitSettings());
		PlayerMock player = operatorAtClearSky();
		Package pkg = affordablePackage(player);

		DropLimitException rejection = assertThrows(DropLimitException.class,
				() -> DropController.playerInitiatedDropPackage(pkg, player, options()));

		assertEquals(Reason.FALLING_CAPACITY, rejection.getReason());
		verify(pkg, never()).chargeUser(player);
		verify(pkg, never()).getItems();
	}

	@Test
	void playerDrop_locationRejectionOccursBeforeChargeOrItems() throws Exception {
		PlayerMock player = operatorAtClearSky();
		Package pkg = affordablePackage(player);
		DropAdmissionController rejectingAdmission = mock(DropAdmissionController.class);
		when(rejectingAdmission.acquirePlayer(
				org.mockito.ArgumentMatchers.eq(player.getUniqueId()),
				org.mockito.ArgumentMatchers.anyBoolean(),
				org.mockito.ArgumentMatchers.any(DropLocationKey.class),
				org.mockito.ArgumentMatchers.any()))
				.thenThrow(new DropLimitException(Reason.LOCATION_RESERVED));
		setStatic("dropAdmissionController", rejectingAdmission);

		DropLimitException rejection = assertThrows(DropLimitException.class,
				() -> DropController.playerInitiatedDropPackage(pkg, player, options()));

		assertEquals(Reason.LOCATION_RESERVED, rejection.getReason());
		verify(pkg, never()).chargeUser(player);
		verify(pkg, never()).getItems();
	}

	@Test
	void programmaticDrop_capacityRejectionOccursBeforeItems() throws Exception {
		installLimitConfig(1, 10);
		admission.acquireSystem(new DropLocationKey(world.getUID(), 50, 65, 50),
				ConfigKeys.getDropLimitSettings());
		Package pkg = mock(Package.class);

		DropLimitException rejection = assertThrows(DropLimitException.class,
				() -> DropController.dropPackage(pkg, world, new Location(world, 0, 120, 0), options()));

		assertEquals(Reason.FALLING_CAPACITY, rejection.getReason());
		verify(pkg, never()).getItems();
	}

	@Test
	void playerDrop_chargeFailureReleasesReservationsAndStartsNoCooldown() throws Exception {
		PlayerMock player = operatorAtClearSky();
		Package pkg = affordablePackage(player);
		when(pkg.chargeUser(player)).thenThrow(new CannotAffordException(player.getName(), 10.0));

		assertThrows(CannotAffordException.class,
				() -> DropController.playerInitiatedDropPackage(pkg, player, options()));

		assertEquals(new DropAdmissionController.Snapshot(0, 0, 0, 0, 0, true), admission.snapshot());
	}

	@Test
	void playerDrop_payloadFailureReleasesBeforeChargeOrRefund() throws Exception {
		PlayerMock player = operatorAtClearSky();
		Package pkg = affordablePackage(player);
		when(pkg.getItems()).thenThrow(new IllegalStateException("payload failed"));

		assertThrows(IllegalStateException.class,
				() -> DropController.playerInitiatedDropPackage(pkg, player, options()));

		assertEquals(new DropAdmissionController.Snapshot(0, 0, 0, 0, 0, true), admission.snapshot());
		verify(pkg, never()).chargeUser(player);
		verify(economy, never()).deposit(eq(player), anyDouble());
	}

	@Test
	void playerDrop_spawnFailureAfterConfirmedChargeRefundsAndReleases() throws Exception {
		PlayerMock player = operatorAtClearSky();
		Package pkg = affordablePackage(player);

		assertThrows(IllegalStateException.class,
				() -> DropController.playerInitiatedDropPackage(pkg, player, options()));

		assertEquals(new DropAdmissionController.Snapshot(0, 0, 0, 0, 0, true), admission.snapshot());
		verify(economy).deposit(player, 10.0);
	}

	@Test
	void playerDrop_partialEntitySpawnFailureCleansEntityRefundsAndReleases() throws Exception {
		World spawnWorld = mock(World.class);
		when(spawnWorld.getUID()).thenReturn(UUID.randomUUID());
		Block ground = mock(Block.class);
		when(ground.getLocation()).thenReturn(new Location(spawnWorld, 0, 64, 0));
		when(spawnWorld.getHighestBlockAt(0, 0)).thenReturn(ground);
		FallingBlock fallingBlock = mock(FallingBlock.class);
		when(fallingBlock.isDead()).thenReturn(false);
		when(spawnWorld.spawn(
				org.mockito.ArgumentMatchers.any(Location.class),
				org.mockito.ArgumentMatchers.eq(FallingBlock.class),
				org.mockito.ArgumentMatchers.<Consumer<? super FallingBlock>>any()))
				.thenAnswer(invocation -> {
					Consumer<? super FallingBlock> initializer = invocation.getArgument(2);
					initializer.accept(fallingBlock);
					return fallingBlock;
				});
		when(spawnWorld.spawnEntity(
				org.mockito.ArgumentMatchers.any(Location.class),
				org.mockito.ArgumentMatchers.eq(EntityType.SLIME)))
				.thenThrow(new IllegalStateException("partial spawn failed"));

		Player player = mock(Player.class);
		when(player.getWorld()).thenReturn(spawnWorld);
		when(player.getLocation()).thenReturn(new Location(spawnWorld, 0, 120, 0));
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getName()).thenReturn("charged-player");
		when(player.hasPermission("airdrop.admin")).thenReturn(true);
		Package pkg = mock(Package.class);
		when(pkg.getName()).thenReturn("starter");
		when(pkg.getPrice()).thenReturn(10.0);
		when(pkg.canAfford(player)).thenReturn(true);
		when(pkg.getItems()).thenReturn(List.of());
		when(pkg.chargeUser(player)).thenReturn(true);
		Airdrop plugin = mock(Airdrop.class);
		when(plugin.isEnabled()).thenReturn(true);
		Airdrop.setPluginInstance(plugin);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> DropController.playerInitiatedDropPackage(pkg, player, options()));

		assertEquals("partial spawn failed", failure.getMessage());
		verify(fallingBlock).setGravity(true);
		verify(fallingBlock).remove();
		verify(economy).deposit(player, 10.0);
		assertEquals(new DropAdmissionController.Snapshot(0, 0, 0, 0, 0, true), admission.snapshot());
	}

	@Test
	void playerDrop_refundFailureIsSuppressedOnOriginalSpawnFailure() throws Exception {
		when(economy.deposit(org.mockito.ArgumentMatchers.any(), anyDouble()))
				.thenReturn(EconomyResult.fail("refund rejected"));
		PlayerMock player = operatorAtClearSky();
		Package pkg = affordablePackage(player);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> DropController.playerInitiatedDropPackage(pkg, player, options()));

		assertEquals("Cannot drop crate while plugin is unavailable", failure.getMessage());
		assertEquals(1, failure.getSuppressed().length);
		assertEquals("Drop failed after charging " + player.getName() + " and refund transaction failed",
				failure.getSuppressed()[0].getMessage());
		assertEquals(new DropAdmissionController.Snapshot(0, 0, 0, 0, 0, true), admission.snapshot());
	}

	@Test
	void playerDrop_withoutBypassPermissionEnforcesCooldown() throws Exception {
		Airdrop plugin = mock(Airdrop.class);
		when(plugin.isEnabled()).thenReturn(true);
		Airdrop.setPluginInstance(plugin);
		PlayerMock player = server.addPlayer();
		player.setOp(false);
		player.teleport(new Location(world, 0, 120, 0));
		Package pkg = affordablePackage(player);

		try (MockedStatic<PermissionsHelper> permissions = mockStatic(PermissionsHelper.class)) {
			permissions.when(() -> PermissionsHelper.hasPermission(player, "starter")).thenReturn(true);
			permissions.when(() -> PermissionsHelper.hasCooldownBypass(player)).thenReturn(false);
			DropController.playerInitiatedDropPackage(pkg, player, options());
			player.teleport(new Location(world, 16, 120, 0));

			DropLimitException rejection = assertThrows(DropLimitException.class,
					() -> DropController.playerInitiatedDropPackage(pkg, player, options()));
			assertEquals(Reason.COOLDOWN, rejection.getReason());
		}
	}

	@Test
	void playerDrop_chargeMessageFailureDoesNotRollbackSuccessfulDrop() throws Exception {
		Airdrop plugin = mock(Airdrop.class);
		when(plugin.isEnabled()).thenReturn(true);
		Airdrop.setPluginInstance(plugin);
		PlayerMock player = operatorAtClearSky();
		Package pkg = affordablePackage(player);
		try (MockedStatic<ChatHandler> chat = mockStatic(ChatHandler.class, CALLS_REAL_METHODS)) {
			chat.when(() -> ChatHandler.send(player, MessageKey.DROP_CHARGED,
					Map.of("amount", "10.0"))).thenThrow(new IllegalStateException("feedback failed"));

			assertDoesNotThrow(() -> DropController.playerInitiatedDropPackage(pkg, player, options()));
		}

		assertEquals(1, admission.snapshot().falling());
		verify(economy, never()).deposit(eq(player), anyDouble());
	}

	@Test
	void playerDrop_throwsEconomyUnavailableBeforeAdmission_whenProviderMissing() throws Exception {
		setStatic("economyProvider", null);
		PlayerMock player = operatorAtClearSky();
		Package pkg = mock(Package.class);
		when(pkg.getName()).thenReturn("starter");

		assertThrows(EconomyUnavailableException.class,
				() -> DropController.playerInitiatedDropPackage(pkg, player, options()));

		assertEquals(new DropAdmissionController.Snapshot(0, 0, 0, 0, 0, true), admission.snapshot());
		verify(pkg, never()).canAfford(player);
		verify(pkg, never()).chargeUser(player);
	}

	private PlayerMock operatorAtClearSky() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		player.teleport(new Location(world, 0, 120, 0));
		return player;
	}

	private Package affordablePackage(PlayerMock player) throws Exception {
		Package pkg = mock(Package.class);
		when(pkg.getName()).thenReturn("starter");
		when(pkg.getPrice()).thenReturn(10.0);
		when(pkg.canAfford(player)).thenReturn(true);
		when(pkg.getItems()).thenReturn(List.of());
		when(pkg.chargeUser(player)).thenReturn(true);
		return pkg;
	}

	private DropOptions options() {
		return DropOptions.createDefault()
				.withDropHeight(20)
				.withChickenCount(1)
				.withFlareEffects(false)
				.withLandingEffects(false)
				.withContinuousEffects(false)
				.withSmokeEnabled(false);
	}

	private void installLimitConfig(int maxFalling, int maxLanded) throws Exception {
		YamlConfiguration values = new YamlConfiguration();
		values.set(ConfigKeys.ECONOMY_ENABLED, true);
		values.set(ConfigKeys.DROP_REQUEST_COOLDOWN_SECONDS, 30);
		values.set(ConfigKeys.DROP_MAX_FALLING, maxFalling);
		values.set(ConfigKeys.DROP_MAX_LANDED, maxLanded);
		values.set(ConfigKeys.DROP_LANDED_LIFETIME_SECONDS, 600);
		Config config = mock(Config.class);
		when(config.getConfig()).thenReturn(values);
		setStatic("configuration", config);
	}

	private void setStatic(String fieldName, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}
}
