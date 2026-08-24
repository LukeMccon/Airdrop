package com.airdropmc.economy;

import java.util.Objects;
import java.util.UUID;

public record EconomyPlayer(UUID uniqueId, String lastKnownName) {

	public EconomyPlayer {
		Objects.requireNonNull(uniqueId, "uniqueId");
		lastKnownName = lastKnownName == null ? "" : lastKnownName;
	}
}
