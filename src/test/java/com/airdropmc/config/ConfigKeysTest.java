package com.airdropmc.config;

import com.airdropmc.Airdrop;
import com.airdropmc.Config;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigKeysTest {

	@AfterEach
	void tearDown() {
		setAirdropConfig(null);
	}

	@Test
	void getDropFallingSpeed_prefersPrimaryPath() {
		YamlConfiguration configValues = new YamlConfiguration();
		configValues.set("drop.falling-speed", 0.65);
		configValues.set("drop.parachute.falling-speed", 0.15);

		Config config = mock(Config.class);
		when(config.getConfig()).thenReturn(configValues);
		setAirdropConfig(config);

		assertEquals(0.65, ConfigKeys.getDropFallingSpeed());
	}

	@Test
	void getDropFallingSpeed_usesLegacyPathWhenPrimaryMissing() {
		YamlConfiguration configValues = new YamlConfiguration();
		configValues.set("drop.parachute.falling-speed", 0.45);

		Config config = mock(Config.class);
		when(config.getConfig()).thenReturn(configValues);
		setAirdropConfig(config);

		assertEquals(0.45, ConfigKeys.getDropFallingSpeed());
	}

	@Test
	void getDropFallingSpeed_usesDefaultWhenNoPathPresent() {
		YamlConfiguration configValues = new YamlConfiguration();

		Config config = mock(Config.class);
		when(config.getConfig()).thenReturn(configValues);
		setAirdropConfig(config);

		assertEquals(0.3, ConfigKeys.getDropFallingSpeed());
	}

	private static void setAirdropConfig(Config config) {
		try {
			Field field = Airdrop.class.getDeclaredField("configuration");
			field.setAccessible(true);
			field.set(null, config);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new IllegalStateException("Unable to set Airdrop configuration", e);
		}
	}
}
