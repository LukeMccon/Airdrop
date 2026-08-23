package com.airdropmc.packages;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
		assertTrue(opener.apply(player));
		return opened[0];
	}

	private InventoryCloseEvent closeEvent(Player player, Inventory editor) {
		InventoryCloseEvent event = mock(InventoryCloseEvent.class);
		when(event.getPlayer()).thenReturn(player);
		when(event.getInventory()).thenReturn(editor);
		return event;
	}

	private void assertNoPlayerStateWrites(Player player) {
		verify(player.getInventory(), never()).setContents(any(ItemStack[].class));
		verify(player, never()).setItemOnCursor(any());
		verify(player, never()).updateInventory();
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
