package com.airdropmc.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TabCompletionFilter {

	private TabCompletionFilter() {
	}

	public static List<String> filter(Collection<String> candidates, String prefix) {
		String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
		Map<String, String> uniqueMatches = new LinkedHashMap<>();
		for (String candidate : candidates) {
			String normalizedCandidate = candidate.toLowerCase(Locale.ROOT);
			if (normalizedCandidate.startsWith(normalizedPrefix)) {
				uniqueMatches.putIfAbsent(normalizedCandidate, candidate);
			}
		}

		List<String> matches = new ArrayList<>(uniqueMatches.values());
		matches.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
		return List.copyOf(matches);
	}
}
