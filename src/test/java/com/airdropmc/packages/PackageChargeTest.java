package com.airdropmc.packages;

import com.airdropmc.Airdrop;
import com.airdropmc.Config;
import com.airdropmc.config.ConfigKeys;
import com.airdropmc.economy.EconomyProvider;
import com.airdropmc.economy.EconomyResult;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PackageChargeTest {

	@AfterEach
	void tearDown() throws Exception {
		setStatic("configuration", null);
		setStatic("economyProvider", null);
	}

	@Test
	void chargeUser_returnsFalseWhenEconomyIsDisabled() throws Exception {
		installEconomyConfig(false);
		Package pkg = new Package("starter", 10.0, List.of());

		assertFalse(pkg.chargeUser(mock(Player.class)));
	}

	@Test
	void chargeUser_returnsConfirmedWithdrawalBeforeAnyMessaging() throws Exception {
		installEconomyConfig(true);
		EconomyProvider economy = mock(EconomyProvider.class);
		Player player = mock(Player.class);
		when(economy.withdraw(player, 10.0)).thenReturn(EconomyResult.ok());
		setStatic("economyProvider", economy);
		Package pkg = new Package("starter", 10.0, List.of());

		assertTrue(pkg.chargeUser(player));

		verify(economy).withdraw(player, 10.0);
		verify(player, never()).sendMessage(anyString());
	}

	private void installEconomyConfig(boolean enabled) throws Exception {
		YamlConfiguration values = new YamlConfiguration();
		values.set(ConfigKeys.ECONOMY_ENABLED, enabled);
		Config config = mock(Config.class);
		when(config.getConfig()).thenReturn(values);
		setStatic("configuration", config);
	}

	private void setStatic(String fieldName, Object value) throws Exception {
		Field field = Airdrop.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(null, value);
	}
}
