package com.airdropmc.packages;

import com.airdropmc.Airdrop;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PackageReadOnlyViewTest {

	private ServerMock server;
	private Airdrop plugin;
	private PluginMock eventPlugin;
	private Package pkg;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		eventPlugin = MockBukkit.createMockPlugin("PackagePreviewHarness");
		plugin = mock(Airdrop.class);
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.getPluginLoader()).thenReturn(eventPlugin.getPluginLoader());
		when(plugin.getName()).thenReturn("Airdrop");
		when(plugin.getServer()).thenReturn(server);
		Airdrop.setPluginInstance(plugin);
		setReady(true);
		ItemStack reward = new ItemStack(Material.DIAMOND_SWORD);
		ItemMeta meta = reward.getItemMeta();
		meta.setDisplayName("Named reward");
		meta.setLore(List.of("Original lore"));
		meta.addEnchant(Enchantment.SHARPNESS, 3, true);
		meta.getPersistentDataContainer().set(new NamespacedKey("test", "reward"), PersistentDataType.STRING, "original");
		reward.setItemMeta(meta);
		pkg = new Package("starter", 0, List.of(reward));
		PackageManager.publishPackages(Map.of("starter", pkg));
	}

	@AfterEach
	void tearDown() throws Exception {
		PackageGui.closeOpenEditors();
		PackageManager.clear();
		Airdrop.setPluginInstance(null);
		setReady(false);
		MockBukkit.unmock();
	}

	@Test
	void readerGetsUnmodifiedRewardCopiesAndReadOnlyControls() {
		PlayerMock reader = reader();
		PackageGui gui = new PackageGui(pkg);
		assertTrue(gui.openInventory(reader));
		Inventory inventory = reader.getOpenInventory().getTopInventory();

		assertEquals(pkg.getItems().getFirst(), inventory.getItem(0));
		assertNull(inventory.getItem(34), "Readers must not see Save");
		assertEquals("back", controlMarker(inventory.getItem(33)));
		assertEquals("cancel", controlMarker(inventory.getItem(35)));
		assertTrue(inventory.getItem(32).getItemMeta().getLore().stream().anyMatch(line -> line.contains("Read-only")));
		assertTrue(inventory.getItem(27).getItemMeta().getLore().stream().anyMatch(line -> line.contains("/airdrop starter")));
		assertTrue(inventory.getItem(27).getItemMeta().getLore().stream().anyMatch(line -> line.contains("Free")));
	}

	@Test
	void idleFreshnessChecksDoNotCloneRewardStacks() {
		AtomicInteger clones = new AtomicInteger();
		ItemStack reward = new CloneCountingItemStack(pkg.getItems().getFirst(), clones);
		pkg = new Package("starter", 0, Collections.nCopies(27, reward));
		PackageManager.publishPackages(Map.of("starter", pkg));
		PlayerMock reader = reader();
		assertTrue(new PackageGui(pkg).openInventory(reader));
		Inventory preview = reader.getOpenInventory().getTopInventory();
		PlayerMock admin = reader();
		admin.setOp(true);
		assertTrue(new PackageGui(pkg).openInventory(admin));
		Inventory editor = admin.getOpenInventory().getTopInventory();
		assertTrue(clones.get() > 0, "Opening views must still detach their reward stacks");
		clones.set(0);

		server.getScheduler().performTicks(20);

		assertSame(preview, reader.getOpenInventory().getTopInventory());
		assertSame(editor, admin.getOpenInventory().getTopInventory());
		assertEquals(0, clones.get(), "Idle reader and admin checks must not clone rewards");
	}

	@Test
	void directSaveByReaderOrForeignAdministratorNeverWrites() {
		PlayerMock reader = reader();
		PackageGui gui = new PackageGui(pkg);
		assertTrue(gui.openInventory(reader));
		gui.save(click(reader, 34, ClickType.LEFT, InventoryAction.PICKUP_ALL));
		PlayerMock other = server.addPlayer();
		other.setOp(true);
		other.openInventory(Bukkit.createInventory(null, 9, "Other view"));
		gui.save(click(other, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL));

		verify(plugin, never()).updatePackageInventoryAsync(any(), any());
	}

	@Test
	void permissionsAndModeChangesCloseIdleViewsNextTick() {
		PlayerMock reader = server.addPlayer();
		PermissionAttachment permission = reader.addAttachment(eventPlugin, "airdrop.package.starter", true);
		PackageGui gui = new PackageGui(pkg);
		assertTrue(gui.openInventory(reader));
		Inventory inventory = reader.getOpenInventory().getTopInventory();
		permission.setPermission("airdrop.package.starter", false);

		server.getScheduler().performOneTick();
		assertNotSame(inventory, reader.getOpenInventory().getTopInventory());

		permission.setPermission("airdrop.package.starter", true);
		assertTrue(new PackageGui(pkg).openInventory(reader));
		inventory = reader.getOpenInventory().getTopInventory();
		reader.setOp(true);
		server.getScheduler().performOneTick();
		assertNotSame(inventory, reader.getOpenInventory().getTopInventory());
	}

	@Test
	void replacedDefinitionClosesPreviewWithoutInput() {
		PlayerMock reader = reader();
		assertTrue(new PackageGui(pkg).openInventory(reader));
		Inventory inventory = reader.getOpenInventory().getTopInventory();
		PackageManager.publishPackages(Map.of("starter", new Package("starter", 5, List.of())));

		server.getScheduler().performOneTick();
		assertNotSame(inventory, reader.getOpenInventory().getTopInventory());
	}

	@Test
	void unauthorizedAndStaleDefinitionsCannotOpen() {
		assertFalse(new PackageGui(pkg).openInventory(server.addPlayer()));
		PackageManager.publishPackages(Map.of("starter", new Package("starter", 5, List.of())));
		assertFalse(new PackageGui(pkg).openInventory(reader()));
		assertFalse(new CreatePackageGui("newpkg", 1).openInventory(reader()));
	}

	@Test
	void everyReaderClickAndCreativeInventoryEventIsCancelledWithoutMutation() {
		PlayerMock reader = reader();
		PackageGui gui = new PackageGui(pkg);
		assertTrue(gui.openInventory(reader));
		Inventory inventory = reader.getOpenInventory().getTopInventory();
		reader.getInventory().setItem(0, new ItemStack(Material.GOLD_INGOT, 7));
		ItemStack[] contents = inventory.getContents();
		ItemStack[] playerItems = reader.getInventory().getContents();
		for (ClickType click : ClickType.values()) {
			for (int slot : new int[]{0, 36, -999}) {
				InventoryClickEvent event = click(reader, slot, click, InventoryAction.PICKUP_ALL);
				server.getPluginManager().callEvent(event);
				assertTrue(event.isCancelled(), click + " at " + slot);
			}
		}
		for (InventoryAction action : InventoryAction.values()) {
			InventoryClickEvent event = click(reader, 0, ClickType.LEFT, action);
			server.getPluginManager().callEvent(event);
			assertTrue(event.isCancelled(), action.toString());
		}
		InventoryCreativeEvent creative = new InventoryCreativeEvent(reader.getOpenInventory(),
				InventoryType.SlotType.CONTAINER, 0, new ItemStack(Material.DIAMOND, 64));
		server.getPluginManager().callEvent(creative);
		assertTrue(creative.isCancelled());
		assertArrayEquals(contents, inventory.getContents());
		assertArrayEquals(playerItems, reader.getInventory().getContents());
		assertEquals(pkg.getItems().getFirst(), inventory.getItem(0));
	}

	@Test
	void dragsAcrossTopBottomOrBothAreCancelledAndMetadataIsIsolatedBetweenReaders() {
		PlayerMock first = reader();
		PlayerMock second = reader();
		assertTrue(new PackageGui(pkg).openInventory(first));
		assertTrue(new PackageGui(pkg).openInventory(second));
		for (Map<Integer, ItemStack> slots : List.of(
				Map.of(0, new ItemStack(Material.STONE)),
				Map.of(36, new ItemStack(Material.STONE)),
				Map.of(0, new ItemStack(Material.STONE), 36, new ItemStack(Material.STONE)))) {
			InventoryDragEvent drag = new InventoryDragEvent(first.getOpenInventory(),
					new ItemStack(Material.STONE), new ItemStack(Material.STONE, 3), false, slots);
			server.getPluginManager().callEvent(drag);
			assertTrue(drag.isCancelled());
		}
		ItemStack displayed = first.getOpenInventory().getTopInventory().getItem(0);
		ItemMeta changed = displayed.getItemMeta();
		changed.setDisplayName("changed");
		changed.setLore(List.of("changed"));
		displayed.setItemMeta(changed);
		displayed.setAmount(4);
		assertEquals(pkg.getItems().getFirst(), second.getOpenInventory().getTopInventory().getItem(0));
		assertEquals("Named reward", pkg.getItems().getFirst().getItemMeta().getDisplayName());
	}

	@Test
	void savingEditorSurvivesPublicationButPermissionLossMakesCompletionInert() {
		PlayerMock admin = reader();
		admin.setOp(true);
		PackageGui gui = new PackageGui(pkg);
		assertTrue(gui.openInventory(admin));
		Inventory inventory = admin.getOpenInventory().getTopInventory();
		CompletableFuture<Boolean> save = new CompletableFuture<>();
		when(plugin.updatePackageInventoryAsync(any(), any())).thenReturn(save);
		gui.save(click(admin, 34, ClickType.LEFT, InventoryAction.PICKUP_ALL));
		PackageManager.publishPackages(Map.of("starter", new Package("starter", 5, List.of())));
		server.getScheduler().performOneTick();
		assertSame(inventory, admin.getOpenInventory().getTopInventory());
		admin.setOp(false);
		server.getScheduler().performOneTick();
		assertNotSame(inventory, admin.getOpenInventory().getTopInventory());
		Inventory newer = Bukkit.createInventory(null, 9, "newer");
		admin.openInventory(newer);
		save.complete(true);
		server.getScheduler().performOneTick();
		assertSame(newer, admin.getOpenInventory().getTopInventory());
	}

	@Test
	void failedSaveAfterReplacementClosesInsteadOfResumingStaleDraft() {
		PlayerMock admin = reader();
		admin.setOp(true);
		PackageGui gui = new PackageGui(pkg);
		assertTrue(gui.openInventory(admin));
		Inventory inventory = admin.getOpenInventory().getTopInventory();
		CompletableFuture<Boolean> save = new CompletableFuture<>();
		when(plugin.updatePackageInventoryAsync(any(), any())).thenReturn(save);
		gui.save(click(admin, 34, ClickType.LEFT, InventoryAction.PICKUP_ALL));
		PackageManager.clear();
		save.complete(false);
		server.getScheduler().performOneTick();
		assertNotSame(inventory, admin.getOpenInventory().getTopInventory());
	}

	@Test
	void fullPackageRetainsAllRewardSlotsAndTaggedRewardsCannotNavigate() {
		ItemStack tagged = new ItemStack(Material.BLUE_WOOL);
		ItemMeta meta = tagged.getItemMeta();
		meta.getPersistentDataContainer().set(Gui.CONTROL_MARKER_KEY, PersistentDataType.STRING, "back");
		tagged.setItemMeta(meta);
		pkg = new Package("starter", 12.5, Collections.nCopies(27, tagged));
		PackageManager.publishPackages(Map.of("starter", pkg));
		PlayerMock reader = reader();
		PackageGui gui = new PackageGui(pkg);
		assertTrue(gui.openInventory(reader));
		Inventory inventory = reader.getOpenInventory().getTopInventory();
		for (int slot = 0; slot < 27; slot++) {
			assertEquals(tagged, inventory.getItem(slot));
			server.getPluginManager().callEvent(click(reader, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL));
		}
		server.getScheduler().performOneTick();
		assertSame(inventory, reader.getOpenInventory().getTopInventory());
		assertEquals(Material.PAPER, inventory.getItem(27).getType());
		assertTrue(inventory.getItem(27).getItemMeta().getLore().getFirst().contains("12.5"));
	}

	@Test
	void forgedSaveControlCannotTurnReaderViewIntoAnEditor() {
		PlayerMock reader = reader();
		PackageGui gui = new PackageGui(pkg);
		assertTrue(gui.openInventory(reader));
		Inventory inventory = reader.getOpenInventory().getTopInventory();
		ItemStack forged = new ItemStack(Material.GREEN_WOOL);
		ItemMeta meta = forged.getItemMeta();
		meta.getPersistentDataContainer().set(Gui.CONTROL_MARKER_KEY, PersistentDataType.STRING, "save");
		forged.setItemMeta(meta);
		inventory.setItem(34, forged);
		InventoryClickEvent event = click(reader, 34, ClickType.LEFT, InventoryAction.PICKUP_ALL);
		server.getPluginManager().callEvent(event);
		assertTrue(event.isCancelled());
		verify(plugin, never()).updatePackageInventoryAsync(any(), any());
	}

	@Test
	void readerCloseWaitsOneTickAndStopsItsListenerAndTask() {
		PlayerMock reader = reader();
		PackageGui gui = new PackageGui(pkg);
		assertTrue(gui.openInventory(reader));
		Inventory inventory = reader.getOpenInventory().getTopInventory();
		server.getPluginManager().callEvent(click(reader, 35, ClickType.LEFT, InventoryAction.PICKUP_ALL));
		assertSame(inventory, reader.getOpenInventory().getTopInventory());
		server.getScheduler().performOneTick();
		assertNotSame(inventory, reader.getOpenInventory().getTopInventory());
		assertFalse(hasClickListener(gui));
		assertTrue(server.getScheduler().getPendingTasks().stream().noneMatch(task -> task.getOwner() == plugin));
		assertNull(reader.nextComponentMessage(), "Closing a preview must not report canceled edits");
	}

	@Test
	void canceledOpenLeavesNoListenerOrTask() {
		server.getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onOpen(InventoryOpenEvent event) {
				event.setCancelled(true);
			}
		}, eventPlugin);
		PackageGui gui = new PackageGui(pkg);
		assertFalse(gui.openInventory(reader()));
		assertFalse(hasClickListener(gui));
		assertTrue(server.getScheduler().getPendingTasks().stream().noneMatch(task -> task.getOwner() == plugin));
	}

	@Test
	void permissionRevokedDuringOpenClosesRewardsBeforeRetiringProtection() {
		PlayerMock reader = server.addPlayer();
		PermissionAttachment permission = reader.addAttachment(eventPlugin, "airdrop.package.starter", true);
		Inventory[] requested = new Inventory[1];
		server.getPluginManager().registerEvents(new Listener() {
			@EventHandler
			public void onOpen(InventoryOpenEvent event) {
				requested[0] = event.getInventory();
				permission.setPermission("airdrop.package.starter", false);
			}
		}, eventPlugin);
		PackageGui gui = new PackageGui(pkg);
		assertFalse(gui.openInventory(reader));
		assertNotSame(requested[0], reader.getOpenInventory().getTopInventory());
		assertFalse(hasClickListener(gui));
	}

	@Test
	void canceledKickLeavesTheAdminViewUsable() {
		PlayerMock admin = reader();
		admin.setOp(true);
		assertTrue(new PackageGui(pkg).openInventory(admin));
		Inventory inventory = admin.getOpenInventory().getTopInventory();
		PlayerKickEvent kick = new PlayerKickEvent(admin,
				net.kyori.adventure.text.Component.text("test"), net.kyori.adventure.text.Component.text("test"));
		kick.setCancelled(true);
		server.getPluginManager().callEvent(kick);
		server.getScheduler().performOneTick();
		InventoryClickEvent remove = click(admin, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
		server.getPluginManager().callEvent(remove);
		assertTrue(remove.isCancelled());
		assertSame(inventory, admin.getOpenInventory().getTopInventory());
		assertNull(inventory.getItem(0), "Canceled kicks must leave editing active");
	}

	private static boolean hasClickListener(PackageGui gui) {
		for (org.bukkit.plugin.RegisteredListener listener : InventoryClickEvent.getHandlerList().getRegisteredListeners()) {
			if (listener.getListener() == gui) {
				return true;
			}
		}
		return false;
	}

	private PlayerMock reader() {
		PlayerMock reader = server.addPlayer();
		reader.addAttachment(eventPlugin, "airdrop.package.starter", true);
		return reader;
	}

	private static String controlMarker(ItemStack item) {
		return item.getItemMeta().getPersistentDataContainer().get(Gui.CONTROL_MARKER_KEY, PersistentDataType.STRING);
	}

	private static InventoryClickEvent click(PlayerMock player, int slot, ClickType click, InventoryAction action) {
		return new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, slot, click, action);
	}

	private static void setReady(boolean ready) throws Exception {
		Field field = Airdrop.class.getDeclaredField("ready");
		field.setAccessible(true);
		field.set(null, ready);
	}

	private static final class CloneCountingItemStack extends ItemStack {
		private final AtomicInteger clones;

		private CloneCountingItemStack(ItemStack source, AtomicInteger clones) {
			super(source.getType(), source.getAmount());
			setItemMeta(source.getItemMeta());
			this.clones = clones;
		}

		@Override
		public ItemStack clone() {
			clones.incrementAndGet();
			return new CloneCountingItemStack(this, clones);
		}
	}
}
