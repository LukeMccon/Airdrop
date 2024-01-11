package com.airdropmc.helpers;

import com.airdropmc.Airdrop;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class PermissionsHelper {

    PermissionsHelper(){

    }

    private static final String AIRDROP_GROUP_ADMIN = "airdrop-admin";
    private static final String AIRDROP_GROUP_USER = "airdrop-user";
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


    public static void initialize() {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            Airdrop.setLuckPerms(provider.getProvider());

            GroupManager manager = Airdrop.getLuckPerms().getGroupManager();
            Group adminGroup = manager.getGroup(AIRDROP_GROUP_ADMIN);
            Group userGroup = manager.getGroup(AIRDROP_GROUP_USER);
            Node allPackagesNode = Node.builder(AIRDROP_PACKAGES_ALL).build();
            Node adminNode = Node.builder(AIRDROP_ADMIN).build();

            if (adminGroup == null) {
                // group doesn't exist.
                adminGroup = manager.createAndLoadGroup(AIRDROP_GROUP_ADMIN).join();
                adminGroup.data().add(adminNode);
            }

            if (userGroup == null) {
                userGroup = manager.createAndLoadGroup(AIRDROP_GROUP_USER).join();
                userGroup.data().add(allPackagesNode);
            }

        }
    }
}
