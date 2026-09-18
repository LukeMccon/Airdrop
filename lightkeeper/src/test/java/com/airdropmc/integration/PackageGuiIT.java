package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.CapturedEventSnapshot;
import nl.pim16aap2.lightkeeper.framework.FreshServer;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.MenuHandle;
import nl.pim16aap2.lightkeeper.framework.MenuItemSnapshot;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.airdropmc.integration.AirdropIntegrationSupport.DROP_EVENT;
import static com.airdropmc.integration.EconomyIntegrationSupport.OPERATION_EVENT;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * AIRDR-87: native menu events exercise Airdrop's virtual definition editor.
 * Lightkeeper does not apply vanilla item transfer, so these tests make no claim
 * about player inventory/cursor conservation, extraction, or shift-click behavior.
 */
@ExtendWith(LightkeeperExtension.class)
class PackageGuiIT {
	private static final int CATALOG_SIZE = 27;
	private static final int PREVIEW_SIZE = 36;
	private static final String PREMIUM_PERMISSION = "airdrop.package.premium";
	private static final String CHANGED = "Packages or permissions changed. Reopen the catalog to continue.";
	private static final String AVAILABILITY = "Balance, cooldown, and server limits are checked when requesting";

	@Test
	@FreshServer
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void independentCatalogsReadOnlyNavigationAndPermissionRevocationNeverRequestOrCharge(
			ILightkeeperFramework framework) throws Exception {
		AirdropIntegrationSupport.awaitReady(framework);
		Path packagesFile = packagesFile(framework);
		byte[] original = Files.readAllBytes(packagesFile);
		var baseline = ConsumerIntegrationSupport.snapshot(framework, "premium");
		List<PlayerHandle> players = new ArrayList<>();
		WorldHandle world = null;
		try {
			GuiReloadIntegrationSupport.enableEconomyProvider(framework);
			baseline = ConsumerIntegrationSupport.snapshot(framework, "premium");
			world = AirdropIntegrationSupport.createLandingWorld(framework);
			PlayerHandle starter = fundedPlayer(framework, world, players, "airdrop.package.starter");
			PlayerHandle premium = fundedPlayer(framework, world, players, PREMIUM_PERMISSION);
			PlayerHandle denied = fundedPlayer(framework, world, players);
			PlayerHandle all = fundedPlayer(framework, world, players, "airdrop.package.all");
			int outputOffset = framework.server().output().size();
			try (var drops = framework.events().capture(DROP_EVENT);
				 var operations = framework.events().capture(OPERATION_EVENT)) {
				MenuHandle starterMenu = openCatalog(starter);
				MenuHandle premiumMenu = openCatalog(premium);
				MenuHandle deniedMenu = openCatalog(denied);
				MenuHandle allMenu = openCatalog(all);
				assertCatalog(starterMenu, "starter");
				assertCatalog(premiumMenu, "premium");
				assertEmptyCatalog(deniedMenu);
				assertCatalog(allMenu, "paid", "premium", "starter");
				assertThat(itemAt(starterMenu, 0, CATALOG_SIZE).lore())
						.containsExactly("Free", "Click to view items", AVAILABILITY);
				assertThat(itemAt(premiumMenu, 0, CATALOG_SIZE).lore())
						.containsExactly("$10.25", "Click to view items", AVAILABILITY);

				premiumMenu.clickAtIndex(0);
				awaitTitle(premiumMenu, "premium");
				assertReadOnlyPreview(premiumMenu, "premium", "$10.25");
				List<MenuItemSnapshot> readOnly = topItems(premiumMenu, PREVIEW_SIZE);
				premiumMenu.clickAtIndex(0).clickAtIndex(27).clickAtIndex(32).clickAtIndex(34);
				assertThat(topItems(premiumMenu, PREVIEW_SIZE))
						.as("read-only item, information, help and absent Save controls do not edit the definition")
						.isEqualTo(readOnly);
				premiumMenu.clickAtIndex(33);
				awaitTitle(premiumMenu, "Packages");
				assertCatalog(premiumMenu, "premium");
				assertCatalog(starterMenu, "starter");
				assertEmptyCatalog(deniedMenu);
				assertCatalog(allMenu, "paid", "premium", "starter");

				// Catalog refresh is viewer-specific, including when no packages remain.
				starter.permissions().revoke("airdrop.package.starter");
				eventually(Duration.ofSeconds(10), () -> assertEmptyCatalog(starterMenu));
				assertCatalog(premiumMenu, "premium");
				assertCatalog(allMenu, "paid", "premium", "starter");
				starter.permissions().grant("airdrop.package.starter");
				eventually(Duration.ofSeconds(10), () -> assertCatalog(starterMenu, "starter"));
				starterMenu.clickAtIndex(0);
				awaitTitle(starterMenu, "starter");
				assertReadOnlyPreview(starterMenu, "starter", "Free");
				starterMenu.clickAtIndex(35).andWaitForMenuClose(Duration.ofSeconds(10));

				premiumMenu.clickAtIndex(0);
				awaitTitle(premiumMenu, "premium");
				int messages = premium.receivedMessages().size();
				premium.permissions().revoke(PREMIUM_PERMISSION);
				premiumMenu.andWaitForMenuClose(Duration.ofSeconds(10));
				awaitMessage(premium, messages, CHANGED);
				assertEmptyCatalog(openCatalog(premium));
				assertCatalog(allMenu, "paid", "premium", "starter");

				for (PlayerHandle player : players) {
					GuiReloadIntegrationSupport.ordinaryClose(framework, player);
				}
				assertNoActivity(framework, players, outputOffset,
						drops.getCapturedEvents(), operations.getCapturedEvents());
				assertUnchanged(framework, packagesFile, original, baseline, outputOffset);
				AirdropIntegrationSupport.awaitNoDropEntities(world);
			}
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			restoreAndCleanup(framework, players, world, packagesFile, original, baseline);
		}
	}

	@Test
	@FreshServer
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void revokingAdminDiscardsVirtualEditEvenWhenPackageAccessRemains(ILightkeeperFramework framework)
			throws Exception {
		AirdropIntegrationSupport.awaitReady(framework);
		Path packagesFile = packagesFile(framework);
		byte[] original = Files.readAllBytes(packagesFile);
		var baseline = ConsumerIntegrationSupport.snapshot(framework, "premium");
		List<PlayerHandle> players = new ArrayList<>();
		WorldHandle world = null;
		try {
			GuiReloadIntegrationSupport.enableEconomyProvider(framework);
			baseline = ConsumerIntegrationSupport.snapshot(framework, "premium");
			world = AirdropIntegrationSupport.createLandingWorld(framework);
			PlayerHandle admin = fundedPlayer(framework, world, players, "airdrop.admin", PREMIUM_PERMISSION);
			int offset = framework.server().output().size();
			try (var drops = framework.events().capture(DROP_EVENT);
				 var operations = framework.events().capture(OPERATION_EVENT)) {
				MenuHandle editor = openPreview(admin, "premium");
				assertEditorControls(editor);
				removeHelmetVirtually(editor);
				int messages = admin.receivedMessages().size();
				admin.permissions().revoke("airdrop.admin");
				editor.andWaitForMenuClose(Duration.ofSeconds(10));
				awaitMessage(admin, messages, CHANGED);
				assertUnchanged(framework, packagesFile, original, baseline, offset);
				MenuHandle preview = openPreview(admin, "premium");
				assertReadOnlyPreview(preview, "premium", "$10.25");
				assertThat(itemAt(preview, 0, PREVIEW_SIZE).materialKey()).isEqualTo("minecraft:iron_helmet");
				GuiReloadIntegrationSupport.ordinaryClose(framework, admin);
				assertNoActivity(framework, players, offset, drops.getCapturedEvents(), operations.getCapturedEvents());
			}
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			restoreAndCleanup(framework, players, world, packagesFile, original, baseline);
		}
	}

	@Test
	@FreshServer
	@Timeout(value = 180, unit = TimeUnit.SECONDS)
	void virtualCancelAndOrdinaryCloseDiscardButSavePublishesOnceAndInvalidatesStalePreview(
			ILightkeeperFramework framework) throws Exception {
		AirdropIntegrationSupport.awaitReady(framework);
		Path packagesFile = packagesFile(framework);
		byte[] original = Files.readAllBytes(packagesFile);
		var baseline = ConsumerIntegrationSupport.snapshot(framework, "premium");
		assertThat(baseline.price()).isEqualTo("10.25");
		assertThat(baseline.items()).containsExactly(
				"IRON_HELMET:1", "IRON_CHESTPLATE:1", "IRON_LEGGINGS:1", "IRON_BOOTS:1", "BREAD:2");
		List<PlayerHandle> players = new ArrayList<>();
		WorldHandle world = null;
		try {
			GuiReloadIntegrationSupport.enableEconomyProvider(framework);
			baseline = ConsumerIntegrationSupport.snapshot(framework, "premium");
			world = AirdropIntegrationSupport.createLandingWorld(framework);
			PlayerHandle admin = fundedPlayer(framework, world, players, "airdrop.admin", PREMIUM_PERMISSION);
			PlayerHandle reader = fundedPlayer(framework, world, players, PREMIUM_PERMISSION);
			int offset = framework.server().output().size();
			try (var drops = framework.events().capture(DROP_EVENT);
				 var operations = framework.events().capture(OPERATION_EVENT)) {
				MenuHandle editor = openPreview(admin, "premium");
				assertEditorControls(editor);
				removeHelmetVirtually(editor);
				int cancelMessages = admin.receivedMessages().size();
				editor.clickAtIndex(35).andWaitForMenuClose(Duration.ofSeconds(10));
				awaitMessage(admin, cancelMessages, "Package edit was canceled");
				assertUnchanged(framework, packagesFile, original, baseline, offset);

				editor = openPreview(admin, "premium");
				removeHelmetVirtually(editor);
				GuiReloadIntegrationSupport.ordinaryClose(framework, admin);
				assertUnchanged(framework, packagesFile, original, baseline, offset);

				MenuHandle stalePreview = openPreview(reader, "premium");
				assertReadOnlyPreview(stalePreview, "premium", "$10.25");
				editor = openPreview(admin, "premium");
				removeHelmetVirtually(editor);
				int saveMessages = admin.receivedMessages().size();
				int staleMessages = reader.receivedMessages().size();
				editor.clickAtIndex(34).andWaitForMenuClose(Duration.ofSeconds(20));
				awaitMessage(admin, saveMessages, "was saved successfully");
				stalePreview.andWaitForMenuClose(Duration.ofSeconds(10));
				awaitMessage(reader, staleMessages, CHANGED);
				var saved = ConsumerIntegrationSupport.snapshot(framework, "premium");
				assertThat(saved.revision()).isEqualTo(baseline.revision() + 1);
				assertThat(saved.name()).isEqualTo(baseline.name());
				assertThat(saved.price()).isEqualTo(baseline.price());
				assertThat(saved.items()).containsExactly("IRON_CHESTPLATE:1", "IRON_LEGGINGS:1", "IRON_BOOTS:1", "BREAD:2");
				assertThat(ConsumerIntegrationSupport.registryMarkers(framework, offset))
						.singleElement().satisfies(line -> assertThat(line).contains(
								"cause=UPDATE", "revision=" + saved.revision(), "primaryThread=true",
								"created=0", "updated=1", "deleted=0"));
				byte[] savedBytes = Files.readAllBytes(packagesFile);
				assertThat(savedBytes).as("Save changed persisted YAML").isNotEqualTo(original);
				assertSavedPreview(openPreview(reader, "premium"));
				GuiReloadIntegrationSupport.ordinaryClose(framework, reader);

				// Reload proves the intended definition came from disk, not only a changed in-memory view.
				GuiReloadIntegrationSupport.reloadSuccessfully(framework);
				var reloaded = ConsumerIntegrationSupport.snapshot(framework, "premium");
				assertThat(reloaded.revision()).isEqualTo(saved.revision() + 1);
				assertThat(reloaded.price()).isEqualTo(saved.price());
				assertThat(reloaded.items()).isEqualTo(saved.items());
				assertThat(Files.readAllBytes(packagesFile)).isEqualTo(savedBytes);
				assertSavedPreview(openPreview(reader, "premium"));
				GuiReloadIntegrationSupport.ordinaryClose(framework, reader);
				List<String> registry = ConsumerIntegrationSupport.registryMarkers(framework, offset);
				assertThat(registry).hasSize(2);
				assertThat(registry.getFirst()).contains("cause=UPDATE", "revision=" + saved.revision());
				assertThat(registry.getLast()).contains("cause=RELOAD", "revision=" + reloaded.revision());
				assertNoActivity(framework, players, offset, drops.getCapturedEvents(), operations.getCapturedEvents());
			}
			AirdropIntegrationSupport.assertNoUnexpectedServerErrors(framework);
		} finally {
			restoreAndCleanup(framework, players, world, packagesFile, original, baseline);
		}
	}

	private static PlayerHandle fundedPlayer(ILightkeeperFramework framework, WorldHandle world,
			List<PlayerHandle> players, String... permissions) {
		// The default legacy spawn captures received feedback; pinned full-login bots do not.
		PlayerHandle player = framework.bots().builder()
				.withRandomName()
				.atLocation(world, AirdropIntegrationSupport.LANDING_X + 0.5,
						AirdropIntegrationSupport.BARREL_Y, AirdropIntegrationSupport.LANDING_Z + 0.5)
				.withPermissions(permissions)
				.build();
		players.add(player);
		EconomyIntegrationSupport.resetAccount(framework, player.uniqueId(), "100.00");
		return player;
	}

	private static MenuHandle openCatalog(PlayerHandle player) {
		MenuHandle menu = player.executeCommand("airdrop packages").andWaitForMenuOpen(10);
		awaitTitle(menu, "Packages");
		return menu;
	}

	private static MenuHandle openPreview(PlayerHandle player, String name) {
		MenuHandle menu = openCatalog(player);
		List<MenuItemSnapshot> entries = topItems(menu, CATALOG_SIZE).stream()
				.filter(item -> "minecraft:chest".equals(item.materialKey()) && name.equals(item.displayName()))
				.toList();
		assertThat(entries).as("catalog entry for package %s", name).hasSize(1);
		menu.clickAtIndex(entries.getFirst().slot());
		awaitTitle(menu, name);
		return menu;
	}

	private static void awaitTitle(MenuHandle menu, String title) {
		eventually(Duration.ofSeconds(10), () -> {
			assertThat(menu.snapshot().open()).isTrue();
			assertThat(menu.snapshot().title()).isEqualTo(title);
		});
	}

	private static List<MenuItemSnapshot> topItems(MenuHandle menu, int topSize) {
		return menu.snapshot().items().stream().filter(item -> item.slot() >= 0 && item.slot() < topSize).toList();
	}

	private static MenuItemSnapshot itemAt(MenuHandle menu, int slot, int topSize) {
		return topItems(menu, topSize).stream().filter(item -> item.slot() == slot).findFirst().orElseThrow();
	}

	private static void assertCatalog(MenuHandle menu, String... names) {
		assertThat(menu.snapshot().title()).isEqualTo("Packages");
		assertThat(topItems(menu, CATALOG_SIZE)).extracting(MenuItemSnapshot::displayName).containsExactly(names);
		assertThat(topItems(menu, CATALOG_SIZE)).extracting(MenuItemSnapshot::materialKey).containsOnly("minecraft:chest");
	}

	private static void assertEmptyCatalog(MenuHandle menu) {
		assertThat(menu.snapshot().title()).isEqualTo("Packages");
		assertThat(topItems(menu, CATALOG_SIZE)).singleElement().satisfies(item -> {
			assertThat(item.slot()).isEqualTo(13);
			assertThat(item.materialKey()).isEqualTo("minecraft:barrier");
			assertThat(item.displayName()).isEqualTo("No packages available with your permissions");
		});
	}

	private static void assertReadOnlyPreview(MenuHandle menu, String name, String price) {
		assertThat(menu.snapshot().title()).isEqualTo(name);
		assertThat(itemAt(menu, 27, PREVIEW_SIZE).lore()).containsExactly(price, "Request: /airdrop " + name, AVAILABILITY);
		assertThat(itemAt(menu, 32, PREVIEW_SIZE).displayName()).isEqualTo("Help");
		assertThat(itemAt(menu, 32, PREVIEW_SIZE).lore()).containsExactly("Read-only preview");
		assertThat(itemAt(menu, 33, PREVIEW_SIZE).displayName()).isEqualTo("Back");
		assertThat(topItems(menu, PREVIEW_SIZE)).noneMatch(item -> item.slot() == 34);
		assertThat(itemAt(menu, 35, PREVIEW_SIZE).displayName()).isEqualTo("Close");
	}

	private static void assertEditorControls(MenuHandle menu) {
		assertThat(itemAt(menu, 32, PREVIEW_SIZE).lore()).containsExactly(
				"Left-click inventory: add stack", "Right-click inventory: add 1",
				"Left-click package: remove stack", "Right-click package: remove 1");
		assertThat(itemAt(menu, 34, PREVIEW_SIZE).displayName()).isEqualTo("Save");
		assertThat(itemAt(menu, 35, PREVIEW_SIZE).displayName()).isEqualTo("Cancel");
	}

	private static void removeHelmetVirtually(MenuHandle menu) {
		assertThat(itemAt(menu, 0, PREVIEW_SIZE).materialKey()).isEqualTo("minecraft:iron_helmet");
		menu.clickAtIndex(0);
		eventually(Duration.ofSeconds(10), () -> assertThat(topItems(menu, PREVIEW_SIZE)).noneMatch(item -> item.slot() == 0));
	}

	private static void assertSavedPreview(MenuHandle menu) {
		assertReadOnlyPreview(menu, "premium", "$10.25");
		assertThat(topItems(menu, PREVIEW_SIZE).stream().filter(item -> item.slot() < 27).toList())
				.extracting(MenuItemSnapshot::materialKey).containsExactly(
						"minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots", "minecraft:bread");
	}

	private static void awaitMessage(PlayerHandle player, int offset, String expected) {
		eventually(Duration.ofSeconds(10), () -> {
			List<String> messages = player.receivedMessages();
			assertThat(messages.subList(offset, messages.size())).anyMatch(message -> message.contains(expected));
		});
	}

	private static void assertUnchanged(ILightkeeperFramework framework, Path file, byte[] bytes,
			ConsumerIntegrationSupport.PackageSnapshot baseline, int offset) throws Exception {
		assertThat(ConsumerIntegrationSupport.snapshot(framework, "premium")).isEqualTo(baseline);
		assertThat(Files.readAllBytes(file)).isEqualTo(bytes);
		assertThat(ConsumerIntegrationSupport.registryMarkers(framework, offset)).isEmpty();
	}

	private static void assertNoActivity(ILightkeeperFramework framework, List<PlayerHandle> players,
			int offset, List<CapturedEventSnapshot> drops, List<CapturedEventSnapshot> operations) {
		for (PlayerHandle player : players) {
			EconomyIntegrationSupport.assertAccountState(framework, player.uniqueId(), "100.00", 0, 0, 0);
			assertThat(EconomyIntegrationSupport.operationsFor(operations, player.uniqueId())).isEmpty();
		}
		assertThat(drops).isEmpty();
		assertThat(AirdropIntegrationSupport.consumerMarkers(framework, offset)).isEmpty();
	}

	private static Path packagesFile(ILightkeeperFramework framework) {
		return framework.server().pluginDataDirectory("Airdrop").resolve("packages.yml");
	}

	private static void restoreAndCleanup(ILightkeeperFramework framework, List<PlayerHandle> players,
			WorldHandle world, Path file, byte[] bytes, ConsumerIntegrationSupport.PackageSnapshot baseline) throws Exception {
		try {
			Files.write(file, bytes);
		} finally {
			try {
				cleanup(framework, players);
			} finally {
				if (world != null) {
					AirdropIntegrationSupport.cleanupCrate(framework, world);
				}
			}
		}
		var restored = ConsumerIntegrationSupport.snapshot(framework, "premium");
		assertThat(restored.price()).isEqualTo(baseline.price());
		assertThat(restored.items()).isEqualTo(baseline.items());
		assertThat(Files.readAllBytes(file)).isEqualTo(bytes);
	}

	private static void cleanup(ILightkeeperFramework framework, List<PlayerHandle> players) {
		List<Throwable> failures = new ArrayList<>();
		for (PlayerHandle player : players) {
			try {
				GuiReloadIntegrationSupport.ordinaryClose(framework, player);
			} catch (RuntimeException | AssertionError failure) {
				failures.add(failure);
			}
			try {
				EconomyIntegrationSupport.resetAccount(framework, player.uniqueId(), "0.00");
			} catch (RuntimeException | AssertionError failure) {
				failures.add(failure);
			} finally {
				try {
					player.remove();
				} catch (RuntimeException | AssertionError failure) {
					failures.add(failure);
				}
			}
		}
		try {
			int offset = framework.server().output().size();
			assertThat(framework.server().executeCommand(CommandSource.CONSOLE, "lkeconomy disable").success()).isTrue();
			eventually(Duration.ofSeconds(10), () -> assertThat(GuiReloadIntegrationSupport.outputSince(framework, offset))
					.anyMatch(line -> line.contains("Disabled LightKeeper economy provider")));
		} finally {
			GuiReloadIntegrationSupport.reloadSuccessfully(framework);
		}
		if (!failures.isEmpty()) {
			AssertionError failure = new AssertionError("GUI fixture cleanup failed");
			failures.forEach(failure::addSuppressed);
			throw failure;
		}
	}
}
