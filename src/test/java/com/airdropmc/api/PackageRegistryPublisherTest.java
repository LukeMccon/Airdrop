package com.airdropmc.api;

import com.airdropmc.api.event.PackageRegistryChangedEvent;
import com.airdropmc.internal.api.PackageRegistryPublisher;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockBukkitExtension.class)
class PackageRegistryPublisherTest {

	@Test
	void publishesDeterministicCreatedUpdatedAndDeletedSnapshots() {
		List<PackageRegistryChangedEvent> events = new ArrayList<>();
		PackageRegistryPublisher publisher = new PackageRegistryPublisher(events::add);

		publisher.publish(Map.of(
				"zulu", pkg("Zulu", "2.00", Material.GOLD_INGOT),
				"alpha", pkg("Alpha", "1.00", Material.IRON_INGOT)), PackageRegistryCause.STARTUP);
		PackageRegistryChange startup = events.getFirst().change();

		assertEquals(1L, startup.revision());
		assertEquals(List.of("Alpha", "Zulu"), startup.created().keySet().stream().toList());
		assertTrue(startup.updated().isEmpty());
		assertTrue(startup.deleted().isEmpty());

		publisher.publish(Map.of(
				"alpha", pkg("Alpha", "1.0", Material.DIAMOND),
				"bravo", pkg("Bravo", "3.00", Material.EMERALD)), PackageRegistryCause.RELOAD);
		PackageRegistryChange reload = events.get(1).change();

		assertEquals(2L, reload.revision());
		assertEquals(PackageRegistryCause.RELOAD, reload.cause());
		assertEquals(List.of("Bravo"), reload.created().keySet().stream().toList());
		assertEquals(List.of("Alpha"), reload.updated().keySet().stream().toList());
		assertEquals(List.of("Zulu"), reload.deleted().keySet().stream().toList());
	}

	@Test
	void successfulNoOpReloadStillAdvancesRevisionAndFiresOnce() {
		List<PackageRegistryChangedEvent> events = new ArrayList<>();
		PackageRegistryPublisher publisher = new PackageRegistryPublisher(events::add);
		Map<String, AirdropPackage> packages = Map.of(
				"starter", pkg("starter", "0.0", Material.BREAD));

		publisher.publish(packages, PackageRegistryCause.STARTUP);
		publisher.publish(packages, PackageRegistryCause.RELOAD);

		assertEquals(2L, publisher.revision());
		assertEquals(2, events.size());
		assertTrue(events.get(1).change().created().isEmpty());
		assertTrue(events.get(1).change().updated().isEmpty());
		assertTrue(events.get(1).change().deleted().isEmpty());
	}

	@Test
	void publicationIsVisibleBeforeTheListenerRuns() {
		AtomicReference<PackageRegistryPublisher> reference = new AtomicReference<>();
		PackageRegistryPublisher publisher = new PackageRegistryPublisher(event -> {
			PackageRegistryPublisher current = reference.get();
			assertEquals(event.change().revision(), current.revision());
			assertEquals(event.change().packages(), current.packages());
		});
		reference.set(publisher);

		publisher.publish(Map.of(
				"starter", pkg("starter", "0.0", Material.BREAD)), PackageRegistryCause.CREATE);
	}

	@Test
	void snapshotsAreIsolatedFromNestedItemMutation() {
		ItemStack input = new ItemStack(Material.DIAMOND, 2);
		Map<String, AirdropPackage> candidate = new LinkedHashMap<>();
		candidate.put("starter", new AirdropPackage("starter", BigDecimal.ZERO, List.of(input)));
		List<PackageRegistryChangedEvent> events = new ArrayList<>();
		PackageRegistryPublisher publisher = new PackageRegistryPublisher(events::add);

		publisher.publish(candidate, PackageRegistryCause.CREATE);
		input.setAmount(9);
		publisher.packages().get("starter").items().getFirst().setAmount(7);
		events.getFirst().change().created().get("starter").items().getFirst().setAmount(5);

		assertEquals(2, publisher.packages().get("starter").items().getFirst().getAmount());
		assertEquals(2, events.getFirst().change().packages()
				.get("starter").items().getFirst().getAmount());
	}

	@Test
	void invalidCandidateDoesNotAdvanceOrDispatch() {
		List<PackageRegistryChangedEvent> events = new ArrayList<>();
		PackageRegistryPublisher publisher = new PackageRegistryPublisher(events::add);
		publisher.publish(Map.of(
				"starter", pkg("starter", "0", Material.BREAD)), PackageRegistryCause.STARTUP);

		Map<String, AirdropPackage> invalid = new LinkedHashMap<>();
		invalid.put("broken", null);
		assertThrows(NullPointerException.class,
				() -> publisher.publish(invalid, PackageRegistryCause.UPDATE));

		assertEquals(1L, publisher.revision());
		assertEquals(List.of("starter"), publisher.packages().keySet().stream().toList());
		assertEquals(1, events.size());
	}

	private static AirdropPackage pkg(String name, String price, Material item) {
		return new AirdropPackage(name, new BigDecimal(price), List.of(new ItemStack(item)));
	}
}
