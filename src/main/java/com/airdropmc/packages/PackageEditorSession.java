package com.airdropmc.packages;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class PackageEditorSession {
	private static final Set<PackageEditorSession> OPEN_SESSIONS =
			Collections.newSetFromMap(new IdentityHashMap<>());
	enum State {
		NEW,
		ACTIVE,
		SAVING,
		TRANSITIONING,
		CLOSED
	}

	private final Inventory inventory;
	private UUID viewerId;
	private State state = State.NEW;

	PackageEditorSession(Inventory inventory) {
		this.inventory = Objects.requireNonNull(inventory);
	}

	boolean bind(Player player) {
		if (state != State.NEW || viewerId != null) {
			return false;
		}

		viewerId = player.getUniqueId();
		return true;
	}

	boolean activate(Inventory openedInventory) {
		if (state != State.NEW || viewerId == null || openedInventory != inventory) {
			return false;
		}

		state = State.ACTIVE;
		synchronized (OPEN_SESSIONS) {
			OPEN_SESSIONS.add(this);
		}
		return true;
	}

	boolean protects(HumanEntity actor, Inventory topInventory) {
		return viewerId != null
				&& viewerId.equals(actor.getUniqueId())
				&& topInventory == inventory
				&& (state == State.ACTIVE || state == State.SAVING || state == State.TRANSITIONING);
	}

	boolean canProcess(HumanEntity actor, Inventory topInventory) {
		return protects(actor, topInventory) && state == State.ACTIVE;
	}

	boolean beginSave() {
		if (state != State.ACTIVE) {
			return false;
		}

		state = State.SAVING;
		return true;
	}

	boolean failSave() {
		if (state != State.SAVING) {
			return false;
		}

		state = State.ACTIVE;
		return true;
	}

	boolean completeSave() {
		if (state != State.SAVING) {
			return false;
		}

		state = State.TRANSITIONING;
		return true;
	}

	boolean beginTransition() {
		if (state != State.ACTIVE) {
			return false;
		}

		state = State.TRANSITIONING;
		return true;
	}

	boolean beginExitTransition() {
		if (state == State.CLOSED) {
			return false;
		}
		if (state == State.ACTIVE || state == State.SAVING) {
			state = State.TRANSITIONING;
		}
		return true;
	}

	boolean retire() {
		if (state == State.CLOSED) {
			return false;
		}

		state = State.CLOSED;
		synchronized (OPEN_SESSIONS) {
			OPEN_SESSIONS.remove(this);
		}
		return true;
	}

	static void closeOpenEditors() {
		List<PackageEditorSession> sessions;
		synchronized (OPEN_SESSIONS) {
			sessions = new ArrayList<>(OPEN_SESSIONS);
			OPEN_SESSIONS.clear();
			for (PackageEditorSession session : sessions) {
				session.state = State.CLOSED;
			}
		}

		for (PackageEditorSession session : sessions) {
			for (HumanEntity viewer : List.copyOf(session.inventory.getViewers())) {
				if (viewer.getOpenInventory().getTopInventory() == session.inventory) {
					viewer.closeInventory();
				}
			}
		}
	}

	State state() {
		return state;
	}

	UUID viewerId() {
		return viewerId;
	}
}
