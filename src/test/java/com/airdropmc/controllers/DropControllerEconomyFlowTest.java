package com.airdropmc.controllers;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.Config;
import com.airdropmc.Crate;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.config.DropOptions;
import com.airdropmc.economy.EconomyPlayer;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.economy.EconomyResult;
import com.airdropmc.exceptions.DropLimitException;
import com.airdropmc.exceptions.DropLimitException.Reason;
import com.airdropmc.exceptions.EconomyUnavailableException;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.helpers.PermissionsHelper;
import com.airdropmc.limits.DropAdmissionController;
import com.airdropmc.limits.DropLocationKey;
import com.airdropmc.packages.Package;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.FallingBlock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DropControllerEconomyFlowTest {

	private ServerMock server;
	private WorldMock world;
	private DropAdmissionController admission;
	private EconomyProvider economy;
	private Airdrop plugin;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("test_world");
		CrateManager.clearAll();
		setStatic("shuttingDown", false);
		admission = new DropAdmissionController();
		setStatic("dropAdmissionController", admission);
		installConfig();

		economy = mock(EconomyProvider.class);
		when(economy.nativeAsync()).thenReturn(false);
		when(economy.canAfford(any(EconomyPlayer.class), any(BigDecimal.class)))
				.thenReturn(CompletableFuture.completedFuture(EconomyResult.ok()));
		when(economy.withdraw(any(EconomyPlayer.class), any(BigDecimal.class)))
				.thenReturn(CompletableFuture.completedFuture(EconomyResult.ok()));
		when(economy.deposit(any(EconomyPlayer.class), any(BigDecimal.class)))
				.thenReturn(CompletableFuture.completedFuture(EconomyResult.ok()));
		setStatic("economyProvider", economy);

		plugin = mock(Airdrop.class);
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.getLogger()).thenReturn(Logger.getLogger("DropControllerEconomyFlowTest"));
		Airdrop.setPluginInstance(plugin);
	}

	@AfterEach
	void tearDown() throws Exception {
		CrateManager.clearAll();
		admission.clear();
		setStatic("dropAdmissionController", null);
		setStatic("economyProvider", null);
		setStatic("configuration", null);
		setStatic("shuttingDown", false);
		Airdrop.setPluginInstance(null);
		MockBukkit.unmock();
	}

	@Test
	void capacityRejectionOccursBeforePaymentOrPayload() throws Exception {
		admission.acquireSystem(new DropLocationKey(world.getUID(), 50, 65, 50),
				ConfigKeys.getDropLimitSettings());
		admission.acquireSystem(new DropLocationKey(world.getUID(), 51, 65, 51),
				ConfigKeys.getDropLimitSettings());
		PlayerMock player = operatorAtClearSky();
		Package pkg = paidPackage();

		DropLimitException rejection = assertThrows(DropLimitException.class,
				() -> DropController.playerInitiatedDropPackage(pkg, player, options()));

		assertEquals(Reason.FALLING_CAPACITY, rejection.getReason());
		verify(pkg, never()).getItems();
		verify(economy, never()).canAfford(any(), any());
	}

	@Test
	void payloadFailureReleasesReservationBeforePayment() throws Exception {
		PlayerMock player = operatorAtClearSky();
		Package pkg = paidPackage();
		when(pkg.getItems()).thenThrow(new IllegalStateException("payload failed"));

		assertThrows(IllegalStateException.class,
				() -> DropController.playerInitiatedDropPackage(pkg, player, options()));

		assertEquals(emptyAdmission(), admission.snapshot());
		verify(economy, never()).canAfford(any(), any());
	}

	@Test
	void confirmedPaymentCreatesOneFallingCrate() throws Exception {
		PlayerMock player = operatorAtClearSky();

		DropController.playerInitiatedDropPackage(paidPackage(), player, options());
		server.getScheduler().performTicks(2L);

		assertEquals(1, CrateManager.getCrateMap().size());
		Crate crate = CrateManager.getCrateMap().values().iterator().next();
		assertTrue(crate.isPaid());
		assertEquals(1, admission.snapshot().falling());
		verify(economy).withdraw(any(EconomyPlayer.class), any(BigDecimal.class));
	}

	@Test
	void fallingCrateFailureRequestsOneRefund() throws Exception {
		PlayerMock player = operatorAtClearSky();
		DropController.playerInitiatedDropPackage(paidPackage(), player, options());
		server.getScheduler().performTicks(2L);
		FallingBlock fallingBlock = CrateManager.getCrateMap().keySet().iterator().next();

		CrateManager.removeCrateAndDestroy(fallingBlock);

		verify(economy).deposit(any(EconomyPlayer.class), any(BigDecimal.class));
		assertEquals(emptyAdmission(), admission.snapshot());
	}

	@Test
	void pendingPaymentKeepsRequestLeaseAndRejectsSecondRequest() throws Exception {
		CompletableFuture<EconomyResult> pending = new CompletableFuture<>();
		when(economy.canAfford(any(EconomyPlayer.class), any(BigDecimal.class))).thenReturn(pending);
		PlayerMock player = operatorAtClearSky();
		Package pkg = paidPackage();

		DropLimitException rejection;
		try (MockedStatic<PermissionsHelper> permissions = mockStatic(PermissionsHelper.class)) {
			permissions.when(() -> PermissionsHelper.hasPermission(player, "starter")).thenReturn(true);
			permissions.when(() -> PermissionsHelper.hasCooldownBypass(player)).thenReturn(false);
			DropController.playerInitiatedDropPackage(pkg, player, options());
			rejection = assertThrows(DropLimitException.class,
					() -> DropController.playerInitiatedDropPackage(pkg, player, options()));
		}

		assertEquals(Reason.REQUEST_PENDING, rejection.getReason());
		assertEquals(1, admission.snapshot().pending());
		verify(economy, never()).withdraw(any(), any());
	}

	@Test
	void zeroPricePackageDoesNotRequireProvider() throws Exception {
		setStatic("economyProvider", null);
		PlayerMock player = operatorAtClearSky();
		Package pkg = paidPackage();
		when(pkg.getPrice()).thenReturn(0.0);

		DropController.playerInitiatedDropPackage(pkg, player, options());

		assertEquals(1, CrateManager.getCrateMap().size());
		verify(economy, never()).canAfford(any(), any());
	}

	@Test
	void missingProviderFailsBeforeAdmissionAndPayload() throws Exception {
		setStatic("economyProvider", null);
		PlayerMock player = operatorAtClearSky();
		Package pkg = paidPackage();

		assertThrows(EconomyUnavailableException.class,
				() -> DropController.playerInitiatedDropPackage(pkg, player, options()));

		assertEquals(emptyAdmission(), admission.snapshot());
		verify(pkg, never()).getItems();
	}

	private PlayerMock operatorAtClearSky() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		player.teleport(new Location(world, 0, 120, 0));
		return player;
	}

	private Package paidPackage() {
		Package pkg = mock(Package.class);
		when(pkg.getName()).thenReturn("starter");
		when(pkg.getPrice()).thenReturn(10.0);
		when(pkg.getItems()).thenReturn(List.of());
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

	private void installConfig() throws Exception {
		YamlConfiguration values = new YamlConfiguration();
		values.set(ConfigKeys.ECONOMY_ENABLED, true);
		values.set(ConfigKeys.DROP_REQUEST_COOLDOWN_SECONDS, 30);
		values.set(ConfigKeys.DROP_MAX_FALLING, 2);
		values.set(ConfigKeys.DROP_MAX_LANDED, 10);
		values.set(ConfigKeys.DROP_LANDED_LIFETIME_SECONDS, 600);
		Config config = mock(Config.class);
		when(config.getConfig()).thenReturn(values);
		setStatic("configuration", config);
	}

	private DropAdmissionController.Snapshot emptyAdmission() {
		return new DropAdmissionController.Snapshot(0, 0, 0, 0, 0, true);
	}

	private void setStatic(String fieldName, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}
}
