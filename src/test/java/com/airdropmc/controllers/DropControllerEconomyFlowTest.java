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
import com.airdropmc.lang.MessageKey;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLocationKey;
import com.airdropmc.packages.Package;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

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
