package com.airdropmc.controllers;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.config.DropOptions;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.exceptions.CannotAffordException;
import com.airdropmc.exceptions.EconomyUnavailableException;
import com.airdropmc.helpers.CrateManager;
import com.airdropmc.packages.Package;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DropControllerEconomyFlowTest {

	private ServerMock server;
	private WorldMock world;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("test_world");
		clearCrateManager();
		setAirdropEconomy(null);
	}

	@AfterEach
	void tearDown() throws Exception {
		clearCrateManager();
		setAirdropEconomy(null);
		MockBukkit.unmock();
	}

	@Test
	void playerInitiatedDropPackage_doesNotSpawnCrate_whenChargeFails() throws Exception {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		player.teleport(new Location(world, 0, 120, 0));
		setAirdropEconomy(mockEconomyProvider());

		Package pkg = mock(Package.class);
		when(pkg.getName()).thenReturn("starter");
		when(pkg.canAfford(player)).thenReturn(true);
		when(pkg.getPrice()).thenReturn(10.0);
		doThrow(new CannotAffordException(player.getName(), 10.0)).when(pkg).chargeUser(player);
		DropOptions options = DropOptions.createDefault().withDropHeight(20);

		assertThrows(CannotAffordException.class, () -> DropController.playerInitiatedDropPackage(pkg, player, options));
		verify(pkg, never()).getItems();
		assertEquals(0, getMapSize("crateMap"));
		assertEquals(0, getMapSize("landedCrateMap"));
	}

	@Test
	void playerInitiatedDropPackage_attemptsRefund_whenDropFailsAfterCharge() throws Exception {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		player.teleport(new Location(world, 0, 120, 0));
		setAirdropEconomy(mockEconomyProvider());

		Package pkg = mock(Package.class);

		when(pkg.getName()).thenReturn("starter");
		when(pkg.canAfford(player)).thenReturn(true);
		when(pkg.getPrice()).thenReturn(25.0);
		when(pkg.getItems()).thenThrow(new IllegalStateException("spawn failed"));

		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> DropController.playerInitiatedDropPackage(
				pkg, player, DropOptions.createDefault().withDropHeight(20)));
		verify(pkg).chargeUser(player);
		assertEquals(1, ex.getSuppressed().length);
		assertEquals(0, getMapSize("crateMap"));
		assertEquals(0, getMapSize("landedCrateMap"));
	}

	@Test
	void playerInitiatedDropPackage_throwsEconomyUnavailable_whenEconomyProviderMissing() throws Exception {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		player.teleport(new Location(world, 0, 120, 0));

		Package pkg = mock(Package.class);
		when(pkg.getName()).thenReturn("starter");

		assertThrows(EconomyUnavailableException.class, () -> DropController.playerInitiatedDropPackage(
				pkg, player, DropOptions.createDefault().withDropHeight(20)));
		verify(pkg, never()).canAfford(player);
		verify(pkg, never()).chargeUser(player);
	}

	private void clearCrateManager() throws Exception {
		Field crateMapField = CrateManager.class.getDeclaredField("crateMap");
		crateMapField.setAccessible(true);
		((Map<?, ?>) crateMapField.get(null)).clear();

		Field landedCrateMapField = CrateManager.class.getDeclaredField("landedCrateMap");
		landedCrateMapField.setAccessible(true);
		((Map<?, ?>) landedCrateMapField.get(null)).clear();
	}

	private int getMapSize(String fieldName) throws Exception {
		Field mapField = CrateManager.class.getDeclaredField(fieldName);
		mapField.setAccessible(true);
		return ((Map<?, ?>) mapField.get(null)).size();
	}

	private void setAirdropEconomy(Object economy) throws Exception {
		Field economyField = Airdrop.class.getDeclaredField("economyProvider");
		economyField.setAccessible(true);
		economyField.set(null, economy);
	}

	private EconomyProvider mockEconomyProvider() {
		return mock(EconomyProvider.class);
	}
}
