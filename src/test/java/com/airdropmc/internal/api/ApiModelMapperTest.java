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
