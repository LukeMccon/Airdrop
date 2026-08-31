package com.airdropmc.integration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AirdropIntegrationSupportTest {
	@Test
	void ignoresPaperBackgroundYggdrasilKeyFetchFailure() {
		assertThat(AirdropIntegrationSupport.isExpectedServerError(
				"com.mojang.authlib.yggdrasil.YggdrasilServicesKeyInfo",
				"Failed to request yggdrasil public key"))
				.isTrue();
	}

	@Test
	void keepsOtherYggdrasilErrorsVisible() {
		assertThat(AirdropIntegrationSupport.isExpectedServerError(
				"com.mojang.authlib.yggdrasil.YggdrasilServicesKeyInfo",
				"Unexpected authentication failure"))
				.isFalse();
	}
}
