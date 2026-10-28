package com.airdropmc.paid;

import com.airdropmc.lang.MessageKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaidDropMessageTest {

	@Test
	void englishLanguageKeepsPaidMessagesAlongsideDeliveryFeedback() {
		YamlConfiguration language = YamlConfiguration.loadConfiguration(
				new File("src/main/resources/lang/en.yml"));

		assertEquals(Set.of("preparing", "charged", "failed", "refunded", "incoming", "incoming-charged",
				"landed", "landed-other-world", "requested-cost", "charged-failed", "refund-failed",
				"payment-unknown", "unknown-world"),
				language.getConfigurationSection("drop").getKeys(false));
		assertEquals("{accent}${amount}{primary} has been taken from your account",
				language.getString("drop.charged"));
		assertEquals("Airdrop failed; no payment was taken", language.getString("drop.failed"));
		assertEquals("Airdrop failed; your payment was refunded", language.getString("drop.refunded"));
		assertEquals("Airdrop failed; no payment was taken", MessageKey.DROP_FAILED.getDefault());
		assertEquals("Airdrop failed; your payment was refunded", MessageKey.DROP_REFUNDED.getDefault());
		for (MessageKey key : MessageKey.values()) {
			if (key.getKey().startsWith("drop.") || key.getKey().startsWith("targeted.")) {
				assertEquals(key.getDefault(), language.getString(key.getKey()), key.getKey());
			}
		}
	}
}
