package com.airdropmc.packages;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackageEditorInventoryIntegrityTest {
	private ServerMock server;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		MockPlugin eventPlugin = MockBukkit.createMockPlugin("AirdropTestHarness");
		Airdrop plugin = mock(Airdrop.class);
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.getPluginLoader()).thenReturn(eventPlugin.getPluginLoader());
		when(plugin.getName()).thenReturn("Airdrop");
		when(plugin.getServer()).thenReturn(server);
		Airdrop.setPluginInstance(plugin);
	}

	@AfterEach
	void tearDown() {
		PackageGui.closeOpenEditors();
		try {
			setPackagesGui(null);
		} catch (ReflectiveOperationException error) {
			throw new IllegalStateException(error);
		}
		Airdrop.setPluginInstance(null);
		MockBukkit.unmock();
	}

	@Test
	void existingEditorOrdinaryClosePreservesUnrelatedInventory() {
		PlayerMock player = operator();
		PackageGui gui = new PackageGui(packageWithStone());
		assertTrue(gui.openInventory(player));
		assertPreservedAfterClose(player, gui::onInventoryClose);
	}

	@Test
	void createEditorOrdinaryClosePreservesUnrelatedInventory() {
		PlayerMock player = operator();
		CreatePackageGui gui = new CreatePackageGui("newpkg", 3.0);
		assertTrue(gui.openInventory(player));
		assertPreservedAfterClose(player, gui::onInventoryClose);
	}

	@Test
	void editorInstanceCannotBeOpenedTwice() {
		PlayerMock first = operator();
		PlayerMock second = operator();
		CreatePackageGui gui = new CreatePackageGui("newpkg", 3.0);

		assertTrue(gui.openInventory(first));
		Inventory editor = first.getOpenInventory().getTopInventory();
		assertFalse(gui.openInventory(second));
		assertSame(editor, first.getOpenInventory().getTopInventory());
	}

	@Test
	void closeOpenEditorsClosesEveryTrackedEditorOnly() {
		PlayerMock existingViewer = operator();
		PlayerMock createViewer = operator();
		PlayerMock unrelatedViewer = operator();
		PackageGui existing = new PackageGui(packageWithStone());
		CreatePackageGui create = new CreatePackageGui("newpkg", 3.0);
		assertTrue(existing.openInventory(existingViewer));
		assertTrue(create.openInventory(createViewer));
		Inventory existingInventory = existingViewer.getOpenInventory().getTopInventory();
		Inventory createInventory = createViewer.getOpenInventory().getTopInventory();
		Inventory unrelated = org.bukkit.Bukkit.createInventory(null, 9, "unrelated");
		unrelatedViewer.openInventory(unrelated);

		PackageGui.closeOpenEditors();

		assertNotSame(existingInventory, existingViewer.getOpenInventory().getTopInventory());
		assertNotSame(createInventory, createViewer.getOpenInventory().getTopInventory());
		assertSame(unrelated, unrelatedViewer.getOpenInventory().getTopInventory());
	}

	@Test
	void existingEditorCleanupNeverWritesPlayerState() {
		Player player = mockPlayer();
		PackageGui gui = new PackageGui(packageWithStone());
		Inventory editor = openMockPlayer(gui::openInventory, player);

		gui.onInventoryClose(closeEvent(player, editor));

		assertNoPlayerStateWrites(player);
	}

	@Test
	void createEditorCleanupNeverWritesPlayerState() {
		Player player = mockPlayer();
		CreatePackageGui gui = new CreatePackageGui("newpkg", 3.0);
		Inventory editor = openMockPlayer(gui::openInventory, player);

		gui.onInventoryClose(closeEvent(player, editor));

		assertNoPlayerStateWrites(player);
	}

	@Test
	void createEditorBottomLeftClickClonesWithoutConsumingSource() {
		PlayerMock player = operator();
		CreatePackageGui gui = new CreatePackageGui("newpkg", 3.0);
		assertTrue(gui.openInventory(player));
		Inventory editor = player.getOpenInventory().getTopInventory();
		player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
		ItemStack source = player.getInventory().getItem(0);

		InventoryClickEvent event = bottomClick(player, ClickType.LEFT, InventoryAction.PICKUP_ALL);
		assertSame(player.getInventory(), event.getClickedInventory());
		assertSame(source, event.getCurrentItem());
		gui.onInventoryClick(event);

		verify(event).setCancelled(true);
		assertSame(source, player.getInventory().getItem(0));
		assertEquals(5, source.getAmount());
		assertNotSame(source, editor.getItem(0));
		assertEquals(new ItemStack(Material.DIAMOND, 5), editor.getItem(0));
	}

	@Test
	void packageEditorBottomRightClickCopiesOneWithoutConsumingSource() {
		PlayerMock player = operator();
		PackageGui gui = new PackageGui(new Package("starter", 3.0, List.of()));
		assertTrue(gui.openInventory(player));
		Inventory editor = player.getOpenInventory().getTopInventory();
		player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
		ItemStack source = player.getInventory().getItem(0);

		InventoryClickEvent event = bottomClick(player, ClickType.RIGHT, InventoryAction.PICKUP_HALF);
		assertSame(player.getInventory(), event.getClickedInventory());
		assertSame(source, event.getCurrentItem());
		gui.onInventoryClick(event);

		verify(event).setCancelled(true);
		assertSame(source, player.getInventory().getItem(0));
		assertEquals(5, source.getAmount());
		assertNotSame(source, editor.getItem(0));
		assertEquals(new ItemStack(Material.DIAMOND, 1), editor.getItem(0));
	}

	@Test
	void shiftClickFromPlayerInventoryIsACancelledNoOp() {
		PlayerMock player = operator();
		PackageGui gui = new PackageGui(new Package("starter", 3.0, List.of()));
		assertTrue(gui.openInventory(player));
		Inventory editor = player.getOpenInventory().getTopInventory();
		player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
		ItemStack source = player.getInventory().getItem(0);

		InventoryClickEvent event = bottomClick(
				player,
				ClickType.SHIFT_LEFT,
				InventoryAction.MOVE_TO_OTHER_INVENTORY);
		assertSame(player.getInventory(), event.getClickedInventory());
		assertSame(source, event.getCurrentItem());
		gui.onInventoryClick(event);

		verify(event).setCancelled(true);
		assertSame(source, player.getInventory().getItem(0));
		assertNull(editor.getItem(0));
	}

	@Test
	void hotbarSwapInEditableSlotIsACancelledNoOp() {
		PlayerMock player = operator();
		PackageGui gui = new PackageGui(packageWithStone());
		assertTrue(gui.openInventory(player));
		Inventory editor = player.getOpenInventory().getTopInventory();
		ItemStack original = editor.getItem(0);

		InventoryClickEvent event = topClick(player, 0, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP);
		assertSame(editor, event.getClickedInventory());
		assertSame(original, event.getCurrentItem());
		gui.onInventoryClick(event);

		verify(event).setCancelled(true);
		assertSame(original, editor.getItem(0));
		assertEquals(2, editor.getItem(0).getAmount());
	}

	@Test
	void nonEmptyCursorInEditableSlotIsACancelledNoOp() {
		PlayerMock player = operator();
		CreatePackageGui gui = new CreatePackageGui("newpkg", 3.0);
		assertTrue(gui.openInventory(player));
		Inventory editor = player.getOpenInventory().getTopInventory();
		editor.setItem(0, new ItemStack(Material.DIRT, 3));
		ItemStack original = editor.getItem(0);
		player.setItemOnCursor(new ItemStack(Material.STONE, 2));

		InventoryClickEvent event = topClick(player, 0, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
		assertSame(editor, event.getClickedInventory());
		assertEquals(Material.STONE, event.getCursor().getType());
		gui.onInventoryClick(event);

		verify(event).setCancelled(true);
		assertSame(original, editor.getItem(0));
		assertEquals(3, editor.getItem(0).getAmount());
	}

	@Test
	void allDragShapesAreCancelledWithoutMutation() {
		PlayerMock player = operator();
		CreatePackageGui gui = new CreatePackageGui("newpkg", 3.0);
		assertTrue(gui.openInventory(player));
		Inventory editor = player.getOpenInventory().getTopInventory();

		for (Set<Integer> rawSlots : List.of(Set.of(0), Set.of(36), Set.of(0, 36))) {
			InventoryDragEvent event = mock(InventoryDragEvent.class);
			when(event.getView()).thenReturn(player.getOpenInventory());
			when(event.getWhoClicked()).thenReturn(player);
			when(event.getRawSlots()).thenReturn(rawSlots);

			gui.onInventoryClick(event);

			verify(event).setCancelled(true);
			assertNull(editor.getItem(0));
		}
	}

	@Test
	void createCancelWaitsOneTickAndBlocksRepeatedInteractions() {
		PlayerMock player = operator();
		CreatePackageGui gui = new CreatePackageGui("newpkg", 3.0);
		assertTrue(gui.openInventory(player));
		Inventory editor = player.getOpenInventory().getTopInventory();

		InventoryClickEvent cancel = topClick(
				player,
				editor.getSize() - 1,
				ClickType.LEFT,
				InventoryAction.PICKUP_ALL);
		gui.onInventoryClick(cancel);

		verify(cancel).setCancelled(true);
		assertSame(editor, player.getOpenInventory().getTopInventory());

		player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 5));
		InventoryClickEvent repeated = bottomClick(player, ClickType.LEFT, InventoryAction.PICKUP_ALL);
		gui.onInventoryClick(repeated);
		verify(repeated).setCancelled(true);
		assertNull(editor.getItem(0));

		server.getScheduler().performOneTick();

		assertFalse(player.getOpenInventory().getType() == InventoryType.CHEST);
	}

	@Test
	void deferredCancelNeverClosesANewerView() {
		PlayerMock player = operator();
		PackageGui gui = new PackageGui(packageWithStone());
		assertTrue(gui.openInventory(player));
		Inventory editor = player.getOpenInventory().getTopInventory();
		InventoryClickEvent cancel = topClick(
				player,
				editor.getSize() - 1,
				ClickType.LEFT,
				InventoryAction.PICKUP_ALL);

		gui.onInventoryClick(cancel);
		assertSame(editor, player.getOpenInventory().getTopInventory());

		Inventory newer = org.bukkit.Bukkit.createInventory(null, 9, "newer");
		player.openInventory(newer);
		server.getScheduler().performOneTick();

		assertSame(newer, player.getOpenInventory().getTopInventory());
	}

	@Test
	void backNavigationRunsOnlyOnTheNextTick() throws Exception {
		PlayerMock player = operator();
		PackageGui gui = new PackageGui(packageWithStone());
		assertTrue(gui.openInventory(player));
		PackagesGui browser = mock(PackagesGui.class);
		setPackagesGui(browser);
		Inventory editor = player.getOpenInventory().getTopInventory();
		InventoryClickEvent back = topClick(
				player,
				editor.getSize() - 3,
				ClickType.LEFT,
				InventoryAction.PICKUP_ALL);

		gui.onInventoryClick(back);

		assertSame(editor, player.getOpenInventory().getTopInventory());
		verify(browser, never()).openInventory(player);
		server.getScheduler().performOneTick();
		verify(browser).openInventory(player);
	}

	@Test
	void kickRemainsProtectedUntilDeferredDisconnectObservation() {
		Player player = mockPlayer();
		CreatePackageGui gui = new CreatePackageGui("newpkg", 3.0);
		Inventory editor = openMockPlayer(gui::openInventory, player);
		when(player.isOnline()).thenReturn(false);
		PlayerKickEvent kick = mock(PlayerKickEvent.class);
		when(kick.getPlayer()).thenReturn(player);

		gui.onPlayerKick(kick);

		InventoryClickEvent beforeTick = protectedClick(player);
		gui.onInventoryClick(beforeTick);
		verify(beforeTick).setCancelled(true);

		server.getScheduler().performOneTick();

		InventoryClickEvent afterTick = protectedClick(player);
		gui.onInventoryClick(afterTick);
		verify(afterTick, never()).setCancelled(true);
		assertSame(editor, player.getOpenInventory().getTopInventory());
	}

	@Test
	void kickCleanupIgnoresCancelledEventsAtMonitorPriority() throws Exception {
		for (Class<?> editor : List.of(CreatePackageGui.class, PackageGui.class)) {
			EventHandler handler = editor.getMethod("onPlayerKick", PlayerKickEvent.class)
					.getAnnotation(EventHandler.class);
			assertSame(EventPriority.MONITOR, handler.priority());
			assertTrue(handler.ignoreCancelled());
		}
	}

	@Test
	void editorInteractionsCancelAtHighestPriority() throws Exception {
		for (Class<?> editor : List.of(CreatePackageGui.class, PackageGui.class)) {
			for (Class<?> eventType : List.of(InventoryClickEvent.class, InventoryDragEvent.class)) {
				EventHandler handler = editor.getMethod("onInventoryClick", eventType)
						.getAnnotation(EventHandler.class);
				assertSame(EventPriority.HIGHEST, handler.priority());
				assertFalse(handler.ignoreCancelled());
			}
		}
	}

	@Test
	void packageEditorsShareInheritedLifecycleHandlers() throws Exception {
		Class<?> sharedEditor = Class.forName("com.airdropmc.packages.PackageEditorGui");
		assertSame(sharedEditor, CreatePackageGui.class.getSuperclass());
		assertSame(sharedEditor, PackageGui.class.getSuperclass());

		List<Method> lifecycleMethods = List.of(
				CreatePackageGui.class.getMethod("openInventory", Player.class),
				CreatePackageGui.class.getMethod("onInventoryClick", InventoryClickEvent.class),
				CreatePackageGui.class.getMethod("onInventoryClick", InventoryDragEvent.class),
				CreatePackageGui.class.getMethod("onInventoryClose", InventoryCloseEvent.class),
				CreatePackageGui.class.getMethod("onPlayerQuit", org.bukkit.event.player.PlayerQuitEvent.class),
				CreatePackageGui.class.getMethod("onPlayerKick", PlayerKickEvent.class),
				CreatePackageGui.class.getMethod("getName"),
				CreatePackageGui.class.getMethod("save", InventoryClickEvent.class),
				CreatePackageGui.class.getMethod("cancel", InventoryClickEvent.class));

		for (Method method : lifecycleMethods) {
			assertSame(sharedEditor, method.getDeclaringClass());
			assertTrue(Modifier.isPublic(method.getModifiers()));
			assertFalse(Modifier.isFinal(method.getModifiers()));
		}
	}

	@Test
	void inheritedClickHandlersAreDispatchedForBothEditors() {
		PlayerMock createPlayer = operator();
		CreatePackageGui createGui = new CreatePackageGui("newpkg", 3.0);
		assertTrue(createGui.openInventory(createPlayer));
		createPlayer.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 2));
		InventoryClickEvent createClick = bottomClick(
				createPlayer, ClickType.LEFT, InventoryAction.PICKUP_ALL);
		when(createClick.getHandlers()).thenReturn(InventoryClickEvent.getHandlerList());

		server.getPluginManager().callEvent(createClick);

		verify(createClick).setCancelled(true);
		assertEquals(new ItemStack(Material.DIAMOND, 2),
				createPlayer.getOpenInventory().getTopInventory().getItem(0));

		PlayerMock updatePlayer = operator();
		PackageGui updateGui = new PackageGui(new Package("starter", 3.0, List.of()));
		assertTrue(updateGui.openInventory(updatePlayer));
		updatePlayer.getInventory().setItem(0, new ItemStack(Material.EMERALD, 3));
		InventoryClickEvent updateClick = bottomClick(
				updatePlayer, ClickType.RIGHT, InventoryAction.PICKUP_HALF);
		when(updateClick.getHandlers()).thenReturn(InventoryClickEvent.getHandlerList());

		server.getPluginManager().callEvent(updateClick);

		verify(updateClick).setCancelled(true);
		assertEquals(new ItemStack(Material.EMERALD, 1),
				updatePlayer.getOpenInventory().getTopInventory().getItem(0));
	}

	private void assertPreservedAfterClose(PlayerMock player, java.util.function.Consumer<InventoryCloseEvent> closeHandler) {
		player.getInventory().setItem(0, new ItemStack(Material.GOLD_INGOT, 4));
		ItemStack unrelated = player.getInventory().getItem(0);

		InventoryCloseEvent closeEvent = mock(InventoryCloseEvent.class);
		when(closeEvent.getPlayer()).thenReturn(player);
		when(closeEvent.getInventory()).thenReturn(player.getOpenInventory().getTopInventory());
		closeHandler.accept(closeEvent);

		assertSame(unrelated, player.getInventory().getItem(0));
	}

	private Player mockPlayer() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
		return player;
	}

	private Inventory openMockPlayer(java.util.function.Function<Player, Boolean> opener, Player player) {
		InventoryView view = mock(InventoryView.class);
		final Inventory[] opened = new Inventory[1];
		when(player.openInventory(any(Inventory.class))).thenAnswer(invocation -> {
			opened[0] = invocation.getArgument(0);
			when(view.getTopInventory()).thenReturn(opened[0]);
			return view;
		});
		when(player.getOpenInventory()).thenReturn(view);
		assertTrue(opener.apply(player));
		return opened[0];
	}

	private InventoryClickEvent protectedClick(Player player) {
		InventoryClickEvent event = mock(InventoryClickEvent.class);
		InventoryView view = player.getOpenInventory();
		when(event.getView()).thenReturn(view);
		when(event.getWhoClicked()).thenReturn(player);
		return event;
	}

	private InventoryCloseEvent closeEvent(Player player, Inventory editor) {
		InventoryCloseEvent event = mock(InventoryCloseEvent.class);
		when(event.getPlayer()).thenReturn(player);
		when(event.getInventory()).thenReturn(editor);
		return event;
	}

	private InventoryClickEvent bottomClick(
			PlayerMock player,
			ClickType click,
			InventoryAction action) {
		return clickEvent(player, player.getInventory(), 0, click, action);
	}

	private InventoryClickEvent topClick(
			PlayerMock player,
			int slot,
			ClickType click,
			InventoryAction action) {
		return clickEvent(player, player.getOpenInventory().getTopInventory(), slot, click, action);
	}

	private InventoryClickEvent clickEvent(
			PlayerMock player,
			Inventory clickedInventory,
			int slot,
			ClickType click,
			InventoryAction action) {
		InventoryClickEvent event = mock(InventoryClickEvent.class);
		when(event.getView()).thenReturn(player.getOpenInventory());
		when(event.getWhoClicked()).thenReturn(player);
		when(event.getClickedInventory()).thenReturn(clickedInventory);
		when(event.getCurrentItem()).thenReturn(clickedInventory.getItem(slot));
		when(event.getCursor()).thenReturn(player.getItemOnCursor());
		when(event.getSlot()).thenReturn(slot);
		when(event.getClick()).thenReturn(click);
		when(event.getAction()).thenReturn(action);
		when(event.isRightClick()).thenReturn(click.isRightClick());
		return event;
	}

	private void assertNoPlayerStateWrites(Player player) {
		verify(player.getInventory(), never()).setContents(any(ItemStack[].class));
		verify(player, never()).setItemOnCursor(any());
		verify(player, never()).updateInventory();
	}

	private void setPackagesGui(PackagesGui packagesGui) throws ReflectiveOperationException {
		Field field = Airdrop.class.getDeclaredField("packagesGui");
		field.setAccessible(true);
		field.set(null, packagesGui);
	}

	private PlayerMock operator() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		return player;
	}

	private Package packageWithStone() {
		return new Package("starter", 3.0, List.of(new ItemStack(Material.STONE, 2)));
	}
}
