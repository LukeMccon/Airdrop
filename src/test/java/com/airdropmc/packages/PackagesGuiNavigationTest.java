package com.airdropmc.packages;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.plugin.PluginMock;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackagesGuiNavigationTest {
	private ServerMock server;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		PluginMock eventPlugin = MockBukkit.createMockPlugin("AirdropBrowserHarness");
		Airdrop plugin = mock(Airdrop.class);
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.getPluginLoader()).thenReturn(eventPlugin.getPluginLoader());
		when(plugin.getName()).thenReturn("Airdrop");
		when(plugin.getServer()).thenReturn(server);
		when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("PackagesGuiNavigationTest"));

		setAirdropStaticField("pluginInstance", plugin);
		PackageManager.publishPackages(Map.of(
				"starter", new Package("starter", 10.0, List.of(new ItemStack(Material.STONE, 2)))));
	}

	@AfterEach
	void tearDown() throws Exception {
		PackageManager.clear();
		clearAirdropStaticFields();
		MockBukkit.unmock();
	}

	@Test
	void packageEditorOpensOnNextTickOnly() {
		PlayerMock player = operator();
		PackagesGui browser = new PackagesGui();
		browser.openInventory(player);
		Inventory browserInventory = player.getOpenInventory().getTopInventory();
		InventoryClickEvent click = packageClick(player, browserInventory, null);

		browser.onInventoryClick(click);

		verify(click).setCancelled(true);
		assertSame(browserInventory, player.getOpenInventory().getTopInventory());
		server.getScheduler().performOneTick();
		assertNotSame(browserInventory, player.getOpenInventory().getTopInventory());
	}

	@Test
	void closeAndUnregisterClosesCurrentViewers() {
		PackagesGui browser = new PackagesGui();
		PlayerMock viewer = operator();
		browser.openInventory(viewer);
		Inventory browserInventory = viewer.getOpenInventory().getTopInventory();
		assertTrue(isClickListenerRegistered(browser));

		browser.closeAndUnregister();

		assertNotSame(browserInventory, viewer.getOpenInventory().getTopInventory());
		assertTrue(!isClickListenerRegistered(browser));
	}

	@Test
	void packageBrowserDisplaysPublishedPackageWithStableMarker() {
		PackagesGui browser = new PackagesGui();
		PlayerMock player = operator();
		browser.openInventory(player);
		Inventory inventory = player.getOpenInventory().getTopInventory();

		assertEquals("airdrop:package_icon", Gui.PACKAGE_ICON_MARKER_KEY.toString());
		assertEquals(List.of("starter"), displayedPackageMarkers(inventory));
	}

	@Test
	void packageBrowserDisplaysZeroPackages() {
		PackageManager.clear();
		PackagesGui browser = new PackagesGui();
		PlayerMock player = operator();
		browser.openInventory(player);

		assertTrue(displayedPackageMarkers(player.getOpenInventory().getTopInventory()).isEmpty());
	}

	@Test
	void packageBrowserDisplaysAllTwentySevenPackagesOnceInCaseInsensitiveOrder() {
		Map<String, Package> packages = new LinkedHashMap<>();
		List<String> insertionOrder = new ArrayList<>();
		for (int index = PackageManager.MAX_PACKAGES - 1; index >= 0; index--) {
			String name = index == 0 ? "Alpha" : index == 1 ? "beta" : "pkg" + index;
			insertionOrder.add(name);
			packages.put(name.toLowerCase(java.util.Locale.ROOT), new Package(name, index, List.of()));
		}
		PackageManager.publishPackages(packages);
		List<String> expected = insertionOrder.stream()
				.sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(java.util.Comparator.naturalOrder()))
				.toList();
		PackagesGui browser = new PackagesGui();
		PlayerMock player = operator();
		browser.openInventory(player);

		List<String> displayed = displayedPackageMarkers(player.getOpenInventory().getTopInventory());
		assertEquals(PackageManager.MAX_PACKAGES, displayed.size());
		assertEquals(expected, displayed);
		assertEquals(PackageManager.MAX_PACKAGES, displayed.stream().distinct().count());
	}

	@Test
	void repeatedPackageClicksOpenOnlyOneEditor() {
		PlayerMock player = operator();
		PackagesGui browser = new PackagesGui();
		browser.openInventory(player);
		Inventory browserInventory = player.getOpenInventory().getTopInventory();
		InventoryClickEvent first = packageClick(player, browserInventory, null);
		InventoryClickEvent second = packageClick(player, browserInventory, null);

		browser.onInventoryClick(first);
		browser.onInventoryClick(second);

		assertSame(browserInventory, player.getOpenInventory().getTopInventory());
		server.getScheduler().performOneTick();
		assertNotSame(browserInventory, player.getOpenInventory().getTopInventory());
	}

	@Test
	void deniedCursorAndActionNeverNavigate() {
		PlayerMock player = operator();
		PackagesGui browser = new PackagesGui();
		browser.openInventory(player);
		Inventory browserInventory = player.getOpenInventory().getTopInventory();
		InventoryClickEvent cursorClick = packageClick(
				player,
				browserInventory,
				new ItemStack(Material.DIAMOND));
		when(cursorClick.getClick()).thenReturn(ClickType.LEFT);
		when(cursorClick.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
		InventoryClickEvent shiftClick = packageClick(player, browserInventory, null);
		when(shiftClick.getClick()).thenReturn(ClickType.SHIFT_LEFT);
		when(shiftClick.getAction()).thenReturn(InventoryAction.MOVE_TO_OTHER_INVENTORY);

		browser.onInventoryClick(cursorClick);
		browser.onInventoryClick(shiftClick);
		server.getScheduler().performOneTick();

		verify(cursorClick).setCancelled(true);
		verify(shiftClick).setCancelled(true);
		assertSame(browserInventory, player.getOpenInventory().getTopInventory());
	}

	@Test
	void unmarkedOrWrongMaterialPackageIconsNeverNavigate() {
		for (ItemStack forged : List.of(
				new ItemStack(Material.CHEST),
				markedItem(Material.STONE, Gui.PACKAGE_ICON_MARKER_KEY, "starter"))) {
			PlayerMock player = operator();
			PackagesGui browser = new PackagesGui();
			browser.openInventory(player);
			Inventory browserInventory = player.getOpenInventory().getTopInventory();
			browserInventory.setItem(0, forged);

			InventoryClickEvent click = packageClick(player, browserInventory, null);
			browser.onInventoryClick(click);
			server.getScheduler().performOneTick();

			verify(click).setCancelled(true);
			assertSame(browserInventory, player.getOpenInventory().getTopInventory());
			browser.closeAndUnregister();
		}
	}

	@Test
	void packageIconMarkerMustMatchThePackageExpectedAtThatSlot() {
		PackageManager.publishPackages(Map.of(
				"alpha", new Package("alpha", 1.0, List.of()),
				"beta", new Package("beta", 2.0, List.of())));
		PlayerMock player = operator();
		PackagesGui browser = new PackagesGui();
		browser.openInventory(player);
		Inventory browserInventory = player.getOpenInventory().getTopInventory();
		browserInventory.setItem(1, browserInventory.getItem(0).clone());

		InventoryClickEvent click = packageClick(player, browserInventory, null, 1);
		browser.onInventoryClick(click);
		server.getScheduler().performOneTick();

		verify(click).setCancelled(true);
		assertSame(browserInventory, player.getOpenInventory().getTopInventory());
	}

	@Test
	void changedViewBeforeTickPreventsNavigation() {
		PlayerMock player = operator();
		PackagesGui browser = new PackagesGui();
		browser.openInventory(player);
		Inventory browserInventory = player.getOpenInventory().getTopInventory();
		browser.onInventoryClick(packageClick(player, browserInventory, null));
		Inventory newer = Bukkit.createInventory(null, 9, "newer");

		player.openInventory(newer);
		server.getScheduler().performOneTick();

		assertSame(newer, player.getOpenInventory().getTopInventory());
	}

	@Test
	void missingPackageBeforeTickLeavesBrowserOpen() {
		PlayerMock player = operator();
		PackagesGui browser = new PackagesGui();
		browser.openInventory(player);
		Inventory browserInventory = player.getOpenInventory().getTopInventory();
		browser.onInventoryClick(packageClick(player, browserInventory, null));

		PackageManager.clear();
		server.getScheduler().performOneTick();

		assertSame(browserInventory, player.getOpenInventory().getTopInventory());
	}

	@Test
	void refreshedSlotBeforeTickPreventsStaleNavigation() {
		PlayerMock player = operator();
		PackagesGui browser = new PackagesGui();
		browser.openInventory(player);
		Inventory browserInventory = player.getOpenInventory().getTopInventory();
		browser.onInventoryClick(packageClick(player, browserInventory, null));

		PackageManager.publishPackages(Map.of(
				"alpha", new Package("alpha", 1.0, List.of()),
				"starter", new Package("starter", 10.0, List.of())));
		browser.initializeItems();
		assertEquals(List.of("alpha", "starter"), displayedPackageMarkers(browserInventory));
		server.getScheduler().performOneTick();

		assertSame(browserInventory, player.getOpenInventory().getTopInventory());
	}

	@Test
	void eventWhoseActorDoesNotOwnTheViewCannotNavigate() {
		PlayerMock owner = operator();
		PlayerMock other = operator();
		PackagesGui browser = new PackagesGui();
		browser.openInventory(owner);
		Inventory browserInventory = owner.getOpenInventory().getTopInventory();
		InventoryClickEvent click = packageClick(owner, browserInventory, null);
		when(click.getWhoClicked()).thenReturn(other);

		browser.onInventoryClick(click);
		server.getScheduler().performOneTick();

		verify(click).setCancelled(true);
		assertSame(browserInventory, owner.getOpenInventory().getTopInventory());
		assertNotSame(browserInventory, other.getOpenInventory().getTopInventory());
	}

	private InventoryClickEvent packageClick(PlayerMock player, Inventory browser, ItemStack cursor) {
		return packageClick(player, browser, cursor, 0);
	}

	private InventoryClickEvent packageClick(
			PlayerMock player, Inventory browser, ItemStack cursor, int slot) {
		InventoryClickEvent event = mock(InventoryClickEvent.class);
		when(event.getInventory()).thenReturn(browser);
		when(event.getView()).thenReturn(player.getOpenInventory());
		when(event.getWhoClicked()).thenReturn(player);
		when(event.getClickedInventory()).thenReturn(browser);
		when(event.getCurrentItem()).thenReturn(browser.getItem(slot));
		when(event.getCursor()).thenReturn(cursor);
		when(event.getSlot()).thenReturn(slot);
		when(event.getClick()).thenReturn(ClickType.LEFT);
		when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
		return event;
	}

	private static List<String> displayedPackageMarkers(Inventory inventory) {
		List<String> markers = new ArrayList<>();
		for (ItemStack item : inventory.getContents()) {
			if (item != null) {
				markers.add(Gui.getPackageIconMarker(item));
			}
		}
		return markers;
	}

	private static ItemStack markedItem(Material material, NamespacedKey key, String value) {
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
		item.setItemMeta(meta);
		return item;
	}

	private static boolean isClickListenerRegistered(PackagesGui gui) {
		for (org.bukkit.plugin.RegisteredListener listener
				: InventoryClickEvent.getHandlerList().getRegisteredListeners()) {
			if (listener.getListener() == gui) {
				return true;
			}
		}
		return false;
	}

	private PlayerMock operator() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		return player;
	}

	private static void setAirdropStaticField(String fieldName, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}

	private static void clearAirdropStaticFields() throws Exception {
		for (Field field : Airdrop.class.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
				field.setAccessible(true);
				field.set(null, field.getType() == boolean.class ? false : null);
			}
		}
	}
}
