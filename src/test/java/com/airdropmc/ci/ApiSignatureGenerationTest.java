package com.airdropmc.ci;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiSignatureGenerationTest {

	@Test
	void generatedSignatureIsSortedCompleteAndLimitedToTheSupportedBoundary() throws IOException {
		List<String> signatures = Files.readAllLines(
				Path.of("build/api-signatures/current.txt"));
		List<String> sorted = signatures.stream().sorted().toList();

		assertFalse(signatures.isEmpty());
		assertEquals(sorted, signatures, "API signatures must have deterministic order");
		assertEquals(signatures.size(), new HashSet<>(signatures).size(),
				"API signatures must not contain duplicate members");
		assertTrue(signatures.stream().anyMatch(line -> line.startsWith(
				"TYPE 0x0601 com.airdropmc.api.AirdropApi ")));
		assertTrue(signatures.stream().anyMatch(line -> line.contains(
				"com.airdropmc.api.AirdropStatus.status") || line.contains(
				"com.airdropmc.api.AirdropApi.status()")));
		assertTrue(signatures.stream().anyMatch(line -> line.startsWith(
				"TYPE 0x0011 com.airdropmc.api.event.AirdropRequestEvent ")));
		assertTrue(signatures.stream().anyMatch(line -> line.startsWith(
				"PERMITS com.airdropmc.api.DropOutcome ")));
		assertTrue(signatures.stream().anyMatch(line -> line.startsWith(
				"RECORD_COMPONENT com.airdropmc.api.AirdropVersions.extensionApiVersion ")));
		assertFalse(signatures.stream().anyMatch(line -> line.contains("com.airdropmc.internal")));
		assertFalse(signatures.stream().anyMatch(line -> line.contains("com.airdropmc.events.")));
	}
}
