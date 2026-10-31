package com.airdropmc.packages;

import com.airdropmc.Airdrop;
import com.airdropmc.helpers.ChatHandler;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerCatalogTest {

	private ServerMock server;
	private PluginMock harness;
	private Airdrop plugin;
	private PackagesGui catalog;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		harness = MockBukkit.createMockPlugin("CatalogHarness");
		plugin = mock(Airdrop.class);
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.getPluginLoader()).thenReturn(harness.getPluginLoader());
		when(plugin.getName()).thenReturn("Airdrop");
		when(plugin.getServer()).thenReturn(server);
		Airdrop.setPluginInstance(plugin);
		setStatic("ready", true);
		ChatHandler.init(null);
		PackageManager.publishPackages(Map.of(
				"starter", new Package("starter", 0.0, List.of(new ItemStack(Material.STONE, 2))),
				"premium", new Package("premium", 12.5, List.of(new ItemStack(Material.DIAMOND)))));
		catalog = new PackagesGui();
		setStatic("packagesGui", catalog);
	}

	@AfterEach
	void tearDown() throws Exception {
		catalog.closeAndUnregister();
		PackageGui.closeOpenEditors();
		PackageManager.clear();
		setStatic("packagesGui", null);
		setStatic("ready", false);
		Airdrop.setPluginInstance(null);
		ChatHandler.init(null);
		MockBukkit.unmock();
	}

	@Test
	void viewersHaveIndependentPermissionFilteredCatalogs() {
		PlayerMock starter = playerWith("starter");
		PlayerMock premium = playerWith("premium");
		catalog.openInventory(starter);
		Inventory first = top(starter);
		catalog.openInventory(premium);

		assertNotSame(first, top(premium));
		assertEquals(List.of("starter"), names(first));
		assertEquals(List.of("premium"), names(top(premium)));
	}

	@Test
	void freeAndPaidPackagesHaveClearPrices() {
		PlayerMock player = playerWith("all");
		catalog.openInventory(player);

		assertEquals(List.of("premium", "starter"), names(top(player)));
		assertTrue(top(player).getItem(0).getItemMeta().getLore().toString().contains("12.5"));
		assertTrue(top(player).getItem(1).getItemMeta().getLore().toString().contains("Free"));
	}

	@Test
	void emptyCatalogExplainsPermissionAvailability() {
		PlayerMock player = server.addPlayer();
		catalog.openInventory(player);

		assertEquals(List.of(), names(top(player)));
		assertTrue(Arrays.stream(top(player).getContents()).filter(java.util.Objects::nonNull)
				.anyMatch(item -> item.getItemMeta().getDisplayName().contains("No packages")));
	}

	@Test
	void ordinaryPlayerOpensSamePackageViewOnNextTick() {
		PlayerMock player = playerWith("starter");
		catalog.openInventory(player);
		Inventory list = top(player);
		InventoryClickEvent click = click(player, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);

		server.getPluginManager().callEvent(click);

		assertTrue(click.isCancelled());
		assertSame(list, top(player));
		server.getScheduler().performOneTick();
		assertNotSame(list, top(player));
		assertEquals(new ItemStack(Material.STONE, 2), top(player).getItem(0));
	}

	@Test
	void idlePermissionChangesRefreshOnlyTheAffectedPlayersList() {
		PlayerMock first = server.addPlayer();
		PermissionAttachment permission = first.addAttachment(harness, "airdrop.package.starter", true);
		PlayerMock second = playerWith("premium");
		catalog.openInventory(first);
		Inventory firstView = top(first);
		catalog.openInventory(second);
		permission.setPermission("airdrop.package.starter", false);

		server.getScheduler().performOneTick();

		assertSame(firstView, top(first));
		assertEquals(List.of(), names(firstView));
		assertEquals(List.of("premium"), names(top(second)));
		permission.setPermission("airdrop.package.premium", true);
		server.getScheduler().performOneTick();
		assertEquals(List.of("premium"), names(firstView));
	}

	@Test
	void permissionLossBeforeNavigationCannotOpenThePackage() {
		PlayerMock player = server.addPlayer();
		PermissionAttachment permission = player.addAttachment(harness, "airdrop.package.starter", true);
		catalog.openInventory(player);
		Inventory list = top(player);
		server.getPluginManager().callEvent(click(player, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL));
		permission.setPermission("airdrop.package.starter", false);

		server.getScheduler().performOneTick();

		assertSame(list, top(player));
		assertEquals(List.of(), names(list));
	}

	@Test
	void sameNameReplacementBeforeNavigationInvalidatesTheClick() {
		PlayerMock player = playerWith("starter");
		catalog.openInventory(player);
		Inventory list = top(player);
		server.getPluginManager().callEvent(click(player, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL));
		PackageManager.publishPackages(Map.of("starter",
				new Package("starter", 7.0, List.of(new ItemStack(Material.GOLD_INGOT)))));

		server.getScheduler().performOneTick();

		assertSame(list, top(player));
		assertTrue(list.getItem(0).getItemMeta().getLore().toString().contains("7.0"));
	}

	@Test
	void refreshAndCursorChangeInvalidatePendingNavigation() {
		PlayerMock player = playerWith("starter");
		catalog.openInventory(player);
		Inventory list = top(player);
		server.getPluginManager().callEvent(click(player, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL));
		catalog.initializeItems();
		server.getScheduler().performOneTick();
		assertSame(list, top(player));

		server.getPluginManager().callEvent(click(player, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL));
		player.setItemOnCursor(new ItemStack(Material.EMERALD, 2));
		server.getScheduler().performOneTick();
		assertSame(list, top(player));
		assertEquals(new ItemStack(Material.EMERALD, 2), player.getItemOnCursor());
	}

	@ParameterizedTest
	@EnumSource(InventoryAction.class)
	void everyInventoryActionIsCancelledAndPreservesInventoryAndCursor(InventoryAction action) {
		PlayerMock player = playerWith("starter");
		catalog.openInventory(player);
		Inventory list = top(player);
		player.getInventory().setItem(0, new ItemStack(Material.EMERALD, 3));
		player.setItemOnCursor(new ItemStack(Material.DIAMOND, 2));
		ItemStack[] contents = list.getContents();
		for (int slot : new int[]{0, list.getSize(), -999}) {
			InventoryClickEvent event = click(player, slot, ClickType.LEFT, action);
			server.getPluginManager().callEvent(event);
			assertTrue(event.isCancelled(), action + " at " + slot);
		}
		server.getScheduler().performOneTick();

		assertSame(list, top(player));
		assertArrayEquals(contents, list.getContents());
		assertEquals(new ItemStack(Material.EMERALD, 3), player.getInventory().getItem(0));
		assertEquals(new ItemStack(Material.DIAMOND, 2), player.getItemOnCursor());
	}

	@Test
	void draggingAcrossBothInventoriesIsCancelled() {
		PlayerMock player = playerWith("starter");
		catalog.openInventory(player);
		ItemStack cursor = new ItemStack(Material.DIAMOND, 2);
		player.setItemOnCursor(cursor);
		InventoryDragEvent drag = new InventoryDragEvent(player.getOpenInventory(),
				new ItemStack(Material.AIR), cursor, false,
				Map.of(0, new ItemStack(Material.DIAMOND), top(player).getSize(), new ItemStack(Material.DIAMOND)));

		server.getPluginManager().callEvent(drag);

		assertTrue(drag.isCancelled());
		assertEquals(cursor, player.getItemOnCursor());
	}

	@Test
	void cancelledOpenLeavesNoCatalogListenerOrTask() {
		server.getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onOpen(InventoryOpenEvent event) {
				event.setCancelled(true);
			}
		}, harness);
		PlayerMock player = playerWith("starter");
		Inventory previous = top(player);

		catalog.openInventory(player);
		server.getScheduler().performOneTick();

		assertSame(previous, top(player));
		assertFalse(Arrays.stream(InventoryClickEvent.getHandlerList().getRegisteredListeners())
				.anyMatch(listener -> listener.getListener() == catalog));
		assertTrue(server.getScheduler().getPendingTasks().isEmpty());
	}

	@Test
	void disableDuringOpenClosesCatalogBeforeRemovingProtection() {
		Inventory[] opened = new Inventory[1];
		server.getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onOpen(InventoryOpenEvent event) {
				opened[0] = event.getInventory();
				when(plugin.isEnabled()).thenReturn(false);
			}
		}, harness);
		PlayerMock player = playerWith("starter");

		catalog.openInventory(player);

		assertNotSame(opened[0], top(player));
		assertFalse(Arrays.stream(InventoryClickEvent.getHandlerList().getRegisteredListeners())
				.anyMatch(listener -> listener.getListener() == catalog));
		assertTrue(server.getScheduler().getPendingTasks().isEmpty());
	}

	@Test
	void closingCatalogRejectsReopeningFromCloseCallbacks() {
		PlayerMock player = playerWith("starter");
		catalog.openInventory(player);
		boolean[] attempted = new boolean[1];
		int[] reopened = new int[1];
		server.getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onOpen(InventoryOpenEvent event) {
				if (event.getInventory().getType() == InventoryType.CHEST) {
					reopened[0]++;
				}
			}

			@EventHandler
			public void onClose(InventoryCloseEvent event) {
				if (!attempted[0]) {
					attempted[0] = true;
					catalog.openInventory(player);
				}
			}
		}, harness);

		catalog.closeAndUnregister();

		assertTrue(attempted[0]);
		assertEquals(0, reopened[0], "Retirement must reject opens before they reach InventoryOpenEvent");
		assertFalse(player.getOpenInventory().getType() == InventoryType.CHEST);
		assertFalse(Arrays.stream(InventoryClickEvent.getHandlerList().getRegisteredListeners())
				.anyMatch(listener -> listener.getListener() == catalog));
		assertTrue(server.getScheduler().getPendingTasks().isEmpty());
	}

	@Test
	void retirementDuringOpenCannotLeaveAnUnprotectedCatalog() {
		Inventory[] requested = new Inventory[1];
		server.getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onOpen(InventoryOpenEvent event) {
				requested[0] = event.getInventory();
				catalog.closeAndUnregister();
			}
		}, harness);
		PlayerMock player = playerWith("starter");

		catalog.openInventory(player);

		assertNotSame(requested[0], top(player));
		assertFalse(Arrays.stream(InventoryClickEvent.getHandlerList().getRegisteredListeners())
				.anyMatch(listener -> listener.getListener() == catalog));
		assertTrue(server.getScheduler().getPendingTasks().isEmpty());
	}

	private PlayerMock playerWith(String packageName) {
		PlayerMock player = server.addPlayer();
		player.addAttachment(harness, "airdrop.package." + packageName, true);
		return player;
	}

	private static Inventory top(PlayerMock player) {
		return player.getOpenInventory().getTopInventory();
	}

	private static List<String> names(Inventory inventory) {
		return Arrays.stream(inventory.getContents()).map(Gui::getPackageIconMarker)
				.filter(java.util.Objects::nonNull).toList();
	}

	private static InventoryClickEvent click(PlayerMock player, int slot, ClickType type, InventoryAction action) {
		return new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
				slot, type, action);
	}

	private static void setStatic(String name, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(null, value);
	}
}
