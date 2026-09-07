package com.airdropmc.packages;

import com.airdropmc.Airdrop;
import com.airdropmc.helpers.CrateManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerCatalogLifecycleTest {
	private ServerMock server;
	private Airdrop plugin;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock(new ServerMock() {
			@Override
			public boolean isStopping() {
				return false;
			}
		});
		plugin = (Airdrop) server.getPluginManager().loadPlugin(Airdrop.class, new Object[0]);
		Files.createDirectories(plugin.getDataFolder().toPath());
		Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"),
				"language: en\neconomy:\n  enabled: false\n");
		server.getPluginManager().enablePlugin(plugin);
		await(Airdrop::isReady);
	}

	@AfterEach
	void tearDown() {
		CrateManager.clearAll();
		MockBukkit.unmock();
		PackageManager.clear();
	}

	@Test
	void successfulReloadClosesReadersAndInvalidatesQueuedCatalogNavigation() {
		PlayerMock reader = reader();
		Inventory preview = openPackage(reader);
		PlayerMock browsing = reader();
		assertTrue(server.dispatchCommand(browsing, "airdrop packages"));
		Inventory browser = top(browsing);
		click(browsing, 0);

		var reload = plugin.reloadConfiguration().toCompletableFuture();
		await(reload::isDone);
		reload.join();
		server.getScheduler().performTicks(2);

		assertNotSame(preview, top(reader));
		assertNotSame(browser, top(browsing));
		assertNotEquals(InventoryType.CHEST, reader.getOpenInventory().getType());
		assertNotEquals(InventoryType.CHEST, browsing.getOpenInventory().getType());
		assertEquals(new ItemStack(Material.EMERALD, 3), reader.getInventory().getItem(0));
	}

	@Test
	void failedReloadRetainsTheProtectedPreviewAndLiveDefinition() throws Exception {
		PlayerMock reader = reader();
		Inventory preview = openPackage(reader);
		Package original = PackageManager.get("starter");
		ItemStack reward = preview.getItem(0).clone();
		Files.writeString(plugin.getDataFolder().toPath().resolve("packages.yml"), "packages: [broken\n");

		var reload = plugin.reloadConfiguration().toCompletableFuture();
		await(reload::isDone);
		assertThrows(CompletionException.class, reload::join);
		server.getScheduler().performOneTick();

		assertSame(original, PackageManager.get("starter"));
		assertSame(preview, top(reader));
		assertTrue(click(reader, 0).isCancelled());
		assertEquals(reward, preview.getItem(0));
	}

	@Test
	void actualAdminSaveClosesStaleReaderAndStillReportsSuccess() throws Exception {
		PlayerMock reader = reader();
		Inventory preview = openPackage(reader);
		PlayerMock admin = reader();
		admin.setOp(true);
		Inventory editor = openPackage(admin);
		for (int slot = 0; slot < PackageManager.MAX_PACKAGE_ITEM_STACKS; slot++) {
			editor.setItem(slot, null);
		}
		editor.setItem(0, new ItemStack(Material.GOLD_INGOT, 4));
		click(admin, 34);
		await(() -> admin.getOpenInventory().getType() != InventoryType.CHEST);
		server.getScheduler().performOneTick();

		assertEquals(List.of(new ItemStack(Material.GOLD_INGOT, 4)), PackageManager.get("starter").getItems());
		assertNotSame(preview, top(reader));
		StringBuilder messages = new StringBuilder();
		Component message;
		while ((message = admin.nextComponentMessage()) != null) {
			messages.append(PlainTextComponentSerializer.plainText().serialize(message));
		}
		assertTrue(messages.toString().contains("saved successfully"), messages.toString());
		assertEquals(new ItemStack(Material.EMERALD, 3), admin.getInventory().getItem(0));
	}

	@Test
	void deletingPackageClosesItsPreviewAndRefreshesOpenCatalog() {
		PlayerMock reader = reader();
		Inventory preview = openPackage(reader);
		PlayerMock browsing = reader();
		server.dispatchCommand(browsing, "airdrop packages");
		Inventory browser = top(browsing);

		var deletion = plugin.deletePackageAsync("starter").toCompletableFuture();
		await(deletion::isDone);
		assertTrue(deletion.join());
		server.getScheduler().performOneTick();

		assertNotSame(preview, top(reader));
		assertSame(browser, top(browsing));
		assertTrue(browser.getItem(13).getItemMeta().getDisplayName().contains("No packages"));
	}

	@Test
	void disableClosesBothViewsBeforeListenersAndTasksDisappear() {
		PlayerMock reader = reader();
		Inventory preview = openPackage(reader);
		PlayerMock browsing = reader();
		server.dispatchCommand(browsing, "airdrop packages");
		Inventory browser = top(browsing);

		server.getPluginManager().disablePlugin(plugin);
		server.getScheduler().performTicks(2);

		assertNotSame(preview, top(reader));
		assertNotSame(browser, top(browsing));
		assertTrue(server.getScheduler().getPendingTasks().stream().noneMatch(task -> task.getOwner() == plugin));
		assertEquals(new ItemStack(Material.EMERALD, 3), reader.getInventory().getItem(0));
		assertEquals(new ItemStack(Material.EMERALD, 3), browsing.getInventory().getItem(0));
	}

	private PlayerMock reader() {
		PlayerMock player = server.addPlayer();
		player.addAttachment(plugin, "airdrop.package.starter", true);
		player.getInventory().setItem(0, new ItemStack(Material.EMERALD, 3));
		return player;
	}

	private Inventory openPackage(PlayerMock player) {
		assertTrue(server.dispatchCommand(player, "airdrop packages"));
		Inventory browser = top(player);
		assertTrue(click(player, 0).isCancelled());
		server.getScheduler().performOneTick();
		assertNotSame(browser, top(player));
		return top(player);
	}

	private InventoryClickEvent click(PlayerMock player, int slot) {
		InventoryClickEvent event = new InventoryClickEvent(player.getOpenInventory(),
				InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
		server.getPluginManager().callEvent(event);
		return event;
	}

	private static Inventory top(PlayerMock player) {
		return player.getOpenInventory().getTopInventory();
	}

	private void await(BooleanSupplier condition) {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
			server.getScheduler().performOneTick();
			LockSupport.parkNanos(Duration.ofMillis(1).toNanos());
		}
		assertTrue(condition.getAsBoolean(), "Timed out waiting for catalog lifecycle operation");
	}
}
