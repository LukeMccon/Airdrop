package com.airdropmc.paid;

import com.airdropmc.lang.MessageKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaidDropMessageTest {

	@Test
	void englishLanguageContainsOnlyTheTwoNewPaidFailureMessages() {
		YamlConfiguration language = YamlConfiguration.loadConfiguration(
				new File("src/main/resources/lang/en.yml"));

		assertEquals(Set.of("charged", "failed", "refunded"),
				language.getConfigurationSection("drop").getKeys(false));
		assertEquals("Airdrop failed; no crate was created", language.getString("drop.failed"));
		assertEquals("Airdrop failed; your payment was refunded", language.getString("drop.refunded"));
		assertEquals("Airdrop failed; no crate was created", MessageKey.DROP_FAILED.getDefault());
		assertEquals("Airdrop failed; your payment was refunded", MessageKey.DROP_REFUNDED.getDefault());
	}
}
