package com.airdropmc.config;

import com.airdropmc.Airdrop;
import com.airdropmc.Config;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

	@Test
	void getDropFallingSpeed_rejectsZeroAndUsesDefault() {
		YamlConfiguration configValues = new YamlConfiguration();
		configValues.set("drop.falling-speed", 0.0);

		Config config = mock(Config.class);
		when(config.getConfig()).thenReturn(configValues);
		setAirdropConfig(config);

		assertEquals(0.3, ConfigKeys.getDropFallingSpeed());
	}

	@Test
	void getParachuteChickenCount_rejectsUnsafeLargeValueAndUsesDefault() {
		YamlConfiguration configValues = new YamlConfiguration();
		configValues.set("drop.parachute.chicken-count", 10000);

		Config config = mock(Config.class);
		when(config.getConfig()).thenReturn(configValues);
		setAirdropConfig(config);

		assertEquals(5, ConfigKeys.getParachuteChickenCount());
	}

	@Test
	void getDropHeight_rejectsNegativeValueAndUsesDefault() {
		YamlConfiguration configValues = new YamlConfiguration();
		configValues.set("drop.height", -1);

		Config config = mock(Config.class);
		when(config.getConfig()).thenReturn(configValues);
		setAirdropConfig(config);

		assertEquals(20, ConfigKeys.getDropHeight());
	}

	@Test
	void getSmokeHeight_rejectsNegativeValueAndUsesDefault() {
		YamlConfiguration configValues = new YamlConfiguration();
		configValues.set("drop.particles.smoke.height", -3);

		Config config = mock(Config.class);
		when(config.getConfig()).thenReturn(configValues);
		setAirdropConfig(config);

		assertEquals(20, ConfigKeys.getSmokeHeight());
	}

	@Test
	void isDebugLoggingEnabled_usesDefaultWhenNoPathPresent() {
		YamlConfiguration configValues = new YamlConfiguration();

		Config config = mock(Config.class);
		when(config.getConfig()).thenReturn(configValues);
		setAirdropConfig(config);

		assertFalse(ConfigKeys.isDebugLoggingEnabled());
	}

	@Test
	void isDebugLoggingEnabled_readsConfiguredValue() {
		YamlConfiguration configValues = new YamlConfiguration();
		configValues.set("logging.debug", true);

		Config config = mock(Config.class);
		when(config.getConfig()).thenReturn(configValues);
		setAirdropConfig(config);

		assertTrue(ConfigKeys.isDebugLoggingEnabled());
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
