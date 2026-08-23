package com.airdropmc.packages;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.PackagesConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackagesGuiNavigationTest {
	private ServerMock server;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		MockPlugin eventPlugin = MockBukkit.createMockPlugin("AirdropBrowserHarness");
		Airdrop plugin = mock(Airdrop.class);
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.getPluginLoader()).thenReturn(eventPlugin.getPluginLoader());
		when(plugin.getName()).thenReturn("Airdrop");
		when(plugin.getServer()).thenReturn(server);

		YamlConfiguration config = new YamlConfiguration();
		config.set("packages.starter.price", 10.0);
		config.set("packages.starter.items", List.of(new ItemStack(Material.STONE, 2)));
		PackagesConfig packagesConfig = mock(PackagesConfig.class);
		when(packagesConfig.getConfig()).thenReturn(config);
		when(packagesConfig.saveConfig(any(FileConfiguration.class))).thenReturn(true);

		setAirdropStaticField("pluginInstance", plugin);
		setAirdropStaticField("packagesConfiguration", packagesConfig);
		PackageManager.clear();
		assertTrue(PackageManager.reload());
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
		InventoryClickEvent event = mock(InventoryClickEvent.class);
		when(event.getInventory()).thenReturn(browser);
		when(event.getView()).thenReturn(player.getOpenInventory());
		when(event.getWhoClicked()).thenReturn(player);
		when(event.getClickedInventory()).thenReturn(browser);
		when(event.getCurrentItem()).thenReturn(browser.getItem(0));
		when(event.getCursor()).thenReturn(cursor);
		when(event.getSlot()).thenReturn(0);
		when(event.getClick()).thenReturn(ClickType.LEFT);
		when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
		return event;
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
				field.set(null, null);
			}
		}
	}
}
