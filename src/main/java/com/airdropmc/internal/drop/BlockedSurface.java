package com.airdropmc.internal.drop;

import org.bukkit.Material;
import org.jetbrains.annotations.ApiStatus;

/** Snapshot of the highest surface that caused a clear-sky rejection. */
@ApiStatus.Internal
public record BlockedSurface(Material material, int y) {
}
