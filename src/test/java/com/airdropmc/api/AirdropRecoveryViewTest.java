package com.airdropmc.api;

import com.airdropmc.internal.recovery.DropContextPersistence;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkitExtension;
import org.mockbukkit.mockbukkit.ServerMock;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockBukkitExtension.class)
class AirdropRecoveryViewTest {

	@Test
	void schemaOneRoundTripsExactRequestPriceAndEveryResolvedSetting() {
		RecoveredDropDescriptor descriptor = descriptor();
		PersistentDataContainer data = barrel().getPersistentDataContainer();

		DropContextPersistence.write(data, descriptor);
		DropContextPersistence.Complete complete = assertInstanceOf(
				DropContextPersistence.Complete.class, DropContextPersistence.read(data));

		assertEquals(descriptor, complete.descriptor());
		assertTrue(data.getKeys().stream().noneMatch(key -> key.getKey().contains("item")));
	}

	@Test
	void absentContextIsLegacyButPartialAndUnknownSchemasAreInvalid() {
		PersistentDataContainer data = barrel().getPersistentDataContainer();
		assertInstanceOf(DropContextPersistence.Legacy.class, DropContextPersistence.read(data));

		data.set(DropContextPersistence.SCHEMA_KEY, PersistentDataType.INTEGER, 1);
		assertInstanceOf(DropContextPersistence.Invalid.class, DropContextPersistence.read(data));

		DropContextPersistence.clear(data);
		data.set(DropContextPersistence.SCHEMA_KEY, PersistentDataType.INTEGER, 99);
		assertInstanceOf(DropContextPersistence.Invalid.class, DropContextPersistence.read(data));
	}

	@Test
	void unknownContextKeysAreInvalidAndClearedWithTheOwnedNamespace() {
		PersistentDataContainer data = barrel().getPersistentDataContainer();
		NamespacedKey futureKey = NamespacedKey.fromString("airdrop:context_future_value");
		data.set(futureKey, PersistentDataType.STRING, "future");

		assertInstanceOf(DropContextPersistence.Invalid.class, DropContextPersistence.read(data));

		DropContextPersistence.clear(data);
		assertFalse(data.getKeys().contains(futureKey));
		assertInstanceOf(DropContextPersistence.Legacy.class, DropContextPersistence.read(data));
	}

	@Test
	void recoveredViewExposesDescriptorWithoutFabricatingResolvedContext() {
		RecoveredDropDescriptor descriptor = descriptor();
		WorldPosition position = new WorldPosition(
				UUID.randomUUID(), 10, 64, 10, 0, 0);

		LandedAirdropView view = LandedAirdropView.recovered(
				UUID.randomUUID(), position, 1_000L, false, descriptor);

		assertTrue(view.recovered());
		assertEquals(descriptor, view.recoveryDescriptor().orElseThrow());
		assertEquals(descriptor.requestId(), view.requestId().orElseThrow());
		assertEquals(descriptor.packageName(), view.packageName().orElseThrow());
		assertEquals(descriptor.packagePrice(), view.packagePrice().orElseThrow());
	}

	private static RecoveredDropDescriptor descriptor() {
		return new RecoveredDropDescriptor(
				UUID.randomUUID(),
				DropSource.PLAYER,
				UUID.randomUUID(),
				"precise",
				new BigDecimal("12.3400"),
				new ResolvedDropSettings(
						7, 0.35, 45, false, true, false, true, 8,
						Duration.ofMillis(12_345L), 6, 11, Duration.ofMillis(987_654L)));
	}

	private static Barrel barrel() {
		ServerMock server = (ServerMock) Bukkit.getServer();
		org.bukkit.World world = server.getWorlds().isEmpty()
				? server.addSimpleWorld("recovery-schema")
				: server.getWorlds().getFirst();
		world.getBlockAt(4, 64, 4).setType(Material.BARREL);
		return (Barrel) world.getBlockAt(4, 64, 4).getState();
	}
}
