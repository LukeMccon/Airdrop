package com.airdropmc.packages;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.airdropmc.packages.PackageEditorInteraction.VirtualAction.CONTROL;
import static com.airdropmc.packages.PackageEditorInteraction.VirtualAction.DENY;
import static com.airdropmc.packages.PackageEditorInteraction.VirtualAction.FULL_STACK;
import static com.airdropmc.packages.PackageEditorInteraction.VirtualAction.SINGLE_ITEM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackageEditorInteractionTest {

	@Test
	void allowsOnlyExpectedEmptyCursorPickups() {
		assertEquals(FULL_STACK, classify(ClickType.LEFT, InventoryAction.PICKUP_ALL, null, false));
		assertEquals(FULL_STACK, classify(ClickType.LEFT, InventoryAction.PICKUP_ALL, air(), false));
		assertEquals(SINGLE_ITEM, classify(ClickType.RIGHT, InventoryAction.PICKUP_HALF, null, false));
		assertEquals(SINGLE_ITEM, classify(ClickType.RIGHT, InventoryAction.PICKUP_HALF, air(), false));
		assertEquals(CONTROL, classify(ClickType.LEFT, InventoryAction.PICKUP_ALL, null, true));
		assertEquals(DENY, classify(ClickType.RIGHT, InventoryAction.PICKUP_HALF, null, true));
	}

	@ParameterizedTest
	@MethodSource("deniedInteractions")
	void deniesUnsupportedInteractions(ClickType click, InventoryAction action, ItemStack cursor) {
		assertEquals(DENY, classify(click, action, cursor, false));
	}

	private static Stream<Arguments> deniedInteractions() {
		return Stream.of(
				arguments(ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY, null),
				arguments(ClickType.SHIFT_RIGHT, InventoryAction.MOVE_TO_OTHER_INVENTORY, null),
				arguments(ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, null),
				arguments(ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP, null),
				arguments(ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR, null),
				arguments(ClickType.DROP, InventoryAction.DROP_ONE_SLOT, null),
				arguments(ClickType.CONTROL_DROP, InventoryAction.DROP_ALL_SLOT, null),
				arguments(ClickType.MIDDLE, InventoryAction.CLONE_STACK, null),
				arguments(ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR, item(Material.STONE)),
				arguments(ClickType.LEFT, InventoryAction.PICKUP_ALL, item(Material.STONE)),
				arguments(ClickType.RIGHT, InventoryAction.PICKUP_HALF, item(Material.STONE)),
				arguments(ClickType.UNKNOWN, InventoryAction.UNKNOWN, null),
				arguments(ClickType.LEFT, InventoryAction.PICKUP_ONE, null),
				arguments(ClickType.RIGHT, InventoryAction.PICKUP_ONE, null));
	}

	private static PackageEditorInteraction.VirtualAction classify(
			ClickType click,
			InventoryAction action,
			ItemStack cursor,
			boolean controlSlot) {
		return PackageEditorInteraction.classify(click, action, cursor, controlSlot);
	}

	private static ItemStack air() {
		return item(Material.AIR);
	}

	private static ItemStack item(Material material) {
		ItemStack item = mock(ItemStack.class);
		when(item.getType()).thenReturn(material);
		return item;
	}
}
