package com.airdropmc.packages;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackageEditorSessionTest {

	@Test
	void bindsOneViewerAndExactInventoryOnce() {
		Inventory editor = mock(Inventory.class);
		Player owner = player(UUID.randomUUID());
		Player other = player(UUID.randomUUID());
		PackageEditorSession session = new PackageEditorSession(editor);

		assertNull(session.viewerId());
		assertTrue(session.bind(owner));
		assertTrue(session.activate(editor));
		assertTrue(session.protects(owner, editor));
		assertTrue(session.canProcess(owner, editor));
		assertFalse(session.bind(other));
		assertFalse(session.activate(editor));
		assertFalse(session.protects(other, editor));
		assertFalse(session.protects(owner, mock(Inventory.class)));
	}

	@Test
	void activationRequiresABoundViewerAndExactInventory() {
		Inventory editor = mock(Inventory.class);
		PackageEditorSession session = new PackageEditorSession(editor);

		assertFalse(session.activate(editor));
		assertTrue(session.bind(player(UUID.randomUUID())));
		assertFalse(session.activate(mock(Inventory.class)));
		assertEquals(PackageEditorSession.State.NEW, session.state());
	}

	@Test
	void transitioningRemainsProtectedButCannotProcess() {
		Inventory editor = mock(Inventory.class);
		Player owner = player(UUID.randomUUID());
		PackageEditorSession session = activeSession(editor, owner);

		assertTrue(session.beginTransition());
		assertTrue(session.protects(owner, editor));
		assertFalse(session.canProcess(owner, editor));
		assertFalse(session.beginTransition());
	}

	@Test
	void retirementIsTerminalAndIdempotent() {
		Inventory editor = mock(Inventory.class);
		Player owner = player(UUID.randomUUID());
		PackageEditorSession session = activeSession(editor, owner);

		assertTrue(session.retire());
		assertFalse(session.retire());
		assertEquals(PackageEditorSession.State.CLOSED, session.state());
		assertFalse(session.protects(owner, editor));
		assertFalse(session.canProcess(owner, editor));
		assertFalse(session.bind(player(UUID.randomUUID())));
	}

	private static PackageEditorSession activeSession(Inventory inventory, Player player) {
		PackageEditorSession session = new PackageEditorSession(inventory);
		assertTrue(session.bind(player));
		assertTrue(session.activate(inventory));
		return session;
	}

	private static Player player(UUID id) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		return player;
	}
}
