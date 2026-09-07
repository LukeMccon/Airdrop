package com.airdropmc.internal.api;

import com.airdropmc.api.AirdropPackage;
import com.airdropmc.api.ResolvedDropSettings;
import com.airdropmc.config.DropOptions;
import com.airdropmc.limits.DropLimitSettings;
import com.airdropmc.packages.Package;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkitExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockBukkitExtension.class)
class ApiModelMapperTest {

	@Test
	void packageSnapshotDetachesTheMutableImplementationPackage() {
		Package internalPackage = new Package(
				"starter", 12.5, List.of(new ItemStack(Material.DIAMOND, 2)));

		AirdropPackage snapshot = ApiModelMapper.packageSnapshot(internalPackage);
		internalPackage.setItems(List.of(new ItemStack(Material.DIRT)));

		assertEquals("starter", snapshot.name());
		assertEquals(new BigDecimal("12.5"), snapshot.price());
		assertEquals(Material.DIAMOND, snapshot.items().getFirst().getType());
		assertEquals(2, snapshot.items().getFirst().getAmount());
	}

	@Test
	void packageSnapshotSkipsNullSlotsAndPreservesDetachedItemOrder() {
		ItemStack diamond = new ItemStack(Material.DIAMOND, 2);
		ItemStack gold = new ItemStack(Material.GOLD_INGOT, 3);
		List<ItemStack> legacyItems = Arrays.asList(null, diamond, null, gold, null);
		Package internalPackage = new Package("legacy", 10.0, legacyItems);

		AirdropPackage snapshot = ApiModelMapper.packageSnapshot(internalPackage);

		assertEquals(legacyItems, internalPackage.getItems());
		internalPackage.setItems(List.of());
		snapshot.items().getFirst().setAmount(1);
		assertEquals(List.of(diamond, gold), snapshot.items());
	}

	@Test
	void packageSnapshotTreatsOnlyNullSlotsAsEmpty() {
		Package internalPackage = new Package("empty", 0.0, Arrays.asList(null, null));

		AirdropPackage snapshot = ApiModelMapper.packageSnapshot(internalPackage);

		assertEquals(List.of(), snapshot.items());
		assertEquals(Arrays.asList(null, null), internalPackage.getItems());
	}

	@Test
	void settingsSnapshotUsesTheExplicitLimitBoundary() {
		DropLimitSettings limits = new DropLimitSettings(
				Duration.ofSeconds(12), 4, 11, Duration.ofSeconds(90));
		DropOptions options = DropOptions.createDefault()
				.withChickenCount(2)
				.withFallingSpeed(0.2)
				.withDropHeight(50)
				.withLandingEffects(false)
				.withContinuousEffects(false)
				.withFlareEffects(false)
				.withSmokeEnabled(false)
				.withSmokeHeight(10);

		ResolvedDropSettings snapshot = ApiModelMapper.resolveSettings(options, limits);

		assertEquals(2, snapshot.chickenCount());
		assertEquals(Duration.ofSeconds(12), snapshot.requestCooldown());
		assertEquals(4, snapshot.maxFalling());
		assertEquals(11, snapshot.maxLanded());
		assertEquals(Duration.ofSeconds(90), snapshot.landedLifetime());
	}
}
