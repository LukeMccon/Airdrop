package com.airdropmc.packages;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.MockPlugin;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import com.airdropmc.Airdrop;
import com.airdropmc.controllers.PackageController;
import com.airdropmc.exceptions.PackageNotFoundException;
import com.airdropmc.helpers.ChatHandler;
import com.airdropmc.lang.LanguageManager;
import com.airdropmc.lang.MessageKey;
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
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackagePersistenceFailureFeedbackTest {

	private ServerMock server;
	private Airdrop plugin;

	@BeforeEach
	void setUp() throws Exception {
		server = MockBukkit.mock();
		MockPlugin eventPlugin = MockBukkit.createMockPlugin("AirdropPersistenceHarness");
		plugin = mock(Airdrop.class);
		when(plugin.isEnabled()).thenReturn(true);
		when(plugin.getPluginLoader()).thenReturn(eventPlugin.getPluginLoader());
		when(plugin.getName()).thenReturn("Airdrop");
		when(plugin.getServer()).thenReturn(server);
		when(plugin.updatePackageInventoryAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(false));
		when(plugin.createPackageAsync(any())).thenReturn(CompletableFuture.completedFuture(false));
		when(plugin.deletePackageAsync(any())).thenReturn(CompletableFuture.completedFuture(false));
		setAirdropStaticField("pluginInstance", plugin);
		setAirdropStaticField("ready", true);
		PackageManager.publishPackages(Map.of(
				"starter", new Package("starter", 10.0, List.of(new ItemStack(Material.STONE, 2)))));
	}

	@AfterEach
	void tearDown() throws Exception {
		PackageGui.closeOpenEditors();
		PackageManager.clear();
		clearAirdropStaticFields();
		MockBukkit.unmock();
	}

	@Test
	void existingPackageSaveFailureKeepsEditorOpenAndSuccessfulRetryClosesIt() throws Exception {
		PlayerMock player = operator();
		PackageGui gui = new PackageGui(PackageManager.get("starter"));
		gui.openInventory(player);
		Inventory editor = player.getOpenInventory().getTopInventory();
		editor.setItem(0, new ItemStack(Material.DIRT, 3));
		player.getInventory().setItem(0, new ItemStack(Material.GOLD_INGOT, 4));
		InventoryClickEvent saveEvent = saveClick(player);

		gui.save(saveEvent);

		assertSame(editor, player.getOpenInventory().getTopInventory());
		assertFailureWithoutSuccess(player, "saved successfully");
		ItemStack changedPlayerItem = player.getInventory().getItem(0);
		assertNotNull(changedPlayerItem);
		assertEquals(Material.GOLD_INGOT, changedPlayerItem.getType());
		assertEquals(4, changedPlayerItem.getAmount());
		List<ItemStack> persistedItems = PackageManager.get("starter").getItems();
		assertEquals(1, persistedItems.size());
		assertEquals(Material.STONE, persistedItems.get(0).getType());
		assertEquals(2, persistedItems.get(0).getAmount());
		assertTrue(persistedItems.stream().noneMatch(item -> item.getType() == Material.DIRT));

		when(plugin.updatePackageInventoryAsync(any(), any())).thenAnswer(invocation -> {
			List<ItemStack> items = invocation.getArgument(1);
			PackageManager.publishPackages(Map.of("starter", new Package("starter", 10.0, items)));
			return CompletableFuture.completedFuture(true);
		});
		gui.save(saveEvent);

		assertSame(editor, player.getOpenInventory().getTopInventory());
		server.getScheduler().performOneTick();

		assertNotEquals(InventoryType.CHEST, player.getOpenInventory().getType());
		assertEquals(new ItemStack(Material.GOLD_INGOT, 4), player.getInventory().getItem(0));
		assertNextMessageContains(player, "saved successfully");
	}

	@Test
	void createPackageSaveFailureKeepsEditorOpenAndReportsNoChanges() {
		PlayerMock player = operator();
		CreatePackageGui gui = new CreatePackageGui("newpkg", 3.0);
		gui.openInventory(player);
		Inventory editor = player.getOpenInventory().getTopInventory();

		gui.save(saveClick(player));

		assertSame(editor, player.getOpenInventory().getTopInventory());
		assertFailureWithoutSuccess(player, "created successfully");
		assertThrows(PackageNotFoundException.class, () -> PackageManager.get("newpkg"));
	}

	@Test
	@SuppressWarnings({"rawtypes", "unchecked"})
	void pendingExistingSaveProtectsEditorClonesPayloadAndReportsOnlyAfterCommit() throws Exception {
		CompletableFuture<Boolean> commit = new CompletableFuture<>();
		when(plugin.updatePackageInventoryAsync(any(), any())).thenReturn(commit);
		PlayerMock player = operator();
		PackageGui gui = new PackageGui(PackageManager.get("starter"));
		assertTrue(gui.openInventory(player));
		Inventory editor = player.getOpenInventory().getTopInventory();
		editor.setItem(0, new ItemStack(Material.DIRT, 3));

		gui.save(saveClick(player));
		InventoryClickEvent repeatedSave = saveClick(player);
		gui.onInventoryClick(repeatedSave);

		assertTrue(repeatedSave.isCancelled());
		verify(plugin, times(1)).updatePackageInventoryAsync(eq("starter"), any());
		ArgumentCaptor<List<ItemStack>> payloadCaptor = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
		verify(plugin).updatePackageInventoryAsync(eq("starter"), payloadCaptor.capture());
		List<ItemStack> payload = payloadCaptor.getValue();
		assertEquals(new ItemStack(Material.DIRT, 3), payload.getFirst());
		assertNotSame(editor.getItem(0), payload.getFirst());
		editor.getItem(0).setAmount(9);
		assertEquals(3, payload.getFirst().getAmount());
		assertNull(player.nextComponentMessage());
		assertSame(editor, player.getOpenInventory().getTopInventory());

		commit.complete(true);

		assertSame(editor, player.getOpenInventory().getTopInventory());
		assertNextMessageContains(player, "saved successfully");
		server.getScheduler().performOneTick();
		assertNotEquals(InventoryType.CHEST, player.getOpenInventory().getType());
	}

	@Test
	void lateCreateCompletionAfterCloseIsInert() {
		CompletableFuture<Boolean> commit = new CompletableFuture<>();
		when(plugin.createPackageAsync(any())).thenReturn(commit);
		PlayerMock player = operator();
		CreatePackageGui gui = new CreatePackageGui("newpkg", 3.0);
		assertTrue(gui.openInventory(player));
		Inventory editor = player.getOpenInventory().getTopInventory();

		gui.save(saveClick(player));
		org.bukkit.event.inventory.InventoryCloseEvent close = mock(
				org.bukkit.event.inventory.InventoryCloseEvent.class);
		when(close.getPlayer()).thenReturn(player);
		when(close.getInventory()).thenReturn(editor);
		gui.onInventoryClose(close);
		commit.complete(true);

		assertNull(player.nextComponentMessage());
		assertSame(editor, player.getOpenInventory().getTopInventory());
	}

	@Test
	void lateCreateCompletionAfterQuitIsInert() {
		CompletableFuture<Boolean> commit = new CompletableFuture<>();
		when(plugin.createPackageAsync(any())).thenReturn(commit);
		PlayerMock player = operator();
		CreatePackageGui gui = new CreatePackageGui("newpkg", 3.0);
		assertTrue(gui.openInventory(player));
		Inventory editor = player.getOpenInventory().getTopInventory();

		gui.save(saveClick(player));
		org.bukkit.event.player.PlayerQuitEvent quit = mock(org.bukkit.event.player.PlayerQuitEvent.class);
		when(quit.getPlayer()).thenReturn(player);
		gui.onPlayerQuit(quit);
		commit.complete(true);

		assertNull(player.nextComponentMessage());
		assertSame(editor, player.getOpenInventory().getTopInventory());
	}

	@Test
	void lateUpdateCompletionAfterKickIsInertWhileEditorRemainsProtected() throws Exception {
		CompletableFuture<Boolean> commit = new CompletableFuture<>();
		when(plugin.updatePackageInventoryAsync(any(), any())).thenReturn(commit);
		PlayerMock player = operator();
		PackageGui gui = new PackageGui(PackageManager.get("starter"));
		assertTrue(gui.openInventory(player));
		Inventory editor = player.getOpenInventory().getTopInventory();

		gui.save(saveClick(player));
		org.bukkit.event.player.PlayerKickEvent kick = mock(org.bukkit.event.player.PlayerKickEvent.class);
		when(kick.getPlayer()).thenReturn(player);
		gui.onPlayerKick(kick);
		commit.complete(true);
		InventoryClickEvent afterKick = saveClick(player);
		gui.onInventoryClick(afterKick);

		assertTrue(afterKick.isCancelled());
		assertNull(player.nextComponentMessage());
		assertSame(editor, player.getOpenInventory().getTopInventory());
	}

	@Test
	void wrappedDomainFailuresKeepEditorsOpenWithSpecificFeedback() throws Exception {
		PlayerMock updatePlayer = operator();
		PackageGui updateGui = new PackageGui(PackageManager.get("starter"));
		assertTrue(updateGui.openInventory(updatePlayer));
		Inventory updateEditor = updatePlayer.getOpenInventory().getTopInventory();
		when(plugin.updatePackageInventoryAsync(any(), any())).thenReturn(
				CompletableFuture.failedFuture(
						new CompletionException(new PackageNotFoundException("starter"))));

		updateGui.save(saveClick(updatePlayer));

		assertSame(updateEditor, updatePlayer.getOpenInventory().getTopInventory());
		assertNextMessageContains(updatePlayer, "not found");

		PlayerMock createPlayer = operator();
		CreatePackageGui createGui = new CreatePackageGui("newpkg", 3.0);
		assertTrue(createGui.openInventory(createPlayer));
		Inventory createEditor = createPlayer.getOpenInventory().getTopInventory();
		when(plugin.createPackageAsync(any())).thenReturn(
				CompletableFuture.failedFuture(
						new CompletionException(new com.airdropmc.exceptions.DuplicatePackageException("newpkg"))));

		createGui.save(saveClick(createPlayer));

		assertSame(createEditor, createPlayer.getOpenInventory().getTopInventory());
		assertNextMessageContains(createPlayer, "already exists");
	}

	@Test
	void existingPackageSavePersistsOnlyEditableSlots() throws Exception {
		allowSuccessfulUpdate();
		PlayerMock player = operator();
		PackageGui gui = new PackageGui(PackageManager.get("starter"));
		assertTrue(gui.openInventory(player));
		Inventory editor = player.getOpenInventory().getTopInventory();
		fillEditableSlots(editor);
		editor.setItem(PackageManager.MAX_PACKAGE_ITEM_STACKS, new ItemStack(Material.DIAMOND));
		editor.setItem(editor.getSize() - 1, new ItemStack(Material.EMERALD));
		ItemStack firstEditorItem = editor.getItem(0);

		gui.save(saveClick(player));
		server.getScheduler().performOneTick();

		List<ItemStack> saved = PackageManager.get("starter").getItems();
		assertEquals(PackageManager.MAX_PACKAGE_ITEM_STACKS, saved.size());
		assertTrue(saved.stream().allMatch(item -> item.getType() == Material.DIRT));
		assertNotSame(firstEditorItem, saved.get(0));
	}

	@Test
	void createdPackageSavePersistsOnlyEditableSlots() throws Exception {
		allowSuccessfulCreate();
		PlayerMock player = operator();
		CreatePackageGui gui = new CreatePackageGui("newpkg", 3.0);
		assertTrue(gui.openInventory(player));
		Inventory editor = player.getOpenInventory().getTopInventory();
		fillEditableSlots(editor);
		editor.setItem(PackageManager.MAX_PACKAGE_ITEM_STACKS, new ItemStack(Material.DIAMOND));
		editor.setItem(editor.getSize() - 1, new ItemStack(Material.EMERALD));

		gui.save(saveClick(player));
		server.getScheduler().performOneTick();

		List<ItemStack> saved = PackageManager.get("newpkg").getItems();
		assertEquals(PackageManager.MAX_PACKAGE_ITEM_STACKS, saved.size());
		assertTrue(saved.stream().allMatch(item -> item.getType() == Material.DIRT));
	}

	@Test
	void deletePackageFailureReportsNoChangesAndKeepsPackageAvailable() throws Exception {
		PlayerMock player = operator();

		PackageController.deletePackageCommand(player, new String[]{"package", "delete", "starter"});

		assertFailureWithoutSuccess(player, "successfully deleted");
		assertDoesNotThrow(() -> PackageManager.get("starter"));
	}

	@Test
	void deleteFeedbackWaitsForAsyncCommitAndUnwrapsNotFoundFailure() {
		PlayerMock successPlayer = operator();
		CompletableFuture<Boolean> deletion = new CompletableFuture<>();
		when(plugin.deletePackageAsync("starter")).thenReturn(deletion);

		PackageController.deletePackageCommand(
				successPlayer, new String[]{"package", "delete", "starter"});

		assertNull(successPlayer.nextComponentMessage());
		deletion.complete(true);
		assertNextMessageContains(successPlayer, "successfully deleted");

		PlayerMock missingPlayer = operator();
		when(plugin.deletePackageAsync("missing")).thenReturn(CompletableFuture.failedFuture(
				new CompletionException(new PackageNotFoundException("missing"))));

		PackageController.deletePackageCommand(
				missingPlayer, new String[]{"package", "delete", "missing"});

		assertNextMessageContains(missingPlayer, "not found");
	}

	@Test
	void createAndDeleteCommandsAreGatedWhilePluginIsNotReady() throws Exception {
		setAirdropStaticField("ready", false);
		PlayerMock createPlayer = operator();

		PackageController.createPackageCommand(
				createPlayer, new String[]{"package", "create", "newpkg", "3.0"});

		assertNotEquals(InventoryType.CHEST, createPlayer.getOpenInventory().getType());
		assertNextMessageContains(createPlayer, "still starting");

		PlayerMock deletePlayer = operator();
		PackageController.deletePackageCommand(
				deletePlayer, new String[]{"package", "delete", "starter"});

		assertNextMessageContains(deletePlayer, "still starting");
		verify(plugin, never()).deletePackageAsync(any());
	}

	@Test
	void directlyConstructedInvalidCreateGuiCannotPersistPackage() {
		PlayerMock player = operator();
		CreatePackageGui gui = new CreatePackageGui("reload", 3.0);
		gui.openInventory(player);
		Inventory editor = player.getOpenInventory().getTopInventory();
		LanguageManager language = mock(LanguageManager.class);
		when(language.get(MessageKey.PREFIX)).thenReturn("[Airdrop]");
		when(language.get(MessageKey.PACKAGES_NAME_INVALID)).thenReturn("legacy character message");
		when(language.get(MessageKey.PACKAGES_NAME_RESERVED)).thenReturn("package name is reserved");
		ChatHandler.init(language);

		try {
			gui.save(saveClick(player));

			assertSame(editor, player.getOpenInventory().getTopInventory());
			assertThrows(PackageNotFoundException.class, () -> PackageManager.get("reload"));
			Component message = player.nextComponentMessage();
			assertNotNull(message);
			assertTrue(PlainTextComponentSerializer.plainText().serialize(message)
					.toLowerCase(Locale.ROOT).contains("reserved"));
		} finally {
			ChatHandler.init(null);
		}
	}

	private void allowSuccessfulUpdate() {
		when(plugin.updatePackageInventoryAsync(any(), any())).thenAnswer(invocation -> {
			String packageName = invocation.getArgument(0);
			List<ItemStack> items = invocation.getArgument(1);
			Package current = PackageManager.get(packageName);
			PackageManager.publishPackages(Map.of(
					packageName.toLowerCase(Locale.ROOT),
					new Package(current.getName(), current.getPrice(), items)));
			return CompletableFuture.completedFuture(true);
		});
	}

	private void allowSuccessfulCreate() {
		when(plugin.createPackageAsync(any())).thenAnswer(invocation -> {
			Package created = invocation.getArgument(0);
			Package starter = PackageManager.get("starter");
			PackageManager.publishPackages(Map.of(
					"starter", starter,
					created.getName().toLowerCase(Locale.ROOT), created));
			return CompletableFuture.completedFuture(true);
		});
	}

	private PlayerMock operator() {
		PlayerMock player = server.addPlayer();
		player.setOp(true);
		return player;
	}

	private void fillEditableSlots(Inventory editor) {
		for (int slot = 0; slot < PackageManager.MAX_PACKAGE_ITEM_STACKS; slot++) {
			editor.setItem(slot, new ItemStack(Material.DIRT, 1));
		}
	}

	private InventoryClickEvent saveClick(PlayerMock player) {
		return new InventoryClickEvent(
				player.getOpenInventory(),
				InventoryType.SlotType.CONTAINER,
				player.getOpenInventory().getTopInventory().getSize() - 2,
				ClickType.LEFT,
				InventoryAction.PICKUP_ALL);
	}

	private void assertFailureWithoutSuccess(PlayerMock player, String successText) {
		List<String> messages = new ArrayList<>();
		Component message;
		while ((message = player.nextComponentMessage()) != null) {
			messages.add(PlainTextComponentSerializer.plainText().serialize(message));
		}

		assertTrue(messages.stream().anyMatch(value -> value.contains("No changes were made")),
				() -> "Expected persistence failure feedback but got: " + messages);
		assertTrue(messages.stream().noneMatch(value -> value.contains(successText)),
				() -> "Unexpected success feedback: " + messages);
	}

	private void assertNextMessageContains(PlayerMock player, String expectedText) {
		Component message = player.nextComponentMessage();
		assertNotNull(message);
		String plainMessage = PlainTextComponentSerializer.plainText().serialize(message);
		assertTrue(plainMessage.contains(expectedText), () -> "Unexpected message: " + plainMessage);
	}

	private static void setAirdropStaticField(String fieldName, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}

	private static void clearAirdropStaticFields() throws Exception {
		for (Field field : Airdrop.class.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
				field.setAccessible(true);
				field.set(null, field.getType() == boolean.class ? false : null);
			}
		}
	}
}
