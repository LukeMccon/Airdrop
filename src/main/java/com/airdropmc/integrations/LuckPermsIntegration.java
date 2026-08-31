package com.airdropmc.integrations;

import com.airdropmc.helpers.AirdropLogger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.node.Node;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * The only class allowed to link LuckPerms API types.
 */
final class LuckPermsIntegration {

	private static final String AIRDROP_GROUP_ADMIN = "airdrop-admin";
	private static final String AIRDROP_GROUP_USER = "airdrop-user";
	private static final String AIRDROP_ADMIN = "airdrop.admin";
	private static final String AIRDROP_PACKAGES_ALL = "airdrop.package.all";

	private final LuckPerms luckPerms;

	LuckPermsIntegration(Object provider) {
		this.luckPerms = (LuckPerms) Objects.requireNonNull(provider, "provider");
	}

	void initialize() {
		GroupManager manager = luckPerms.getGroupManager();
		Node adminNode = luckPerms.getNodeBuilderRegistry().forKey(AIRDROP_ADMIN).build();
		Node userNode = luckPerms.getNodeBuilderRegistry().forKey(AIRDROP_PACKAGES_ALL).build();
		ensureGroupHasNode(manager, AIRDROP_GROUP_ADMIN, adminNode);
		ensureGroupHasNode(manager, AIRDROP_GROUP_USER, userNode);
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
		}).exceptionally(failure -> {
			AirdropLogger.log(Level.WARNING,
					"Failed to create LuckPerms group '" + groupName + "'",
					failure);
			return null;
		});
	}

	private static void saveGroupIfNodeAdded(GroupManager manager, Group group, Node node) {
		if (!group.data().add(node).wasSuccessful()) {
			return;
		}

		manager.saveGroup(group).exceptionally(failure -> {
			AirdropLogger.log(Level.WARNING,
					"Failed to save LuckPerms group '" + group.getName() + "'",
					failure);
			return null;
		});
	}
}
