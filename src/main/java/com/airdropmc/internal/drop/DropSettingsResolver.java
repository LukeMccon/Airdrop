package com.airdropmc.internal.drop;

import com.airdropmc.api.DropRequestOptions;
import com.airdropmc.api.ResolvedDropSettings;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.limits.DropLimitSettings;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** Resolves public request overrides against one current configuration snapshot. */
@ApiStatus.Internal
public final class DropSettingsResolver {

	public ResolvedDropSettings resolve(DropRequestOptions options) {
		DropRequestOptions required = Objects.requireNonNull(options, "options");
		DropLimitSettings limits = ConfigKeys.getDropLimitSettings();
		return new ResolvedDropSettings(
				required.chickenCount().orElseGet(ConfigKeys::getParachuteChickenCount),
				required.fallingSpeed().orElseGet(ConfigKeys::getDropFallingSpeed),
				required.dropHeight().orElseGet(ConfigKeys::getDropHeight),
				required.landingEffects().orElseGet(ConfigKeys::shouldShowLandingParticleEffects),
				required.continuousEffects().orElseGet(ConfigKeys::shouldShowContinuousParticleEffects),
				required.flareEffects().orElseGet(ConfigKeys::shouldShowFlareParticleEffects),
				required.smokeEnabled().orElseGet(ConfigKeys::isSmokeEnabled),
				required.smokeHeight().orElseGet(ConfigKeys::getSmokeHeight),
				limits.requestCooldown(),
				limits.maxFalling(),
				limits.maxLanded(),
				limits.landedLifetime());
	}
}
