package com.airdropmc.packages;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Objects;
import java.util.UUID;

final class PackageEditorSession {
	enum State {
		NEW,
		ACTIVE,
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
		return true;
	}

	boolean protects(HumanEntity actor, Inventory topInventory) {
		return viewerId != null
				&& viewerId.equals(actor.getUniqueId())
				&& topInventory == inventory
				&& (state == State.ACTIVE || state == State.TRANSITIONING);
	}

	boolean canProcess(HumanEntity actor, Inventory topInventory) {
		return protects(actor, topInventory) && state == State.ACTIVE;
	}

	boolean beginTransition() {
		if (state != State.ACTIVE) {
			return false;
		}

		state = State.TRANSITIONING;
		return true;
	}

	boolean retire() {
		if (state == State.CLOSED) {
			return false;
		}

		state = State.CLOSED;
		return true;
	}

	State state() {
		return state;
	}

	UUID viewerId() {
		return viewerId;
	}
}
