package com.airdropmc.integrations;

import com.airdropmc.Airdrop;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeBuilder;
import net.luckperms.api.node.NodeBuilderRegistry;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LuckPermsIntegrationTest {

	@AfterEach
	void tearDown() {
		if (MockBukkit.isMocked()) {
			MockBukkit.unmock();
		}
	}

	@Test
	void missingPluginServiceAndLinkageFailuresAreDegradedResults() throws Exception {
		Server noPlugin = mock(Server.class);
		PluginManager noPluginManager = mock(PluginManager.class);
		when(noPlugin.getPluginManager()).thenReturn(noPluginManager);
		assertEquals("NOT_INSTALLED", initializeOptional(noPlugin));
		assertEquals("NOT_INSTALLED", initializeOptional(noPlugin));

		Server noService = mock(Server.class);
		PluginManager presentPluginManager = mock(PluginManager.class);
		Plugin plugin = mock(Plugin.class);
		ServicesManager emptyServices = mock(ServicesManager.class);
		when(noService.getPluginManager()).thenReturn(presentPluginManager);
		when(presentPluginManager.getPlugin("LuckPerms")).thenReturn(plugin);
		when(plugin.isEnabled()).thenReturn(true);
		when(noService.getServicesManager()).thenReturn(emptyServices);
		when(emptyServices.getRegistrations(plugin)).thenReturn(List.of());
		assertEquals("SERVICE_UNAVAILABLE", initializeOptional(noService));

		Server linkageFailure = mock(Server.class);
		PluginManager brokenPluginManager = mock(PluginManager.class);
		when(linkageFailure.getPluginManager()).thenReturn(brokenPluginManager);
		when(brokenPluginManager.getPlugin("LuckPerms"))
				.thenThrow(new NoClassDefFoundError("simulated optional dependency linkage failure"));
		assertDoesNotThrow(() -> assertEquals("FAILED", initializeOptional(linkageFailure)));

		Server runtimeFailure = mock(Server.class);
		PluginManager failingPluginManager = mock(PluginManager.class);
		when(runtimeFailure.getPluginManager()).thenReturn(failingPluginManager);
		when(failingPluginManager.getPlugin("LuckPerms"))
				.thenThrow(new IllegalStateException("simulated optional dependency runtime failure"));
		assertDoesNotThrow(() -> assertEquals("FAILED", initializeOptional(runtimeFailure)));
	}

	@Test
	void installedServiceRetainsConvenienceGroupNodesWithoutBlockingReadiness() throws Exception {
		ServerMock server = MockBukkit.mock();
		PluginMock luckPermsPlugin = MockBukkit.createMockPlugin("LuckPerms");
		LuckPerms luckPerms = mock(LuckPerms.class);
		GroupManager groups = mock(GroupManager.class);
		NodeBuilderRegistry nodeBuilders = mock(NodeBuilderRegistry.class);
		@SuppressWarnings("rawtypes") NodeBuilder adminNodeBuilder = mock(NodeBuilder.class);
		@SuppressWarnings("rawtypes") NodeBuilder userNodeBuilder = mock(NodeBuilder.class);
		PermissionNode adminNode = node("airdrop.admin");
		PermissionNode userNode = node("airdrop.package.all");
		Group adminGroup = group("airdrop-admin");
		Group userGroup = group("airdrop-user");
		when(luckPerms.getGroupManager()).thenReturn(groups);
		when(luckPerms.getNodeBuilderRegistry()).thenReturn(nodeBuilders);
		doReturn(adminNodeBuilder).when(nodeBuilders).forKey("airdrop.admin");
		doReturn(userNodeBuilder).when(nodeBuilders).forKey("airdrop.package.all");
		doReturn(adminNode).when(adminNodeBuilder).build();
		doReturn(userNode).when(userNodeBuilder).build();
		when(groups.getGroup("airdrop-admin")).thenReturn(adminGroup);
		when(groups.getGroup("airdrop-user")).thenReturn(userGroup);
		when(groups.saveGroup(adminGroup)).thenReturn(CompletableFuture.completedFuture(null));
		when(groups.saveGroup(userGroup)).thenReturn(CompletableFuture.completedFuture(null));
		server.getServicesManager().register(
				LuckPerms.class, luckPerms, luckPermsPlugin, ServicePriority.Normal);

		Airdrop plugin = loadPlugin(server);

		assertTrue(plugin.isEnabled());
		assertTrue(Airdrop.isReady());
		verify(adminGroup.data()).add(argThat(node -> node.getKey().equals("airdrop.admin")));
		verify(userGroup.data()).add(argThat(node -> node.getKey().equals("airdrop.package.all")));
		verify(groups).saveGroup(adminGroup);
		verify(groups).saveGroup(userGroup);
	}

	private static PermissionNode node(String key) {
		PermissionNode node = mock(PermissionNode.class);
		when(node.getKey()).thenReturn(key);
		return node;
	}

	private static Group group(String name) {
		Group group = mock(Group.class);
		NodeMap data = mock(NodeMap.class);
		when(group.getName()).thenReturn(name);
		when(group.data()).thenReturn(data);
		when(data.add(org.mockito.ArgumentMatchers.any())).thenReturn(DataMutateResult.SUCCESS);
		return group;
	}

	private static Airdrop loadPlugin(ServerMock server) throws Exception {
		Airdrop plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(plugin.getDataFolder().toPath());
		Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"),
				"language: en\neconomy:\n  enabled: false\n");
		Files.writeString(plugin.getDataFolder().toPath().resolve("packages.yml"), "packages: {}\n");
		server.getPluginManager().enablePlugin(plugin);

		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (System.nanoTime() < deadline && plugin.isEnabled() && !Airdrop.isReady()) {
			server.getScheduler().performOneTick();
			LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
		}
		assertTrue(Airdrop.isReady(), "Timed out waiting for asynchronous startup");
		return plugin;
	}

	private static String initializeOptional(Server server) throws Exception {
		Class<?> type = Class.forName("com.airdropmc.integrations.OptionalIntegrations");
		Method initialize = type.getMethod("initialize", Server.class);
		try {
			return String.valueOf(initialize.invoke(null, server));
		} catch (InvocationTargetException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof Exception checked) {
				throw checked;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw exception;
		}
	}
}
