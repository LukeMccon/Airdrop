package com.airdropmc.internal.drop;

import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.packages.Package;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;

/** Internal service seam for plugin requests and already-resolved package adapters. */
@ApiStatus.Internal
public interface InternalDropRequests {

	DropHandle requestSendDrop(CommandSender sender, Player recipient, String packageName,
			DropRequestOptions options, boolean requireRecipientPermission);

	DropHandle requestSendDrop(CommandSender sender, Location location, String packageName,
			DropRequestOptions options);

	DropHandle requestPlayerDrop(Player player, Package pkg, DropRequestOptions options);

	DropHandle requestSystemDrop(Location location, Package pkg, DropRequestOptions options);
}
