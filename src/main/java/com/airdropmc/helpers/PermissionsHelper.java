package com.airdropmc.helpers;

import com.airdropmc.packages.PackageNamePolicy;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.ServerOperator;

public class PermissionsHelper {

    PermissionsHelper() {

    }

    private static final String AIRDROP_ADMIN = "airdrop.admin";
    private static final String AIRDROP_PACKAGES_ALL = "airdrop.package.all";
	private static final String AIRDROP_COOLDOWN_BYPASS = "airdrop.cooldown.bypass";

    /**
     * Determines if the player is a superuser in the context of airdrop
     * 
     * @param player to check for superuser perms
     * @return is player a superuser
     */
    public static boolean isAdmin(Player player) {
        return player.hasPermission(PermissionsHelper.AIRDROP_ADMIN) || player.isOp();
    }

    /**
     * Determines if the sender is an admin
     * 
     * @param sender either a player or the server console
     * @return is the sender a superuser
     */
    public static boolean isAdmin(CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) {
            return true;
        }
        if (sender instanceof Player player) {
            return isAdmin(player);
        }
        if (sender.hasPermission(AIRDROP_ADMIN)) {
            return true;
        }
        if (sender instanceof ServerOperator operator) {
            return operator.isOp();
        }
        return false;
    }

    /**
     * Checks if player has permission to drop a package
     * 
     * @param player      to check permission
     * @param packageName to check permissions for
     * @return player has permissions
     */
    public static boolean hasPermission(Player player, String packageName) {
		PackageNamePolicy.Result validation = PackageNamePolicy.validate(packageName);
		if (!validation.accepted()) {
			return false;
		}

        if (isAdmin(player)) {
            return true;
        }

		String packageNode = PackageNamePolicy.permissionNode(packageName);
		return player.hasPermission(packageNode)
                || player.hasPermission(PermissionsHelper.AIRDROP_PACKAGES_ALL);
    }

	public static boolean hasCooldownBypass(Player player) {
		return player.hasPermission(AIRDROP_COOLDOWN_BYPASS);
	}
}
