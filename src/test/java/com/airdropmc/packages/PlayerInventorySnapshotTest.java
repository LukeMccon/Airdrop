package com.airdropmc.packages;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerInventorySnapshotTest {

	@Test
	void restore_putsBackClonedInventoryAndCursor() {
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);

		ItemStack originalStack = new ItemStack(Material.DIRT, 1);
		ItemStack[] originalContents = new ItemStack[] { originalStack, null };
		when(inventory.getContents()).thenReturn(originalContents);

		ItemStack originalCursor = new ItemStack(Material.STONE, 1);
		when(player.getItemOnCursor()).thenReturn(originalCursor);

		PlayerInventorySnapshot snapshot = new PlayerInventorySnapshot();
		snapshot.capture(player);

		originalStack.setAmount(5);
		originalCursor.setAmount(4);

		snapshot.restore(player);

		ArgumentCaptor<ItemStack[]> contentsCaptor = ArgumentCaptor.forClass(ItemStack[].class);
		verify(inventory).setContents(contentsCaptor.capture());
		ItemStack[] restoredContents = contentsCaptor.getValue();

		assertEquals(1, restoredContents[0].getAmount());
		assertNotSame(originalStack, restoredContents[0]);

		ArgumentCaptor<ItemStack> cursorCaptor = ArgumentCaptor.forClass(ItemStack.class);
		verify(player).setItemOnCursor(cursorCaptor.capture());
		assertEquals(1, cursorCaptor.getValue().getAmount());
		assertNotSame(originalCursor, cursorCaptor.getValue());
		verify(player).updateInventory();
	}

	@Test
	void restore_withoutCapture_doesNothing() {
		Player player = mock(Player.class);

		PlayerInventorySnapshot snapshot = new PlayerInventorySnapshot();
		snapshot.restore(player);

		verify(player, never()).getInventory();
		verify(player, never()).setItemOnCursor(any());
		verify(player, never()).updateInventory();
	}

	@Test
	void restore_onlyAppliesOncePerCapture() {
		Player player = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getContents()).thenReturn(new ItemStack[0]);
		when(player.getItemOnCursor()).thenReturn(null);

		PlayerInventorySnapshot snapshot = new PlayerInventorySnapshot();
		snapshot.capture(player);

		snapshot.restore(player);
		snapshot.restore(player);

		verify(inventory, times(1)).setContents(any());
		verify(player, times(1)).setItemOnCursor(isNull());
		verify(player, times(1)).updateInventory();
	}
}
