package com.airdropmc.commands;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TabCompletionFilterTest {

	private Locale defaultLocale;

	@BeforeEach
	void rememberLocale() {
		defaultLocale = Locale.getDefault();
	}

	@AfterEach
	void restoreLocale() {
		Locale.setDefault(defaultLocale);
	}

	@Test
	void filtersAndDeduplicatesWithoutCaseDifferencesInStableOrder() {
		List<String> candidates = List.of("starter", "Reload", "reload", "Premium", "package");

		List<String> results = TabCompletionFilter.filter(candidates, "rE");

		assertEquals(List.of("Reload"), results);
	}

	@Test
	void usesLocaleIndependentPrefixMatching() {
		Locale.setDefault(Locale.forLanguageTag("tr-TR"));

		List<String> results = TabCompletionFilter.filter(List.of("starter", "TITLE"), "ti");

		assertEquals(List.of("TITLE"), results);
	}
}
