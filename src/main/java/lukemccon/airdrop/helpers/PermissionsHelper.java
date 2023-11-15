package lukemccon.airdrop.helpers;

import org.bukkit.entity.Player;

public class PermissionsHelper {

    public static Boolean isAdmin(Player player) {
        return player.hasPermission("airdrop.admin") || player.isOp();
    }
}
