package com.airdropmc.helpers;

import com.airdropmc.Airdrop;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.ServerOperator;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class PermissionsHelper {

    PermissionsHelper() {

    }

    private static final String AIRDROP_GROUP_ADMIN = "airdrop-admin";
    private static final String AIRDROP_GROUP_USER = "airdrop-user";
    private static final String AIRDROP_ADMIN = "airdrop.admin";
    private static final String AIRDROP_PACKAGES_ALL = "airdrop.package.all";
    private static final String AIRDROP_PACKAGE = "airdrop.package";

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

        if (isAdmin(player)) {
            return true;
        }
        if (packageName == null || packageName.isBlank()) {
            return false;
        }

        String normalizedNode = AIRDROP_PACKAGE + "." + packageName.toLowerCase(Locale.ROOT);
        String exactNode = AIRDROP_PACKAGE + "." + packageName;

        return player.hasPermission(normalizedNode)
                || (!exactNode.equals(normalizedNode) && player.hasPermission(exactNode))
                || player.hasPermission(PermissionsHelper.AIRDROP_PACKAGES_ALL);
    }

    public static void initialize() {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            Airdrop.setLuckPerms(provider.getProvider());

            GroupManager manager = Airdrop.getLuckPerms().getGroupManager();
            ensureGroupHasNode(manager, AIRDROP_GROUP_ADMIN, Node.builder(AIRDROP_ADMIN).build());
            ensureGroupHasNode(manager, AIRDROP_GROUP_USER, Node.builder(AIRDROP_PACKAGES_ALL).build());

        }
    }

    private static void ensureGroupHasNode(GroupManager manager, String groupName, Node node) {
        Group existingGroup = manager.getGroup(groupName);
        if (existingGroup != null) {
            saveGroupIfNodeAdded(manager, existingGroup, node);
            return;
        }

        CompletableFuture<Group> createGroupFuture = manager.createAndLoadGroup(groupName);
        createGroupFuture.thenAccept(group -> {
            if (group == null) {
                AirdropLogger.warning("LuckPerms returned null when creating group '" + groupName + "'");
                return;
            }
            saveGroupIfNodeAdded(manager, group, node);
        }).exceptionally(throwable -> {
            AirdropLogger.log(Level.WARNING,
                    "Failed to create LuckPerms group '" + groupName + "'",
                    throwable);
            return null;
        });
    }

    private static void saveGroupIfNodeAdded(GroupManager manager, Group group, Node node) {
        if (!group.data().add(node).wasSuccessful()) {
            return;
        }

        manager.saveGroup(group).exceptionally(throwable -> {
            AirdropLogger.log(Level.WARNING,
                    "Failed to save LuckPerms group '" + group.getName() + "'",
                    throwable);
            return null;
        });
    }
}
