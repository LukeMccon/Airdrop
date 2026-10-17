package com.airdropmc.helpers;

import com.airdropmc.lang.MessageKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeliveryMessageColorTest {
	private PlayerMock player;

	@BeforeEach
	void setUp() {
		player = MockBukkit.mock().addPlayer();
		ChatHandler.init(null);
	}

	@AfterEach
	void tearDown() {
		ChatHandler.init(null);
		MockBukkit.unmock();
	}

	@ParameterizedTest
	@EnumSource(value = MessageKey.class, names = {
			"DROP_INCOMING", "DROP_INCOMING_CHARGED", "DROP_LANDED", "DROP_LANDED_OTHER_WORLD",
			"TARGETED_SPAWNED", "TARGETED_SPAWNED_CHARGED", "TARGETED_LANDED",
			"TARGETED_REJECTED", "TARGETED_FAILED", "TARGETED_REFUNDED",
			"TARGETED_CHARGED_FAILED", "TARGETED_REFUND_FAILED", "TARGETED_PAYMENT_UNKNOWN",
			"TARGETED_RECIPIENT_INCOMING", "TARGETED_RECIPIENT_LANDED"})
	void deliveredComponentsAccentValuesAndRestoreSurroundingColor(MessageKey key) {
		Map<String, String> placeholders = Map.of("name", "Starter", "amount", "12.50",
				"x", "12", "y", "-4", "z", "-10", "world", "delivery_world",
				"player", "Recipient", "sender", "Sender", "request_id", "support-reference");
		boolean error = key.getDefault().contains("{error}");
		if (key.getKey().startsWith("targeted.recipient-")) {
			ChatHandler.sendWithSender(player, key, placeholders, Component.text("Sender"));
		} else if (error) {
			ChatHandler.sendError(player, key, placeholders);
		} else {
			ChatHandler.send(player, key, placeholders);
		}
		Component message = player.nextComponentMessage();
		assertNotNull(message);
		StringBuilder text = new StringBuilder();
		List<TextColor> colors = new ArrayList<>();
		flatten(message, null, text, colors);
		assertTrue(text.toString().contains("Starter"));
		for (String value : List.of("Starter", "$12.50", "X: 12, Y: -4, Z: -10",
				"delivery_world", "Recipient", "support-reference")) {
			int start = text.indexOf(value);
			if (start < 0) continue;
			for (int index = start; index < start + value.length(); index++) {
				assertEquals(NamedTextColor.AQUA, colors.get(index), key + " value: " + value);
			}
			assertEquals(error ? NamedTextColor.RED : NamedTextColor.BLUE,
					colors.get(start + value.length()), key + " text after: " + value);
		}
	}

	private void flatten(Component component, TextColor inherited, StringBuilder text, List<TextColor> colors) {
		TextColor color = component.color() == null ? inherited : component.color();
		if (component instanceof TextComponent literal) {
			text.append(literal.content());
			for (int index = 0; index < literal.content().length(); index++) colors.add(color);
		}
		component.children().forEach(child -> flatten(child, color, text, colors));
	}
}
