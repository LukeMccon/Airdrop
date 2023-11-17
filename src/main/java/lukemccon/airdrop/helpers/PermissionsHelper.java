package lukemccon.airdrop.helpers;

import org.bukkit.entity.Player;

public class PermissionsHelper {


    private static final String AIRDROP_ADMIN = "airdrop.admin";
    private static final String AIRDROP_PACKAGES_ALL = "airdrop.package.all";
    private static final String AIRDROP_PACKAGE = "airdrop.package";


    /**
     * Determines if the player is a superuser in the context of airdrop
     * @param player to check for superuser perms
     * @return is player a superuser
     */
    public static boolean isAdmin(Player player) {
        return player.hasPermission(PermissionsHelper.AIRDROP_ADMIN) || player.isOp();
    }

    /**
     * Checks if player has permission to drop a package
     * @param player to check permission
     * @param packageName to check permissions for
     * @return player has permissions
     */
    public static boolean hasPermission(Player player , String packageName) {

        if (isAdmin(player)) {
            return true;
        }
        return player.hasPermission(AIRDROP_PACKAGE + "."+ packageName.toLowerCase()) ||
                player.hasPermission(PermissionsHelper.AIRDROP_PACKAGES_ALL);
    }
}
