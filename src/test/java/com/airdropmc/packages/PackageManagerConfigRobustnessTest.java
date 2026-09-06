package com.airdropmc.packages;

import com.airdropmc.exceptions.PackageNotFoundException;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkitExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockBukkitExtension.class)
class PackageManagerConfigRobustnessTest {

	@AfterEach
	void tearDown() {
		PackageManager.clear();
	}

	@Test
	void liveRegistry_isHeldAsOneVolatileSnapshotReference() throws Exception {
		Field registry = PackageManager.class.getDeclaredField("packages");

		assertTrue(Modifier.isVolatile(registry.getModifiers()));
		assertTrue(Map.class.isAssignableFrom(registry.getType()));
	}

	@Test
	void fields_doNotDifferOnlyByCapitalization() {
		Field[] fields = PackageManager.class.getDeclaredFields();
		for (int leftIndex = 0; leftIndex < fields.length; leftIndex++) {
			for (int rightIndex = leftIndex + 1; rightIndex < fields.length; rightIndex++) {
				String leftName = fields[leftIndex].getName();
				String rightName = fields[rightIndex].getName();
				assertFalse(leftName.equalsIgnoreCase(rightName),
						() -> "Field names differ only by capitalization: " + leftName + ", " + rightName);
			}
		}
	}

	@Test
	void materializePackages_requiresPackagesSectionButAcceptsExplicitEmpty() throws Exception {
		PackageMaterializationException missing = assertThrows(PackageMaterializationException.class,
				() -> PackageManager.materializePackages(new YamlConfiguration()));
		assertTrue(missing.getMessage().contains("packages"));

		YamlConfiguration scalar = new YamlConfiguration();
		scalar.set("packages", "not-a-section");
		PackageMaterializationException scalarFailure = assertThrows(PackageMaterializationException.class,
				() -> PackageManager.materializePackages(scalar));
		assertTrue(scalarFailure.getMessage().contains("Root 'packages'"));
		assertTrue(scalarFailure.getMessage().contains("section"));

		YamlConfiguration explicitEmpty = new YamlConfiguration();
		explicitEmpty.loadFromString("packages: {}\n");
		Map<String, Package> materialized = PackageManager.materializePackages(explicitEmpty);

		assertTrue(materialized.isEmpty());
		assertThrows(UnsupportedOperationException.class,
				() -> materialized.put("later", new Package("later", 1.0, List.of())));
	}

	@Test
	void materializePackages_rejectsWholeCandidateOnNonSectionEntryAndPreservesLiveSnapshot()
			throws Exception {
		YamlConfiguration initial = configurationWithPackage("starter", 10.0);
		PackageManager.publishPackages(PackageManager.materializePackages(initial));
		Package livePackage = PackageManager.get("starter");

		YamlConfiguration candidate = configurationWithPackage("other", 2.0);
		candidate.set("packages.broken", "not-a-section");

		PackageMaterializationException failure = assertThrows(PackageMaterializationException.class,
				() -> PackageManager.materializePackages(candidate));

		assertTrue(failure.getMessage().contains("broken"));
		assertTrue(failure.getMessage().contains("section"));
		assertSame(livePackage, PackageManager.get("STARTER"));
		assertThrows(PackageNotFoundException.class, () -> PackageManager.get("other"));
	}

	@Test
	void materializePackages_rejectsOverCapacityCandidateAndPreservesLiveSnapshot() throws Exception {
		YamlConfiguration initial = configurationWithPackage("starter", 10.0);
		PackageManager.publishPackages(PackageManager.materializePackages(initial));
		Package livePackage = PackageManager.get("starter");
		YamlConfiguration candidate = new YamlConfiguration();
		candidate.createSection("packages");
		for (int index = 0; index < PackageManager.MAX_PACKAGES + 1; index++) {
			addPackage(candidate, "pkg" + index, 1.0);
		}

		PackageMaterializationException failure = assertThrows(PackageMaterializationException.class,
				() -> PackageManager.materializePackages(candidate));

		assertTrue(failure.getMessage().contains(String.valueOf(PackageManager.MAX_PACKAGES + 1)));
		assertTrue(failure.getMessage().contains(String.valueOf(PackageManager.MAX_PACKAGES)));
		assertSame(livePackage, PackageManager.get("starter"));
		assertThrows(PackageNotFoundException.class, () -> PackageManager.get("pkg0"));
	}

	@Test
	void materializePackages_rejectsWholeCandidateOnInvalidOrReservedName() {
		for (String invalidName : List.of(
				"all", "*", "package", "packages", "version", "status", "reload", "bad name")) {
			YamlConfiguration candidate = configurationWithPackage("valid_name", 1.0);
			addPackage(candidate, invalidName, 2.0);

			PackageMaterializationException failure = assertThrows(PackageMaterializationException.class,
					() -> PackageManager.materializePackages(candidate), invalidName);
			assertTrue(failure.getMessage().contains(invalidName), failure::getMessage);
		}
	}

	@Test
	void materializePackages_rejectsWholeCandidateOnCaseInsensitiveCollision() {
		YamlConfiguration candidate = configurationWithPackage("Starter", 1.0);
		addPackage(candidate, "starter", 2.0);
		addPackage(candidate, "other", 3.0);

		PackageMaterializationException failure = assertThrows(PackageMaterializationException.class,
				() -> PackageManager.materializePackages(candidate));

		assertTrue(failure.getMessage().contains("Starter"));
		assertTrue(failure.getMessage().contains("starter"));
		assertTrue(failure.getMessage().contains("conflict"));
	}

	@Test
	void materializePackages_rejectsWholeCandidateOnEveryInvalidRawPrice() {
		List<Object> invalidPrices = List.of(
				"10",
				true,
				Double.NaN,
				Double.POSITIVE_INFINITY,
				Double.NEGATIVE_INFINITY,
				-1,
				new BigDecimal("1e10000"));

		YamlConfiguration missingPrice = new YamlConfiguration();
		missingPrice.createSection("packages.missing");
		missingPrice.set("packages.missing.items", List.of());
		PackageMaterializationException missingFailure = assertThrows(PackageMaterializationException.class,
				() -> PackageManager.materializePackages(missingPrice));
		assertTrue(missingFailure.getMessage().contains("missing"));
		assertTrue(missingFailure.getMessage().contains("<missing>"));

		for (int index = 0; index < invalidPrices.size(); index++) {
			String packageName = "invalid" + index;
			YamlConfiguration candidate = configurationWithPackage("valid", 1.0);
			addPackage(candidate, packageName, invalidPrices.get(index));

			PackageMaterializationException failure = assertThrows(PackageMaterializationException.class,
					() -> PackageManager.materializePackages(candidate), packageName);
			assertTrue(failure.getMessage().contains(packageName), failure::getMessage);
			assertTrue(failure.getMessage().contains("price"), failure::getMessage);
		}
	}

	@Test
	void materializePackages_acceptsNumericFiniteNonNegativePrices() throws Exception {
		YamlConfiguration candidate = new YamlConfiguration();
		candidate.createSection("packages");
		addPackage(candidate, "integer-zero", 0);
		addPackage(candidate, "double-zero", 0.0);
		addPackage(candidate, "positive", 12.5f);

		Map<String, Package> materialized = PackageManager.materializePackages(candidate);

		assertEquals(Set.of("integer-zero", "double-zero", "positive"), materialized.keySet());
		assertEquals(0.0, materialized.get("integer-zero").getPrice());
		assertEquals(0.0, materialized.get("double-zero").getPrice());
		assertEquals(12.5, materialized.get("positive").getPrice());
	}

	@Test
	void materializePackages_requiresItemsToBeAList() throws Exception {
		List<Object> invalidShapes = List.of(
				"not-a-list",
				42,
				Map.of("==", "org.bukkit.inventory.ItemStack"));
		for (int index = 0; index < invalidShapes.size(); index++) {
			String packageName = "invalid_shape_" + index;
			YamlConfiguration candidate = configurationWithPackage(packageName, 0.0);
			candidate.set("packages." + packageName + ".items", invalidShapes.get(index));

			PackageMaterializationException failure = assertThrows(PackageMaterializationException.class,
					() -> PackageManager.materializePackages(candidate), packageName);

			assertTrue(failure.getMessage().contains(packageName), failure::getMessage);
			assertTrue(failure.getMessage().contains("items"), failure::getMessage);
			assertTrue(failure.getMessage().contains("list"), failure::getMessage);
		}

		YamlConfiguration missingItems = new YamlConfiguration();
		missingItems.set("packages.missing_items.price", 0.0);
		PackageMaterializationException missingFailure = assertThrows(PackageMaterializationException.class,
				() -> PackageManager.materializePackages(missingItems));
		assertTrue(missingFailure.getMessage().contains("missing_items"), missingFailure::getMessage);
		assertTrue(missingFailure.getMessage().contains("list"), missingFailure::getMessage);

		YamlConfiguration nullItems = new YamlConfiguration();
		nullItems.loadFromString("""
				packages:
				  null_items:
				    price: 0.0
				    items: null
				""");
		PackageMaterializationException nullFailure = assertThrows(PackageMaterializationException.class,
				() -> PackageManager.materializePackages(nullItems));
		assertTrue(nullFailure.getMessage().contains("null_items"), nullFailure::getMessage);
		assertTrue(nullFailure.getMessage().contains("list"), nullFailure::getMessage);
	}

	@Test
	void materializePackages_rejectsEveryNonItemStackMemberWithItsZeroBasedIndex() throws Exception {
		YamlConfiguration initial = configurationWithPackage("starter", 10.0);
		PackageManager.publishPackages(PackageManager.materializePackages(initial));
		Package livePackage = PackageManager.get("starter");

		List<Object> invalidMembers = new ArrayList<>();
		invalidMembers.add(null);
		invalidMembers.add("not-an-item");
		invalidMembers.add(42);
		invalidMembers.add(Map.of("==", "incompatible.ItemStack"));
		for (int index = 0; index < invalidMembers.size(); index++) {
			String packageName = "invalid_member_" + index;
			YamlConfiguration candidate = configurationWithPackage(packageName, 0.0);
			List<Object> rawItems = new ArrayList<>();
			rawItems.add(new ItemStack(Material.STONE));
			rawItems.add(invalidMembers.get(index));
			candidate.set("packages." + packageName + ".items", rawItems);

			PackageMaterializationException failure = assertThrows(PackageMaterializationException.class,
					() -> PackageManager.materializePackages(candidate), packageName);

			assertTrue(failure.getMessage().contains(packageName), failure::getMessage);
			assertTrue(failure.getMessage().contains("index 1"), failure::getMessage);
			assertSame(livePackage, PackageManager.get("starter"));
		}
	}

	@Test
	void materializePackages_rejectsPaidPackagesWithNoDeliverableStacksButAllowsFreeEmptyAndNamedStacks()
			throws Exception {
		YamlConfiguration freeEmpty = configurationWithPackage("free_empty", 0.0);
		freeEmpty.set("packages.free_empty.items", List.of());
		assertTrue(PackageManager.materializePackages(freeEmpty).get("free_empty").getItems().isEmpty());

		YamlConfiguration paidEmpty = configurationWithPackage("paid_empty", 1.0);
		paidEmpty.set("packages.paid_empty.items", List.of());
		assertPaidEmptyFailure(paidEmpty, "paid_empty", Set.of());

		ItemStack airStack = mock(ItemStack.class);
		when(airStack.getType()).thenReturn(Material.AIR);
		YamlConfiguration paidAir = configurationWithPackage("paid_air", 1.0);
		paidAir.set("packages.paid_air.items", List.of(airStack));
		assertPaidEmptyFailure(paidAir, "paid_air", Set.of());

		YamlConfiguration paidNamed = configurationWithPackage("paid_named", 1.0);
		paidNamed.set("packages.paid_named.items", List.of(namedItem("Save")));
		List<ItemStack> paidNamedItems = PackageManager.materializePackages(
				paidNamed, Set.of("Save")).get("paid_named").getItems();
		assertEquals(1, paidNamedItems.size());
		assertEquals("Save", paidNamedItems.getFirst().getItemMeta().getDisplayName());
	}

	@Test
	void materializePackages_preservesValidItemMetadataAndOrder() throws Exception {
		ItemStack namedItem = namedItem("First");
		ItemMeta sourceMeta = namedItem.getItemMeta();
		sourceMeta.setLore(List.of("metadata survives"));
		namedItem.setItemMeta(sourceMeta);
		ItemStack secondItem = new ItemStack(Material.BREAD, 3);
		YamlConfiguration candidate = configurationWithPackage("ordered", 2.0);
		candidate.set("packages.ordered.items", List.of(namedItem, secondItem));

		List<ItemStack> items = PackageManager.materializePackages(candidate, Set.of())
				.get("ordered").getItems();

		assertEquals(List.of(Material.PAPER, Material.BREAD),
				items.stream().map(ItemStack::getType).toList());
		assertEquals("First", items.getFirst().getItemMeta().getDisplayName());
		assertEquals(List.of("metadata survives"), items.getFirst().getItemMeta().getLore());
		assertEquals(3, items.get(1).getAmount());
		assertNotSame(namedItem, items.getFirst());
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1})
	void materializePackages_rejectsPaidPackagesWithOnlyNonPositiveAmounts(int amount) throws Exception {
		PackageManager.publishPackages(PackageManager.materializePackages(
				configurationWithPackage("starter", 10.0)));
		Package livePackage = PackageManager.get("starter");
		ItemStack emptyStack = new ItemStack(Material.STONE);
		emptyStack.setAmount(amount);
		YamlConfiguration candidate = configurationWithPackage("paid_empty", 1.0);
		candidate.set("packages.paid_empty.items", List.of(emptyStack));

		assertPaidEmptyFailure(candidate, "paid_empty", Set.of());
		assertSame(livePackage, PackageManager.get("starter"));
		assertFalse(PackageManager.has("paid_empty"));
	}

	@Test
	void materializePackages_excludesNonPositiveAmountsBeforeApplyingBarrelCapacity() throws Exception {
		ItemStack zeroStack = new ItemStack(Material.STONE);
		zeroStack.setAmount(0);
		ItemStack negativeStack = new ItemStack(Material.DIRT);
		negativeStack.setAmount(-1);
		ItemStack namedItem = namedItem("Reward");
		ItemStack bread = new ItemStack(Material.BREAD, 3);
		List<ItemStack> configuredItems = new ArrayList<>();
		for (int index = 0; index < PackageManager.MAX_PACKAGE_ITEM_STACKS; index++) {
			configuredItems.add(index % 2 == 0 ? zeroStack : negativeStack);
		}
		configuredItems.add(namedItem);
		configuredItems.add(bread);
		YamlConfiguration candidate = configurationWithPackage("mixed", 2.0);
		candidate.set("packages.mixed.items", configuredItems);

		List<ItemStack> items = PackageManager.materializePackages(candidate).get("mixed").getItems();

		assertEquals(List.of(Material.PAPER, Material.BREAD),
				items.stream().map(ItemStack::getType).toList());
		assertEquals("Reward", items.getFirst().getItemMeta().getDisplayName());
		assertEquals(3, items.get(1).getAmount());
		assertNotSame(namedItem, items.getFirst());
		assertEquals(PackageManager.MAX_PACKAGE_ITEM_STACKS + 2, configuredItems.size());
	}

	@Test
	void materializePackages_allowsFreePackagesWithOnlyNonPositiveAmounts() throws Exception {
		ItemStack zeroStack = new ItemStack(Material.STONE);
		zeroStack.setAmount(0);
		ItemStack negativeStack = new ItemStack(Material.DIRT);
		negativeStack.setAmount(-1);
		YamlConfiguration candidate = configurationWithPackage("free_empty", 0.0);
		candidate.set("packages.free_empty.items", List.of(zeroStack, negativeStack));

		Package freePackage = PackageManager.materializePackages(candidate).get("free_empty");

		assertEquals(0.0, freePackage.getPrice());
		assertTrue(freePackage.getItems().isEmpty());
	}

	@Test
	void materializeAndPublish_detachConfigurationCandidateAndLiveSnapshot() throws Exception {
		ItemStack sourceItem = new ItemStack(Material.DIRT, 2);
		YamlConfiguration candidate = configurationWithPackage("Starter", 10.0);
		candidate.set("packages.Starter.items", List.of(sourceItem));

		Map<String, Package> materialized = PackageManager.materializePackages(candidate);
		ItemStack materializedItem = materialized.get("starter").getItems().getFirst();
		assertNotSame(sourceItem, materializedItem);

		PackageManager.publishPackages(materialized);
		Field registry = PackageManager.class.getDeclaredField("packages");
		registry.setAccessible(true);
		assertSame(materialized, registry.get(null));
		Package livePackage = PackageManager.get("STARTER");
		assertSame(materialized.get("starter"), livePackage);

		sourceItem.setAmount(7);
		((ItemStack) candidate.getList("packages.Starter.items").getFirst()).setAmount(9);

		assertEquals(Material.DIRT, livePackage.getItems().getFirst().getType());
		assertEquals(2, livePackage.getItems().getFirst().getAmount());
		assertTrue(PackageManager.has("sTaRtEr"));
		assertFalse(PackageManager.has("missing"));
		assertEquals(Set.of("Starter"), PackageManager.getPackages());
	}

	private static void assertPaidEmptyFailure(
			YamlConfiguration candidate,
			String packageName,
			Set<String> controlItemNames) {
		PackageMaterializationException failure = assertThrows(PackageMaterializationException.class,
				() -> PackageManager.materializePackages(candidate, controlItemNames));
		assertTrue(failure.getMessage().contains(packageName), failure::getMessage);
		assertTrue(failure.getMessage().contains("positive price"), failure::getMessage);
		assertTrue(failure.getMessage().contains("deliverable"), failure::getMessage);
	}

	private static ItemStack namedItem(String displayName) {
		ItemStack item = new ItemStack(Material.PAPER);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(displayName);
		item.setItemMeta(meta);
		return item;
	}

	private static YamlConfiguration configurationWithPackage(String packageName, Object price) {
		YamlConfiguration config = new YamlConfiguration();
		config.createSection("packages");
		addPackage(config, packageName, price);
		return config;
	}

	private static void addPackage(YamlConfiguration config, String packageName, Object price) {
		config.createSection("packages." + packageName);
		config.set("packages." + packageName + ".items", List.of(new ItemStack(Material.STONE)));
		if (price != null) {
			config.set("packages." + packageName + ".price", price);
		}
	}
}
